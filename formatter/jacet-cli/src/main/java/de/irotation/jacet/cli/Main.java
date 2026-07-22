package de.irotation.jacet.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import de.irotation.jacet.FormatResult;
import de.irotation.jacet.JacetFormatter;
import de.irotation.jacet.cli.FormatRunner.WriteOutcome;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
  name = "jacet",
  mixinStandardHelpOptions = true,
  versionProvider = Main.VersionProvider.class,
  description = "Format Java source files in Prettier style."
)
public final class Main implements Callable<Integer> {

  @Parameters(description = "Java source files or directories to format.", arity = "0..*")
  List<Path> files = List.of();

  // Presence flags — absent means false; these select a run mode and have no .jacet.json counterpart.

  @Option(names = "--check", description = "Check if files are formatted (exit code 1 if not).")
  boolean check;

  @Option(
    names = "--write",
    description = "Edit files in-place. This is already the default; accept it for explicitness (conflicts with --check)."
  )
  boolean write;

  @Option(names = "--staged", description = "Format git-staged Java files and re-stage them (for pre-commit hooks).")
  boolean staged;

  @Option(names = "--changed", description = "Format Java files changed in the working tree (modified and untracked), in place.")
  boolean changed;

  @Option(names = "--no-config", description = "Ignore config files, use only CLI flags and defaults.")
  boolean noConfig;

  @Nullable
  @Option(names = "--stdin-filepath", description = "Format stdin and write to stdout. The path is used for config file lookup.")
  String stdinFilepath;

  // Tri-state config overrides — null means "not passed, keep the configured value"; a non-null value wins over .jacet.json.

  @Nullable
  @Option(names = "--print-width", description = "Line width (default: from config or 140).")
  Integer printWidth;

  @Nullable
  @Option(names = "--tab-width", description = "Indentation width (default: from config or 2).")
  Integer tabWidth;

  // Negatable booleans: picocli requires a primitive here, so the "was it passed at all?" half of the tri-state comes from the parse result
  // (see triState) rather than a null field. --x forces true, --no-x forces false, absent keeps the configured value.

  @Option(names = "--use-tabs", negatable = true, description = "Indent with tabs instead of spaces (--no-use-tabs to force spaces).")
  boolean useTabs;

  @Option(names = "--force-braces", negatable = true, description = "Add braces to single-statement blocks (--no-force-braces to disable).")
  boolean forceBraces;

  @Nullable
  @Option(names = "--end-of-line", description = "Line ending: lf, crlf, cr, auto (default: from config or lf).")
  String endOfLine;

  @Nullable
  @Option(names = "--static-imports", description = "Static import position: top, bottom, mixed (default: from config or top).")
  String staticImports;

  @Nullable
  @Option(
    names = "--import-groups",
    description = "Comma-separated import group prefixes (default: from config or java,javax,jakarta,org,com,de,lombok)."
  )
  String importGroups;

  @Option(
    names = "--remove-unused-imports",
    negatable = true,
    description = "Remove imports whose name is never referenced; wildcard imports are kept (--no-remove-unused-imports to disable)."
  )
  boolean removeUnusedImports;

  @Nullable
  @Option(names = "--config", description = "Path to .jacet.json config file.")
  Path configFile;

  /** Test-only override for the directory git runs in; production uses the process working directory. */
  @Nullable
  Path workingDir;

  /** Test-only override for the output sink; production binds to {@code System.out}/{@code System.err}. */
  @Nullable
  CliConsole console;

  /** Injected by picocli; used to tell "flag not passed" from "flag passed as false" for the negatable boolean options. */
  @Spec
  @Nullable
  CommandSpec spec;

