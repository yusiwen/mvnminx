package cn.yusiwen.mvnmin;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

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
		out.println();
		out.println("  Debug");
		out.println("    -d --dry-run               Don't invoke maven, print out the commands that");
		out.println("                               would have been executed.");
		out.println("       --version               Print the version number of mvnmin and exit.");
		out.println();
	}
}
