# mvnminx — Agent Guide

## Build & Test

```bash
mvn clean package           # full build (fat JAR at target/mvnminx-1.0.5.jar)
mvn test                    # run all tests
mvn test -Dtest=FilteringProjectRepositoryTest  # single test class
```

- Java 8 source/target. Depends on `com.elasticpath.tools:mvnmin:1.0.3` (requires local repo install or private repo).
- Fat JAR output: `target/mvnminx-1.0.5.jar` (via maven-shade-plugin).
- `target/` should be gitignored.

## Architecture

- **`MvnMinEx.java`** — CLI entry point. Parses `--exclude`/`--exclude=`, `--dep`, `--diff`/`--diff=` from args, strips them, wires `FilteringProjectRepository`, delegates to `MvnMinCli.run()` or handles `--dep` mode directly.
- **`FilteringProjectRepository.java`** — Decorator implementing `com.elasticpath.tools.mavenminimal.diff.ProjectRepository`. Applies glob exclude patterns on `findDirtyFiles()` and `gitDiffRange()`. Passes through `findAllPomFiles()` and `determineProjectIdsForFilesOrFolders()` unfiltered (so `--all` mode is unaffected).
- **`DepResolver.java`** — Resolves transitive inter-module Maven dependencies by parsing `pom.xml` files. Given module IDs from `-pl`, traverses `<dependencies>`, matches against known project modules, and returns the transitive closure including parent wrapper modules. Used by `--dep` mode (with optional `--diff` intersection).

## Testing

- Tests use **JUnit 5**.
- `FilteringProjectRepositoryTest` tests the decorator in isolation with mock `ProjectRepository` implementations.
- 9 test cases: no exclusions, root-only patterns, recursive globs, negation re-include, multiple patterns, `--all` mode isolation, commitish + dirty intersection.
- `DepResolverTest` tests dependency resolution with 12 test cases: no deps, direct deps, transitive deps, multiple inputs, short-name resolution, `${project.groupId}` variables, external dependency filtering, self-dependency protection, unknown modules, parent wrapper inclusion, and transitive parent chains.

## Exclude Pattern Semantics

- Java `FileSystem.getPathMatcher("glob:...")` syntax.
- Comma-separated: `--exclude "*.md,*.txt"`.
- Negation with `!` prefix: `--exclude "!**/*.java"`.
- `**/` prefix fix for root-level matching: `**/README.md` matches root `README.md` (Java's PathMatcher normally requires a directory separator for `**/` to match at root).
- `--all` mode is not affected by excludes.

## Prerequisites

`com.elasticpath.tools:mvnmin:1.0.3` must be available in the local Maven repository. Install it from the mvnmin release tag:

```bash
git checkout mvnmin-1.0.3
mvn clean install -DskipTests
git checkout -
```

## Releases

Version 1.0.5 is the current release. Semantic versioning following mvnmin's release pattern.
