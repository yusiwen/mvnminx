package cn.yusiwen.mvnmin;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.elasticpath.tools.mavenminimal.diff.ProjectRepository;

public class FilteringProjectRepository implements ProjectRepository {

	private static final int GLOB_PREFIX_LENGTH = 3;

	private final ProjectRepository delegate;
	private final List<PatternDef> excludePatterns = new ArrayList<>();

	public FilteringProjectRepository(final ProjectRepository delegate, final List<String> patterns) {
		this.delegate = delegate;
		for (String pattern : patterns) {
			boolean negated = pattern.startsWith("!");
			String glob = negated ? pattern.substring(1) : pattern;
			excludePatterns.add(new PatternDef(glob, negated));
		}
	}

	@Override
	public Set<String> findDirtyFiles() {
		return applyExcludes(delegate.findDirtyFiles());
	}

	@Override
	public Set<String> gitDiffRange(final String commitRange) {
		return applyExcludes(delegate.gitDiffRange(commitRange));
	}

	@Override
	public Set<String> findAllPomFiles(final int maxDepth) {
		return delegate.findAllPomFiles(maxDepth);
	}

	@Override
	public Set<String> determineProjectIdsForFilesOrFolders(final Set<String> files) {
		return delegate.determineProjectIdsForFilesOrFolders(files);
	}

	private Set<String> applyExcludes(final Set<String> files) {
		if (excludePatterns.isEmpty()) {
			return files;
		}

		Set<String> result = new HashSet<>(files);
		for (Iterator<String> iter = result.iterator(); iter.hasNext();) {
			String filePath = iter.next();
			Path path = Paths.get(filePath);
			boolean excluded = false;

			for (PatternDef def : excludePatterns) {
				PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + def.glob);
				boolean matches = matcher.matches(path);

				if (!matches && def.glob.startsWith("**/")) {
					String namePattern = def.glob.substring(GLOB_PREFIX_LENGTH);
					PathMatcher nameMatcher = FileSystems.getDefault().getPathMatcher("glob:" + namePattern);
					matches = nameMatcher.matches(path.getFileName());
				}

				if (matches) {
					excluded = !def.negated;
				}
			}

			if (excluded) {
				iter.remove();
			}
		}
		return result;
	}

	private static final class PatternDef {
		final String glob;
		final boolean negated;

		PatternDef(final String glob, final boolean negated) {
			this.glob = glob;
			this.negated = negated;
		}
	}
}
