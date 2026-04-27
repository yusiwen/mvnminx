package cn.yusiwen.mvnmin;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.elasticpath.tools.mavenminimal.MvnMinCli;
import com.elasticpath.tools.mavenminimal.diff.GitFilesystemProjectRepository;
import com.elasticpath.tools.mavenminimal.diff.ProjectRepository;

public final class MvnMinEx {

	private MvnMinEx() {
	}

	public static void main(final String[] args) {
		for (String arg : args) {
			if ("--help".equals(arg)) {
				printUsage(System.out);
				return;
			}
		}

		boolean depMode = false;
		for (String arg : args) {
			if ("--dep".equals(arg)) {
				depMode = true;
				break;
			}
		}

		if (depMode) {
			handleDepMode(args);
			return;
		}

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

	private static void handleDepMode(final String[] args) {
		if (!hasArg(args, "-p")) {
			System.err.println("--dep requires -p");
			System.exit(1);
		}

		Set<String> inputModules = parseProjectList(args);
		if (inputModules.isEmpty()) {
			System.err.println("--dep requires -pl with at least one module");
			System.exit(1);
		}

		String diffRange = parseDiffArg(args);
		List<String> excludePatterns = parseExcludePatterns(args);

		GitFilesystemProjectRepository baseRepo = new GitFilesystemProjectRepository();
		ProjectRepository repo = baseRepo;
		if (!excludePatterns.isEmpty()) {
			repo = new FilteringProjectRepository(baseRepo, excludePatterns);
		}

		DepResolver resolver = new DepResolver(repo);
		Set<String> deps = resolver.resolve(inputModules);

		if (diffRange != null) {
			Set<String> changedFiles = new HashSet<>();
			changedFiles.addAll(repo.findDirtyFiles());
			changedFiles.addAll(repo.gitDiffRange(diffRange));
			Set<String> changedModules = repo.determineProjectIdsForFilesOrFolders(changedFiles);

			Set<String> involved = new TreeSet<>(deps);
			for (String input : inputModules) {
				String fullId = input.contains(":")
						? input : resolver.resolveModuleId(input.trim());
				if (fullId != null) {
					involved.add(fullId);
				}
			}
			involved.retainAll(changedModules);

			for (String module : involved) {
				System.out.println(module);
			}
			return;
		}

		for (String dep : deps) {
			System.out.println(dep);
		}
	}

	private static boolean hasArg(final String[] args, final String target) {
		for (String arg : args) {
			if (target.equals(arg)) {
				return true;
			}
		}
		return false;
	}

	private static List<String> parseExcludePatterns(final String[] args) {
		List<String> patterns = new ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if ("--exclude".equals(arg)) {
				if (i + 1 < args.length) {
					for (String p : args[++i].split(",")) {
						String trimmed = p.trim();
						if (!trimmed.isEmpty()) {
							patterns.add(trimmed);
						}
					}
				}
			} else if (arg.startsWith("--exclude=")) {
				for (String p : arg.substring("--exclude=".length()).split(",")) {
					String trimmed = p.trim();
					if (!trimmed.isEmpty()) {
						patterns.add(trimmed);
					}
				}
			}
		}
		return patterns;
	}

	private static String parseDiffArg(final String[] args) {
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if ("--diff".equals(arg)) {
				if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
					return args[i + 1];
				}
				return "master..";
			}
			if (arg.startsWith("--diff=")) {
				return arg.substring("--diff=".length());
			}
		}
		return null;
	}

	private static Set<String> parseProjectList(final String[] args) {
		Set<String> modules = new HashSet<>();
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if ("-pl".equals(arg) || "--projects".equals(arg)) {
				if (i + 1 < args.length) {
					for (String part : args[++i].split(",")) {
						String trimmed = part.trim();
						if (!trimmed.isEmpty() && !trimmed.startsWith("!") && !trimmed.startsWith("-")) {
							modules.add(trimmed);
						}
					}
				}
			} else if (arg.startsWith("-pl=")) {
				for (String part : arg.substring("-pl=".length()).split(",")) {
					String trimmed = part.trim();
					if (!trimmed.isEmpty() && !trimmed.startsWith("!") && !trimmed.startsWith("-")) {
						modules.add(trimmed);
					}
				}
			} else if (arg.startsWith("--projects=")) {
				for (String part : arg.substring("--projects=".length()).split(",")) {
					String trimmed = part.trim();
					if (!trimmed.isEmpty() && !trimmed.startsWith("!") && !trimmed.startsWith("-")) {
						modules.add(trimmed);
					}
				}
			}
		}
		return modules;
	}

	private static void printUsage(final PrintStream out) {
		out.println("usage: mvmin [options] [<maven goal(s)>] [<maven phase(s)>] [<maven arg(s)>]");
		out.println();
		out.println("  Project Activation/Deactivation");
		out.println("    --all                      Activate all `pom.xml` files in all sub directories");
		out.println("                               (default max depth: 6)");
		out.println("    --diff[=commit[..commit]]  Activate all projects changed since the specified commit,");
		out.println("                               or range of specified commits.");
		out.println("                               (default: 'master')");
		out.println("    -pl,--projects <arg>       Comma-delimited list of specified reactor projects");
		out.println("                               to build as well as those otherwise activated.");
		out.println("                               A project can be specified by `groupId:artifactId`");
		out.println("                               A project can be deactivated by leading with an");
		out.println("                               exclamation mark or hyphen: `-groupId:artifactId`");
		out.println("    --nbi                      No build-if dependencies are considered, just");
		out.println("                               changed modules.");
		out.println("    --exclude <pattern>        Exclude files matching the given glob pattern(s) from");
		out.println("                               dirty/diff detection. Comma-separated for multiple");
		out.println("                               patterns. Prefix with ! to re-include.");
		out.println();
		out.println("  Scripting");
		out.println("    -p                         Don't invoke maven, print out activated projects,");
		out.println("                               sorted, newline separated.");
		out.println("    --dep                      When combined with -p and -pl, print the transitive");
		out.println("                               inter-module dependencies of the given project(s)");
		out.println("                               instead of the activated projects.");
		out.println("                               When --diff is also present, prints the intersection");
		out.println("                               of dependencies and changed modules (--exclude respected).");
		out.println();
		out.println("  Debug");
		out.println("    -d --dry-run               Don't invoke maven, print out the commands that");
		out.println("                               would have been executed.");
		out.println("       --version               Print the version number of mvnmin and exit.");
		out.println();
	}
}
