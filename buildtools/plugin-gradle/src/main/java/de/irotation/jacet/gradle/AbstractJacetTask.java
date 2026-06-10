package de.irotation.jacet.gradle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.work.Incremental;
import org.jspecify.annotations.Nullable;

import de.irotation.jacet.ConfigLoader;
import de.irotation.jacet.FormatResult;
import de.irotation.jacet.FormatterOptions;
import de.irotation.jacet.ImportOptions;

/**
 * Shared plumbing for jacet tasks: source inputs, configuration fingerprint, and a stamp-file output so Gradle can evaluate
 * {@code UP_TO_DATE} across runs.
 */
@DisableCachingByDefault(because = "Subclasses override with their own caching policy")
public abstract class AbstractJacetTask extends DefaultTask {

  @Incremental
  @InputFiles
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract ConfigurableFileCollection getSourceFiles();

  /**
   * The configured {@link JacetExtension}, carried into the task so its DSL values resolve to {@link FormatterOptions} at execution time.
   * Marked {@code @Internal} because the individual options are fingerprinted separately as {@code @Input} providers (see
   * {@code resolvedOptions}) — the extension object itself is wiring, not a build input.
   */
  @Internal
  public abstract Property<JacetExtension> getExtension();

  /**
   * The resolved {@code .jacet.json} backing the configuration, or absent when none applies. Wired by {@code JacetPlugin} (explicit
   * {@code configFile}, else the walk-up lookup). Declared as an {@code @InputFile} so a change to its <em>content</em> invalidates the
   * task — without this, editing {@code .jacet.json} would leave the task spuriously {@code UP_TO_DATE}. {@code @Optional} because the file
   * may not exist; {@code PathSensitivity.NONE} because only the content matters, not where the file lives.
   */
  @Optional
  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getConfigFile();

  @OutputFile
  public abstract RegularFileProperty getStampFile();

  /**
   * Cache for {@link #baseOptions()}. The parsed {@code .jacet.json} feeds every {@code @Input} fingerprint getter below plus the task
   * action, and each {@code Provider.get()} re-evaluates its chain — without this cache the file would be read and regex-parsed up to eight
   * times per task per build. Transient so the configuration cache never serializes it; content changes still invalidate the task via the
   * {@code @InputFile} fingerprint of {@link #getConfigFile()}.
   */
  private transient @Nullable FormatterOptions cachedBaseOptions;

  /**
   * Resolves the effective options: the {@code .jacet.json} base (or defaults when absent) with the {@link JacetExtension} DSL layered on
   * top. The single resolution point shared by the {@code @Input} fingerprints below and the task actions. Falls back to defaults when the
   * extension is unset, avoiding a cryptic "no value" error if a task is registered outside {@code JacetPlugin}.
   *
   * <p>Each formatter option is additionally exposed as its own {@code @Input} (below) so Gradle fingerprints them
   * canonically rather than via {@code FormatterOptions.toString()}.
   */
  Provider<FormatterOptions> resolvedOptions() {
    return this.getExtension()
      .map(ext -> ext.applyOverrides(this.baseOptions()))
      .orElse(FormatterOptions.defaults());
  }

  private FormatterOptions baseOptions() {
    if (this.cachedBaseOptions == null) {
      this.cachedBaseOptions = this.getConfigFile().isPresent()
        ? ConfigLoader.parseConfigFile(this.getConfigFile().get().getAsFile().toPath())
        : FormatterOptions.defaults();
    }
    return this.cachedBaseOptions;
  }

  @Input
  public Provider<Integer> getPrintWidth() {
    return this.resolvedOptions().map(FormatterOptions::printWidth);
  }

  @Input
  public Provider<Integer> getTabWidth() {
    return this.resolvedOptions().map(FormatterOptions::tabWidth);
  }

  @Input
  public Provider<Boolean> getUseTabs() {
    return this.resolvedOptions().map(FormatterOptions::useTabs);
  }

  @Input
  public Provider<Boolean> getForceBraces() {
    return this.resolvedOptions().map(FormatterOptions::forceBraces);
  }

  @Input
  public Provider<String> getEndOfLineName() {
    return this.resolvedOptions().map(o -> o.endOfLine().name());
  }

  @Input
  public Provider<String> getStaticImportsPosition() {
    return this.resolvedOptions().map(o -> o.imports().staticPosition().name());
  }

  @Input
  public Provider<List<String>> getImportGroupPrefixes() {
    return this.resolvedOptions().map(o -> o.imports().groups().stream().map(ImportOptions.Group::prefix).toList());
  }

  /** Logs each parse error against the file as a warning; the file is then skipped, leaving its source untouched. */
  final void warnParseErrors(final File file, final FormatResult result) {
    for (final String error : result.parseErrors()) {
      this.getLogger().warn("{}: {}", file, error);
    }
  }

  /**
   * Logs a token-coverage violation against the file. The formatter fell back to the original source rather than emit output that dropped
   * or duplicated a token, so the file is skipped — but never silently: surfacing this is the whole point of the verification guard.
   */
  final void warnVerificationFailure(final File file, final FormatResult result) {
    this.getLogger().warn("{}: skipped — verification failed:", file);
    for (final String error : result.verificationErrors()) {
      this.getLogger().warn("  {}", error);
    }
  }

  /**
   * Writes the stamp file Gradle uses to evaluate {@code UP_TO_DATE} across runs. The content is empty: only the file's existence matters,
   * and empty content keeps the task output deterministic.
   */
  final void writeStamp() {
    try {
      final File stamp = this.getStampFile().get().getAsFile();
      Files.createDirectories(stamp.getParentFile().toPath());
      Files.writeString(stamp.toPath(), "");
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
