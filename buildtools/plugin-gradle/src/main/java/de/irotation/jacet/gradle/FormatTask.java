package de.irotation.jacet.gradle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

import org.gradle.api.tasks.TaskAction;
import org.gradle.work.ChangeType;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.work.FileChange;
import org.gradle.work.InputChanges;

import de.irotation.jacet.FormatResult;
import de.irotation.jacet.FormatterOptions;
import de.irotation.jacet.JacetFormatter;

/**
 * Formats Java source files in-place.
 */
@DisableCachingByDefault(because = "Modifies source files in-place")
public abstract class FormatTask extends AbstractJacetTask {

  @TaskAction
  public void format(final InputChanges inputChanges) {
    final FormatterOptions options = this.resolvedOptions().get();
    final JacetFormatter formatter = new JacetFormatter(options);
    int changed = 0;

    for (final FileChange change : inputChanges.getFileChanges(this.getSourceFiles())) {
      if (change.getChangeType() == ChangeType.REMOVED) {
        continue;
      }
      final File file = change.getFile();
      if (!file.isFile()) {
        continue;
      }
      if (this.formatFile(formatter, file)) {
        changed++;
      }
    }

    if (changed > 0) {
      this.getLogger().lifecycle("{} file(s) formatted.", changed);
    }

    this.writeStamp();
  }

  private boolean formatFile(final JacetFormatter formatter, final File file) {
    try {
      final String source = Files.readString(file.toPath());
      final FormatResult result = formatter.formatWithResult(source);

      switch (FormatOutcome.classify(source, result)) {
        case PARSE_ERROR -> this.warnParseErrors(file, result);
        case VERIFICATION_ERROR -> this.warnVerificationFailure(file, result);
        case NEEDS_FORMAT -> {
          Files.writeString(file.toPath(), result.source());
          this.getLogger().lifecycle("Formatted: {}", file);
          return true;
        }
        case UNCHANGED -> { /* already formatted */ }
      }
      return false;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
