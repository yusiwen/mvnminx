package cn.yusiwen.mvnmin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.elasticpath.tools.mavenminimal.diff.ProjectRepository;

class FilteringProjectRepositoryTest {

	private static final Set<String> DIRTY_FILES = new HashSet<>(Arrays.asList(
			"README.md",
			"src/main/java/Example.java",
			"docs/README.md",
			"notes.txt",
			"module-a/src/main/java/Foo.java",
			"module-a/data.txt"
	));

	private static final Set<String> COMMIT_FILES = new HashSet<>(Arrays.asList(
			"CHANGELOG.md",
			"module-b/generated/Output.java"
	));

	private static final Set<String> ALL_POMS = new HashSet<>(Arrays.asList(
			"pom.xml",
			"module-a/pom.xml",
			"module-b/pom.xml"
	));

	@Test
	void noExclusionsAllDirtyFilesPassThrough() {
		ProjectRepository filtered = new FilteringProjectRepository(dirtyOnlyRepo(), Collections.emptyList());
		assertEquals(DIRTY_FILES, filtered.findDirtyFiles());
	}

	@Test
	void excludeRootReadmeOnly() {
		ProjectRepository filtered = new FilteringProjectRepository(dirtyOnlyRepo(),
				Collections.singletonList("README.md"));
		Set<String> result = filtered.findDirtyFiles();
		assertEquals(new HashSet<>(Arrays.asList(
				"src/main/java/Example.java",
				"docs/README.md",
				"notes.txt",
				"module-a/src/main/java/Foo.java",
				"module-a/data.txt"
		)), result);
	}

	@Test
	void excludeAllTxtRecursive() {
		ProjectRepository filtered = new FilteringProjectRepository(dirtyOnlyRepo(),
				Collections.singletonList("**/*.txt"));
		Set<String> result = filtered.findDirtyFiles();
		assertEquals(new HashSet<>(Arrays.asList(
				"README.md",
				"src/main/java/Example.java",
				"docs/README.md",
				"module-a/src/main/java/Foo.java"
		)), result);
	}

	@Test
	void excludeRootTxtOnly() {
		ProjectRepository filtered = new FilteringProjectRepository(dirtyOnlyRepo(),
				Collections.singletonList("*.txt"));
		Set<String> result = filtered.findDirtyFiles();
		assertEquals(new HashSet<>(Arrays.asList(
				"README.md",
				"src/main/java/Example.java",
				"docs/README.md",
				"module-a/src/main/java/Foo.java",
				"module-a/data.txt"
		)), result);
	}

	@Test
	void negationReincludes() {
		ProjectRepository filtered = new FilteringProjectRepository(dirtyOnlyRepo(),
				Arrays.asList("**", "!**/*.java"));
		Set<String> result = filtered.findDirtyFiles();
		assertEquals(new HashSet<>(Arrays.asList(
				"src/main/java/Example.java",
				"module-a/src/main/java/Foo.java"
		)), result);
	}

	@Test
	void excludeDoesNotAffectAllPomMode() {
		ProjectRepository filtered = new FilteringProjectRepository(pomOnlyRepo(),
				Arrays.asList("**/pom.xml", "**/*.md"));
		Set<String> result = filtered.findAllPomFiles(6);
		assertEquals(ALL_POMS, result);
	}

	@Test
	void multipleExcludePatterns() {
		ProjectRepository filtered = new FilteringProjectRepository(dirtyOnlyRepo(),
				Arrays.asList("*.md", "*.txt"));
		Set<String> result = filtered.findDirtyFiles();
		assertEquals(new HashSet<>(Arrays.asList(
				"docs/README.md",
				"src/main/java/Example.java",
				"module-a/src/main/java/Foo.java",
				"module-a/data.txt"
		)), result);
	}

	@Test
	void excludeAppliesToCommitishAndDirtyFiles() {
		ProjectRepository filtered = new FilteringProjectRepository(dirtyAndCommitRepo(),
				Collections.singletonList("*.md"));
		Set<String> result = filtered.gitDiffRange("HEAD..HEAD~1");
		Set<String> expected = new HashSet<>(COMMIT_FILES);
		expected.remove("CHANGELOG.md");
		assertEquals(expected, result);
	}

	@Test
	void excludeEmptyPatternsPreservesAllFiles() {
		ProjectRepository filtered = new FilteringProjectRepository(dirtyOnlyRepo(),
				Arrays.asList("", "  "));
		assertEquals(DIRTY_FILES, filtered.findDirtyFiles());
	}

	private ProjectRepository dirtyOnlyRepo() {
		return new ProjectRepository() {
			@Override
			public Set<String> findDirtyFiles() {
				return new HashSet<>(DIRTY_FILES);
			}

			@Override
			public Set<String> gitDiffRange(final String commitRange) {
				return Collections.emptySet();
			}

			@Override
			public Set<String> findAllPomFiles(final int maxDepth) {
				return new HashSet<>(ALL_POMS);
			}

			@Override
			public Set<String> determineProjectIdsForFilesOrFolders(final Set<String> files) {
				return new HashSet<>(files);
			}
		};
	}

	private ProjectRepository pomOnlyRepo() {
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
				return new HashSet<>(ALL_POMS);
			}

			@Override
			public Set<String> determineProjectIdsForFilesOrFolders(final Set<String> files) {
				return new HashSet<>(files);
			}
		};
	}

	private ProjectRepository dirtyAndCommitRepo() {
		return new ProjectRepository() {
			@Override
			public Set<String> findDirtyFiles() {
				return new HashSet<>(DIRTY_FILES);
			}

			@Override
			public Set<String> gitDiffRange(final String commitRange) {
				return new HashSet<>(COMMIT_FILES);
			}

			@Override
			public Set<String> findAllPomFiles(final int maxDepth) {
				return new HashSet<>(ALL_POMS);
			}

			@Override
			public Set<String> determineProjectIdsForFilesOrFolders(final Set<String> files) {
				return new HashSet<>(files);
			}
		};
	}
}