  @Override
  public Integer call() throws Exception {
    final CliConsole out = this.console != null ? this.console : CliConsole.system();

    if (check && write) {
      out.err("Error: --check and --write are mutually exclusive.");
      return 2;
    }
    if (staged && changed) {
      out.err("Error: --staged and --changed are mutually exclusive.");
      return 2;
    }

    if (configFile != null && !Files.isRegularFile(configFile)) {
      out.err("Error: config file not found: " + configFile);
      return 2;
    }

    final OptionsResolver resolver = this.optionsResolver();
    try {
      resolver.validateOverrides();
    } catch (final IllegalArgumentException e) {
      out.err("Error: " + e.getMessage());
      return 2;
    }

    if (staged) {
      return this.runGitMode("--staged", true, this::selectStaged, out, resolver);
    }
    if (changed) {
      return this.runGitMode("--changed", false, this::selectChanged, out, resolver);
    }
    if (stdinFilepath != null) {
      return this.handleStdin(out, resolver);
    }

    if (files.isEmpty()) {
      out.err("Error: No files specified. Use --help for usage.");
      return 2;
    }

    final List<Path> javaFiles;
    try {
      javaFiles = collectJavaFiles(files);
    } catch (final NoSuchFileException e) {
      out.err("Error: file not found: " + e.getFile());
      return 2;
    }
    if (javaFiles.isEmpty()) {
      out.err("No .java files found.");
      return 0;
    }

    final Path first = files.getFirst();
    final Path configReference = Files.isDirectory(first) ? first : first.toAbsolutePath().getParent();
    final JacetFormatter formatter = new JacetFormatter(resolver.resolve(configReference));
    final FormatRunner runner = new FormatRunner(formatter, out);
    if (check) {
      return runner.check(javaFiles);
    }
    runner.write(javaFiles);
    return 0;
  }

  /**
   * Formats stdin and writes the result to stdout. On parse errors the original source is written back unchanged (exit code 2) so a
   * downstream pipeline stage never receives empty or truncated output.
   */
  private int handleStdin(final CliConsole out, final OptionsResolver resolver) throws IOException {
    final String source = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
    final Path reference = stdinFilepath != null ? Path.of(stdinFilepath).getParent() : null;
    final JacetFormatter formatter = new JacetFormatter(resolver.resolve(reference));

    final FormatResult result = formatter.formatWithResult(source);
    if (result.hasParseErrors()) {
      for (final String error : result.parseErrors()) {
        out.err(stdinFilepath + ": " + error);
      }
      out.writeStdout(source.getBytes(StandardCharsets.UTF_8));
      return 2;
    }

    out.writeStdout(result.source().getBytes(StandardCharsets.UTF_8));
    return 0;
  }

  /**
   * Drives a git-backed mode. Validation, repository detection, formatter construction and the check/write split are shared; the two modes
   * differ only in which files they select ({@code selector}) and whether the formatted files are re-staged ({@code reStage}). Requires
   * {@code git} on the PATH.
   */
  private int runGitMode(
    final String flag,
    final boolean reStage,
    final GitFileSelector selector,
    final CliConsole out,
    final OptionsResolver resolver
  ) throws IOException {
    if (!files.isEmpty()) {
      out.err("Error: " + flag + " cannot be combined with file arguments.");
      return 2;
    }
    if (stdinFilepath != null) {
      out.err("Error: " + flag + " and --stdin-filepath are mutually exclusive.");
      return 2;
    }

    final Path baseDir = workingDir != null ? workingDir : Path.of("").toAbsolutePath();
    final GitClient git = new GitClient(baseDir);

    try {
      if (!git.isInsideWorkTree()) {
        out.err("Error: " + flag + " requires running inside a git repository.");
        return 2;
      }

      final Path repoRoot = git.repoRoot();
      final List<Path> javaFiles = selector.select(git, repoRoot, out);
      if (javaFiles.isEmpty()) {
        return 0;
      }

      final JacetFormatter formatter = new JacetFormatter(resolver.resolve(repoRoot));
      final FormatRunner runner = new FormatRunner(formatter, out);
      if (check) {
        return runner.check(javaFiles);
      }

      final WriteOutcome outcome = runner.write(javaFiles);
      if (reStage) {
        git.add(outcome.changed().stream().map(Path::toString).toList());
      }
      return 0;
    } catch (final GitClient.GitException e) {
      out.err("Error: " + e.getMessage());
      return 2;
    }
  }

