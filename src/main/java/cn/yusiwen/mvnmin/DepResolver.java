package cn.yusiwen.mvnmin;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.elasticpath.tools.mavenminimal.diff.ProjectRepository;

public class DepResolver {

	private final Map<String, String> artifactIdToFullId = new HashMap<>();
	private final Map<String, String> fullIdToPomPath = new HashMap<>();
	private final Set<String> allFullIds = new HashSet<>();
	private final Map<String, String> childToParent = new HashMap<>();

	public DepResolver(final ProjectRepository repo) {
		Set<String> allPoms = repo.findAllPomFiles(Integer.MAX_VALUE);

		for (String pomPath : allPoms) {
			ModuleInfo info = readModuleInfo(pomPath);
			if (info != null) {
				fullIdToPomPath.put(info.fullId, pomPath);
				artifactIdToFullId.put(info.artifactId, info.fullId);
				allFullIds.add(info.fullId);
			}
		}

		for (String pomPath : allPoms) {
			ModuleInfo info = readModuleInfo(pomPath);
			if (info != null && info.parentFullId != null) {
				childToParent.put(info.fullId, info.parentFullId);
			}
		}
	}

	public Set<String> resolve(final Set<String> inputModules) {
		Set<String> result = new TreeSet<>();
		Set<String> toProcess = new HashSet<>();
		Set<String> visited = new HashSet<>();

		for (String input : inputModules) {
			String fullId = resolveModuleId(input.trim());
			if (fullId != null) {
				toProcess.add(fullId);
			}
		}

		Set<String> inputFullIds = new HashSet<>(toProcess);

		while (!toProcess.isEmpty()) {
			String current = toProcess.iterator().next();
			toProcess.remove(current);
			if (!visited.add(current)) {
				continue;
			}

			String pomPath = fullIdToPomPath.get(current);
			if (pomPath == null) {
				continue;
			}

			ModuleInfo currentInfo = readModuleInfo(pomPath);
			if (currentInfo == null) {
				continue;
			}

			Set<String> deps = readDependencies(pomPath, currentInfo.groupId);
			for (String dep : deps) {
				if (allFullIds.contains(dep) && !inputFullIds.contains(dep)) {
					result.add(dep);
					if (!visited.contains(dep)) {
						toProcess.add(dep);
					}
				}
			}
		}

		addParentModules(inputFullIds, result);
		addParentModules(result, result);

		return result;
	}

	private void addParentModules(final Set<String> sourceModules, final Set<String> result) {
		Set<String> toCheck = new HashSet<>(sourceModules);
		while (!toCheck.isEmpty()) {
			String current = toCheck.iterator().next();
			toCheck.remove(current);
			String parent = childToParent.get(current);
			if (parent != null && allFullIds.contains(parent)) {
				if (result.add(parent)) {
					toCheck.add(parent);
				}
			}
		}
	}

	String resolveModuleId(final String input) {
		if (input.contains(":")) {
			return input;
		}
		return artifactIdToFullId.get(input);
	}

	private Set<String> readDependencies(final String pomPath, final String ownGroupId) {
		try {
			Document doc = parseXml(pomPath);
			Element root = doc.getDocumentElement();

			Set<String> deps = new HashSet<>();

			Element depsEl = getChild(root, "dependencies");
			if (depsEl != null) {
				NodeList depNodes = depsEl.getElementsByTagName("dependency");
				for (int i = 0; i < depNodes.getLength(); i++) {
					if (depNodes.item(i).getNodeType() != Node.ELEMENT_NODE) {
						continue;
					}
					Element depEl = (Element) depNodes.item(i);
					String groupId = getChildText(depEl, "groupId");
					String artifactId = getChildText(depEl, "artifactId");
					if (artifactId == null) {
						continue;
					}
					groupId = resolveGroupId(groupId, ownGroupId);
					deps.add(groupId + ":" + artifactId);
				}
			}

			return deps;
		} catch (Exception e) {
			return Collections.emptySet();
		}
	}

	private static String resolveGroupId(final String rawGroupId, final String ownGroupId) {
		if (rawGroupId == null || rawGroupId.isEmpty()) {
			return ownGroupId != null ? ownGroupId : "";
		}
		if ("${project.groupId}".equals(rawGroupId) && ownGroupId != null) {
			return ownGroupId;
		}
		return rawGroupId;
	}

	private ModuleInfo readModuleInfo(final String pomPath) {
		try {
			Document doc = parseXml(pomPath);
			Element root = doc.getDocumentElement();

			String artifactId = getChildText(root, "artifactId");
			if (artifactId == null) {
				return null;
			}

			String groupId = getChildText(root, "groupId");
			String parentGroupId = null;
			String parentArtifactId = null;

			Element parentEl = getChild(root, "parent");
			if (parentEl != null) {
				parentGroupId = getChildText(parentEl, "groupId");
				parentArtifactId = getChildText(parentEl, "artifactId");
			}

			if (groupId == null && parentGroupId != null) {
				groupId = parentGroupId;
			}

			String parentFullId = null;
			if (parentGroupId != null && parentArtifactId != null) {
				parentFullId = parentGroupId + ":" + parentArtifactId;
			}

			if (groupId != null) {
				return new ModuleInfo(groupId, artifactId, parentFullId);
			}
			return null;
		} catch (Exception e) {
			return null;
		}
	}

	private Document parseXml(final String path) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		return builder.parse(new File(path));
	}

	private static Element getChild(final Element parent, final String tagName) {
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE && tagName.equals(child.getNodeName())) {
				return (Element) child;
			}
		}
		return null;
	}

	private static String getChildText(final Element parent, final String tagName) {
		Element child = getChild(parent, tagName);
		if (child != null) {
			return child.getTextContent().trim();
		}
		return null;
	}

	private static final class ModuleInfo {
		final String groupId;
		final String artifactId;
		final String fullId;
		final String parentFullId;

		ModuleInfo(final String groupId, final String artifactId, final String parentFullId) {
			this.groupId = groupId;
			this.artifactId = artifactId;
			this.fullId = groupId + ":" + artifactId;
			this.parentFullId = parentFullId;
		}
	}
}
