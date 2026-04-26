package cn.yusiwen.mvnmin;

import java.util.ArrayList;
import java.util.List;

import com.elasticpath.tools.mavenminimal.MvnMinCli;
import com.elasticpath.tools.mavenminimal.diff.GitFilesystemProjectRepository;
import com.elasticpath.tools.mavenminimal.diff.ProjectRepository;

public final class MvnMinEx {

	private MvnMinEx() {
	}

	public static void main(final String[] args) {
		List<String> excludePatterns = new ArrayList<>();
		List<String> cleanedArgs = new ArrayList<>();

		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if ("--exclude".equals(arg)) {
				if (i + 1 < args.length) {
					String value = args[++i];
					for (String p : value.split(",")) {
						String trimmed = p.trim();
						if (!trimmed.isEmpty()) {
							excludePatterns.add(trimmed);
						}
					}
				}
			} else if (arg.startsWith("--exclude=")) {
				String value = arg.substring("--exclude=".length());
				for (String p : value.split(",")) {
					String trimmed = p.trim();
					if (!trimmed.isEmpty()) {
						excludePatterns.add(trimmed);
					}
				}
			} else {
				cleanedArgs.add(arg);
			}
		}

		ProjectRepository repo = new GitFilesystemProjectRepository();
		if (!excludePatterns.isEmpty()) {
			repo = new FilteringProjectRepository(repo, excludePatterns);
		}

		int exitCode = MvnMinCli.run(repo, cleanedArgs.toArray(new String[0]), System.out, true);
		System.exit(exitCode);
	}
}
