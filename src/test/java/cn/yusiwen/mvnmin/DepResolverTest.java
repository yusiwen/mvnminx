package cn.yusiwen.mvnmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.elasticpath.tools.mavenminimal.diff.ProjectRepository;

class DepResolverTest {

	private Path tempDir;
	private String rootPom;
	private String moduleAPom;
	private String moduleBPom;
	private String moduleCPom;
	private String moduleDPom;

	@BeforeEach
	void setUp() throws Exception {
		tempDir = Files.createTempDirectory("dep-resolver-test");

		rootPom = writePom(tempDir, "pom.xml",
				"com.test", "parent", null, null);

		moduleAPom = writePom(tempDir, "module-a/pom.xml",
				"com.test", "module-a", "com.test",
				createDep("com.test", "module-b"));

		moduleBPom = writePom(tempDir, "module-b/pom.xml",
				"com.test", "module-b", "com.test",
				createDep("com.test", "module-c"));

		moduleCPom = writePom(tempDir, "module-c/pom.xml",
				"com.test", "module-c", "com.test", "");

		moduleDPom = writePom(tempDir, "module-d/pom.xml",
				"com.test", "module-d", "com.test",
				createDep("com.test", "module-a")
						+ createDep("com.test", "module-b"));
	}

	@AfterEach
	void tearDown() throws Exception {
		deleteRecursive(tempDir.toFile());
	}

	@Test
	void noDependenciesReturnsEmpty() {
		ProjectRepository repo = createRepo(moduleCPom);
		DepResolver resolver = new DepResolver(repo);
		Set<String> result = resolver.resolve(Collections.singleton("com.test:module-c"));
		assertTrue(result.isEmpty());
	}

	@Test
	void directDependencyResolved() throws IOException {
		String pomX = writePom(tempDir, "module-x/pom.xml",
				"com.test", "module-x", "com.test",
				createDep("com.test", "module-c"));
		ProjectRepository repo = createRepo(rootPom, pomX, moduleCPom);
		DepResolver resolver = new DepResolver(repo);
		Set<String> result = resolver.resolve(Collections.singleton("com.test:module-x"));
		assertEquals(Collections.singleton("com.test:module-c"), result);
	}

	@Test
	void transitiveDependenciesResolved() {
		ProjectRepository repo = createRepo(rootPom, moduleAPom, moduleBPom, moduleCPom);
		DepResolver resolver = new DepResolver(repo);
		Set<String> result = resolver.resolve(Collections.singleton("com.test:module-a"));
		assertEquals(new HashSet<>(Arrays.asList("com.test:module-b", "com.test:module-c")), result);
	}

	@Test
	void multipleDirectDependencies() {
		ProjectRepository repo = createRepo(rootPom, moduleAPom, moduleBPom, moduleCPom, moduleDPom);
		DepResolver resolver = new DepResolver(repo);
		Set<String> result = resolver.resolve(Collections.singleton("com.test:module-d"));
		assertEquals(new HashSet<>(Arrays.asList("com.test:module-a", "com.test:module-b", "com.test:module-c")),
				result);
	}

	@Test
	void shortModuleNameResolution() {
		ProjectRepository repo = createRepo(rootPom, moduleAPom, moduleBPom, moduleCPom);
		DepResolver resolver = new DepResolver(repo);
		Set<String> result = resolver.resolve(Collections.singleton("module-a"));
		assertEquals(new HashSet<>(Arrays.asList("com.test:module-b", "com.test:module-c")), result);
	}

	@Test
	void multipleInputModules() {
		ProjectRepository repo = createRepo(rootPom, moduleAPom, moduleBPom, moduleCPom);
		DepResolver resolver = new DepResolver(repo);
		Set<String> result = resolver.resolve(new HashSet<>(Arrays.asList("com.test:module-a", "com.test:module-b")));
		assertEquals(new HashSet<>(Arrays.asList("com.test:module-c")), result);
	}

	@Test
	void unknownModuleReturnsEmpty() {
		ProjectRepository repo = createRepo(rootPom, moduleAPom);
		DepResolver resolver = new DepResolver(repo);
		Set<String> result = resolver.resolve(Collections.singleton("com.test:nonexistent"));
		assertTrue(result.isEmpty());
	}

