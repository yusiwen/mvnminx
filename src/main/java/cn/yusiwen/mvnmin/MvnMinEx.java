package cn.yusiwen.mvnmin;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
		boolean hasPrintMode = false;
		for (String arg : args) {
			if ("-p".equals(arg)) {
				hasPrintMode = true;
				break;
			}
		}
		if (!hasPrintMode) {
			System.err.println("--dep requires -p");
			System.exit(1);
		}

		Set<String> inputModules = new HashSet<>();
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if ("-pl".equals(arg) || "--projects".equals(arg)) {
				if (i + 1 < args.length) {
					String value = args[++i];
					for (String part : value.split(",")) {
						String trimmed = part.trim();
						if (!trimmed.isEmpty() && !trimmed.startsWith("!") && !trimmed.startsWith("-")) {
							inputModules.add(trimmed);
						}
					}
				}
			} else if (arg.startsWith("-pl=")) {
				String value = arg.substring("-pl=".length());
				for (String part : value.split(",")) {
					String trimmed = part.trim();
					if (!trimmed.isEmpty() && !trimmed.startsWith("!") && !trimmed.startsWith("-")) {
						inputModules.add(trimmed);
					}
				}
			} else if (arg.startsWith("--projects=")) {
				String value = arg.substring("--projects=".length());
				for (String part : value.split(",")) {
					String trimmed = part.trim();
					if (!trimmed.isEmpty() && !trimmed.startsWith("!") && !trimmed.startsWith("-")) {
						inputModules.add(trimmed);
					}
				}
			}
		}

		if (inputModules.isEmpty()) {
			System.err.println("--dep requires -pl with at least one module");
			System.exit(1);
		}

		ProjectRepository repo = new GitFilesystemProjectRepository();
		DepResolver resolver = new DepResolver(repo);
		Set<String> deps = resolver.resolve(inputModules);

		for (String dep : deps) {
			System.out.println(dep);
		}
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
		out.println();
		out.println("  Debug");
		out.println("    -d --dry-run               Don't invoke maven, print out the commands that");
		out.println("                               would have been executed.");
		out.println("       --version               Print the version number of mvnmin and exit.");
		out.println();
	}
}
