# mvnminx — `--exclude` pattern filtering for mvnmin

Extends [mvnmin](https://github.com/elasticpath/mvnmin) with glob-based exclude pattern filtering for change detection and `--dep` dependency resolution, without modifying upstream sources.

## Usage

```bash
java -jar target/mvnminx-1.0.4.jar [options] [<maven goals>]

# exclude root-level .md files
java -jar target/mvnminx-1.0.4.jar --exclude "*.md" clean install

# exclude all .txt files at any depth
java -jar target/mvnminx-1.0.4.jar --exclude "**/*.txt" test

# exclude everything except .java files (negation with !)
java -jar target/mvnminx-1.0.4.jar --exclude "**" --exclude "!**/*.java" compile

# multiple patterns (comma-separated)
java -jar target/mvnminx-1.0.4.jar --exclude "*.md,*.txt,*.generated.java" verify
```

## `--dep` — Dependency Resolution

Print the transitive inter-module Maven dependencies of given project(s). Requires `-p` (print mode) and `-pl` (project list).

```bash
# print all modules that beap-system transitively depends on
java -jar target/mvnminx-1.0.4.jar -p --dep -pl beap-system

# short names work (resolved by artifactId)
java -jar target/mvnminx-1.0.4.jar -p --dep -pl module-a,module-b

# full coordinates also accepted
java -jar target/mvnminx-1.0.4.jar -p --dep -pl com.example:module-a
```

The output includes:

- **Leaf dependencies** — modules listed in `<dependencies>` of the input module(s) and their transitive dependencies, matched against known modules in the same project
- **Parent wrapper modules** — pom-packaged aggregator modules (`<parent>` chain) for every dependency found, so the full reactor build order is visible
- **The project root module** — the top-level aggregator pom (if present in the parent chain)

External dependencies (not present in the project's own pom files) are ignored. `${project.groupId}` in dependency coordinates is resolved to the module's own groupId.

### Intersection with `--diff` / `--exclude`

When combined with `--diff` and `--exclude`, `--dep` prints only the **intersection** of dependency modules and modules changed in the given commit range:

```bash
# which dependencies of beap-system changed between HEAD and main?
java -jar target/mvnminx-1.0.4.jar -p --dep -pl beap-system --diff=HEAD..main

# same, excluding markdown changes from the diff
java -jar target/mvnminx-1.0.4.jar -p --dep -pl beap-system --diff=HEAD..main --exclude='*.md'
```

This answers: *"among the modules my project depends on, which were actually touched in this commit range?"*

The input module(s) from `-pl` are included in the intersection (if changed), so `-p --dep -pl beap-system --diff=HEAD..HEAD~1` prints `beap-system` when its own source changed.

## `--exclude` patterns

- Patterns use Java `FileSystem.getPathMatcher("glob:...")` syntax
- Comma-separated for multiple rules: `--exclude "*.md,**/*.txt"`
- Negation with `!` prefix: `"!**/*.java"` re-includes Java files
- Negation requires at least one positive exclusion to negate against
- `--all` mode is **not** affected by exclude patterns
- Multiple `--exclude` flags are cumulative
- With `--dep`, `--exclude` only has effect when `--diff` is also present

## How it works

mvnminx depends on `com.elasticpath.tools:mvnmin:1.0.3` as a library. The `--exclude` flag is parsed by `MvnMinEx` before being passed to `MvnMinCli.run()`. A `FilteringProjectRepository` decorator wraps the real `GitFilesystemProjectRepository` and applies glob filtering on `findDirtyFiles()` and `gitDiffRange()` — the two methods that return changed files. The `findAllPomFiles()` method (used by `--all` mode) passes through unfiltered.

No source code from mvnmin is modified. This is pure composition over the `ProjectRepository` interface.

## Prerequisites

`com.elasticpath.tools:mvnmin:1.0.3` must be installed to your local Maven repository first:

```bash
git clone git@github.com:elasticpath/mvnmin.git
cd mvnmin
git checkout mvnmin-1.0.3
mvn clean install -DskipTests
```

This dependency is not available on Maven Central, so local installation is required.

## Build

```bash
mvn clean package          # produces target/mvnminx-1.0.4.jar (fat JAR)
mvn test                   # run tests
```

## License

Apache License 2.0 (same as mvnmin).
