package de.irotation.jacet.gradle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import de.irotation.jacet.FormatResult;
import de.irotation.jacet.FormatterOptions;
import de.irotation.jacet.JacetFormatter;

/**
 * Checks that Java source files are formatted. Fails the build if any file is not formatted.
 *
 * <p>Unlike {@link FormatTask}, this validates the <em>entire</em> source set whenever it runs — not just the
 * incremental delta. A validation task must guarantee the whole state, and a changes-only check can let an unchanged-but-unformatted file
 * slip through (e.g. after an intermediate failed run that nonetheless advances Gradle's incremental baseline). The stamp output still
 * drives {@code UP_TO_DATE}, so Gradle skips the task entirely when no source file or option changed — when it does run, it runs in full.
 */
@DisableCachingByDefault(because = "Validation task without cacheable outputs")
public abstract class CheckTask extends AbstractJacetTask {

  @TaskAction
  public void check() {
    final FormatterOptions options = this.resolvedOptions().get();
    final JacetFormatter formatter = new JacetFormatter(options);
    final List<String> unformatted = new ArrayList<>();

    for (final File file : this.getSourceFiles().getFiles()) {
      if (!file.isFile()) {
        continue;
      }
      if (!this.isFormatted(formatter, file)) {
        unformatted.add(file.toString());
      }
    }

    if (!unformatted.isEmpty()) {
      throw new GradleException(unformatted.size() + " file(s) not formatted. Run 'formatJava' to fix.");
    }

    this.writeStamp();
  }

  /**
   * Returns {@code true} when the file is already formatted, or when it cannot be validated — files that fail to parse or fail output
   * verification are skipped (treated as passing) with a warning, mirroring {@code FormatTask} and the CLI. Only a clean parse that differs
   * from the formatted output counts as "not formatted".
   */
  private boolean isFormatted(final JacetFormatter formatter, final File file) {
    try {
      final String source = Files.readString(file.toPath());
      final FormatResult result = formatter.formatWithResult(source);

      switch (FormatOutcome.classify(source, result)) {
        case PARSE_ERROR -> this.warnParseErrors(file, result);
        case VERIFICATION_ERROR -> this.warnVerificationFailure(file, result);
        case NEEDS_FORMAT -> {
          this.getLogger().lifecycle("Not formatted: {}", file);
          return false;
        }
        case UNCHANGED -> { /* already formatted */ }
      }
      return true;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
