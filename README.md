# mvnminx — `--exclude` pattern filtering for mvnmin

Extends [mvnmin](https://github.com/elasticpath/mvnmin) with glob-based exclude pattern filtering for change detection, without modifying upstream sources.

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

## `--exclude` patterns

- Patterns use Java `FileSystem.getPathMatcher("glob:...")` syntax
- Comma-separated for multiple rules: `--exclude "*.md,**/*.txt"`
- Negation with `!` prefix: `"!**/*.java"` re-includes Java files
- Negation requires at least one positive exclusion to negate against
- `--all` mode is **not** affected by exclude patterns
- Multiple `--exclude` flags are cumulative

## How it works

mvnminx depends on `com.elasticpath.tools:mvnmin:1.0.3` as a library. The `--exclude` flag is parsed by `MvnMinEx` before being passed to `MvnMinCli.run()`. A `FilteringProjectRepository` decorator wraps the real `GitFilesystemProjectRepository` and applies glob filtering on `findDirtyFiles()` and `gitDiffRange()` — the two methods that return changed files. The `findAllPomFiles()` method (used by `--all` mode) passes through unfiltered.

No source code from mvnmin is modified. This is pure composition over the `ProjectRepository` interface.

## Build

```bash
mvn clean package          # produces target/mvnminx-1.0.4.jar (fat JAR)
mvn test                   # run tests
```

Requires `com.elasticpath.tools:mvnmin:1.0.3` in your local Maven repository (install from the release tag if needed).

## License

Apache License 2.0 (same as mvnmin).