  /**
   * Selects the staged Java files to format. Partially-staged files — staged but carrying further unstaged working-tree edits — are skipped
   * so the hook never pulls unintended changes into the commit.
   */
  private List<Path> selectStaged(final GitClient git, final Path repoRoot, final CliConsole out) {
    final Collection<String> unstaged = new HashSet<>(git.unstagedJavaFiles());

    final List<Path> javaFiles = new ArrayList<>();
    for (final String relative : git.stagedJavaFiles()) {
      if (unstaged.contains(relative)) {
        out.err("WARN: skipping partially-staged file (has unstaged changes): " + relative);
        continue;
      }
      final Path absolute = repoRoot.resolve(relative);
      if (Files.isRegularFile(absolute)) {
        javaFiles.add(absolute);
      } else {
        out.err("WARN: staged file missing from working tree, skipping: " + relative);
      }
    }
    return javaFiles;
  }

  /** Selects the working-tree changes to format: tracked files with unstaged modifications plus untracked new files. */
  private List<Path> selectChanged(final GitClient git, final Path repoRoot, final CliConsole out) {
    final Collection<String> relativePaths = new LinkedHashSet<>(git.unstagedJavaFiles());
    relativePaths.addAll(git.untrackedJavaFiles());

    final List<Path> javaFiles = new ArrayList<>();
    for (final String relative : relativePaths) {
      final Path absolute = repoRoot.resolve(relative);
      if (Files.isRegularFile(absolute)) {
        javaFiles.add(absolute);
      }
    }
    return javaFiles;
  }

  private OptionsResolver optionsResolver() {
    return new OptionsResolver(
      printWidth,
      tabWidth,
      this.triState(useTabs, "--use-tabs"),
      this.triState(forceBraces, "--force-braces"),
      endOfLine,
      staticImports,
      importGroups,
      this.triState(removeUnusedImports, "--remove-unused-imports"),
      configFile,
      noConfig
    );
  }

  /**
   * Resolves a negatable boolean option to its tri-state override: {@code value} if the option (either {@code --name} or {@code --no-name})
   * was passed, otherwise {@code null} so the configured value is kept.
   */
  private @Nullable Boolean triState(final boolean value, final String name) {
    final CommandSpec resolved = Objects.requireNonNull(this.spec, "command spec not injected");
    return resolved.commandLine().getParseResult().hasMatchedOption(name) ? value : null;
  }

  private static List<Path> collectJavaFiles(final Iterable<Path> paths) throws IOException {
    final List<Path> result = new ArrayList<>();
    for (final Path path : paths) {
      if (Files.isDirectory(path)) {
        try (final Stream<Path> stream = Files.walk(path)) {
          stream
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(result::add);
        }
      } else if (path.toString().endsWith(".java")) {
        if (!Files.isRegularFile(path)) {
          throw new NoSuchFileException(path.toString());
        }
        result.add(path);
      }
    }
    return result;
  }

  /** Selects the files a git-backed mode should format, emitting any per-file warnings to {@code out}. */
  @FunctionalInterface
  private interface GitFileSelector {

    List<Path> select(GitClient git, Path repoRoot, CliConsole out);
  }

  /**
   * Supplies {@code --version} output from {@link BuildInfo#VERSION}, which is generated at build time from the axion-release version — so
   * the binary's reported version always tracks the published artifact.
   */
  static final class VersionProvider implements CommandLine.IVersionProvider {

    @Override
    public String[] getVersion() {
      return new String[] { BuildInfo.VERSION };
    }
  }

  static void main(final String[] args) {
    final int exitCode = new CommandLine(new Main()).execute(args);
    System.exit(exitCode);
  }
}