	@Test
	void dependencyWithProjectGroupIdVariable() throws IOException {
		String pomA = writePom(tempDir, "module-a2/pom.xml",
				"com.test", "module-a2", "com.test",
				"      <dependency>\n"
						+ "        <groupId>${project.groupId}</groupId>\n"
						+ "        <artifactId>module-b</artifactId>\n"
						+ "      </dependency>\n");
		ProjectRepository repo = createRepo(rootPom, pomA, moduleBPom);
		DepResolver resolver = new DepResolver(repo);
		Set<String> result = resolver.resolve(Collections.singleton("com.test:module-a2"));
		assertEquals(Collections.singleton("com.test:module-b"), result);
	}

	@Test
	void dependencyOutsideProjectIgnored() throws IOException {
		String pom = writePom(tempDir, "module-ext/pom.xml",
				"com.test", "module-ext", "com.test",
				createDep("com.other", "external-lib")
						+ createDep("com.test", "module-b"));
		ProjectRepository repo = createRepo(rootPom, pom, moduleBPom, moduleCPom);
		DepResolver resolver = new DepResolver(repo);
		Set<String> result = resolver.resolve(Collections.singleton("com.test:module-ext"));
		assertEquals(new HashSet<>(Arrays.asList("com.test:module-b", "com.test:module-c")), result);
	}

	@Test
	void moduleNotDependingOnItself() throws IOException {
		String pom = writePom(tempDir, "module-circ/pom.xml",
				"com.test", "module-circ", "com.test",
				createDep("com.test", "module-circ"));
		ProjectRepository repo = createRepo(rootPom, pom);
		DepResolver resolver = new DepResolver(repo);
		Set<String> result = resolver.resolve(Collections.singleton("com.test:module-circ"));
		assertTrue(result.isEmpty());
	}

	private ProjectRepository createRepo(final String... pomPaths) {
		Set<String> pomSet = new HashSet<>(Arrays.asList(pomPaths));
		return new ProjectRepository() {
			@Override
			public Set<String> findDirtyFiles() {
				return Collections.emptySet();
			}

			@Override
			public Set<String> gitDiffRange(final String commitRange) {
				return Collections.emptySet();
			}

			@Override
			public Set<String> findAllPomFiles(final int maxDepth) {
				return new HashSet<>(pomSet);
			}

			@Override
			public Set<String> determineProjectIdsForFilesOrFolders(final Set<String> files) {
				return new HashSet<>(files);
			}
		};
	}

	private static String writePom(final Path baseDir, final String relativePath,
			final String groupId, final String artifactId,
			final String parentGroupId, final String depsXml) throws IOException {
		File pomFile = baseDir.resolve(relativePath).toFile();
		pomFile.getParentFile().mkdirs();

		StringBuilder xml = new StringBuilder();
		xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		xml.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n");
		xml.append("  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
		xml.append("  xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n");
		xml.append("  <modelVersion>4.0.0</modelVersion>\n");

		if (parentGroupId != null) {
			xml.append("  <parent>\n");
			xml.append("    <groupId>").append(parentGroupId).append("</groupId>\n");
			xml.append("    <artifactId>parent</artifactId>\n");
			xml.append("  </parent>\n");
		}

		if (!"pom.xml".equals(relativePath)) {
			xml.append("  <groupId>").append(groupId).append("</groupId>\n");
		} else {
			xml.append("  <groupId>").append(groupId).append("</groupId>\n");
		}
		xml.append("  <artifactId>").append(artifactId).append("</artifactId>\n");

		if (depsXml != null && !depsXml.isEmpty()) {
			xml.append("  <dependencies>\n");
			xml.append(depsXml);
			xml.append("  </dependencies>\n");
		}

		xml.append("</project>\n");

		try (FileWriter writer = new FileWriter(pomFile)) {
			writer.write(xml.toString());
		}
		return pomFile.getAbsolutePath();
	}

	private static String createDep(final String groupId, final String artifactId) {
		return "      <dependency>\n"
				+ "        <groupId>" + groupId + "</groupId>\n"
				+ "        <artifactId>" + artifactId + "</artifactId>\n"
				+ "      </dependency>\n";
	}

	private static void deleteRecursive(final File file) {
		if (file.isDirectory()) {
			File[] children = file.listFiles();
			if (children != null) {
				for (File child : children) {
					deleteRecursive(child);
				}
			}
		}
		file.delete();
	}
}
