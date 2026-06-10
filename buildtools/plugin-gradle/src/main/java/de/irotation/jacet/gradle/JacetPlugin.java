package de.irotation.jacet.gradle;

import java.io.File;
import java.nio.file.Path;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginExtension;

import de.irotation.jacet.ConfigLoader;

/**
 * Gradle plugin that registers {@code formatJava} and {@code checkFormatJava} tasks.
 *
 * <p>Applies to projects that use the {@code java} plugin. Collects all Java source files from all source sets.
 */
public final class JacetPlugin implements Plugin<Project> {

  @Override
  public void apply(final Project project) {
    final JacetExtension extension = project.getExtensions().create("jacet", JacetExtension.class);

    project.getPlugins().withId("java", javaPlugin -> {
      final JavaPluginExtension javaExt = project.getExtensions().getByType(JavaPluginExtension.class);

      project.getTasks().register("formatJava", FormatTask.class, task -> {
        task.setGroup("formatting");
        task.setDescription("Format Java source files with jacet.");
        task.getExtension().set(extension);
        wireConfigFile(project, extension, task);
        task.getSourceFiles().from(collectJavaSources(project, javaExt));
        task.getStampFile().set(project.getLayout().getBuildDirectory().file("jacet/formatJava.stamp"));
      });

      project.getTasks().register("checkFormatJava", CheckTask.class, task -> {
        task.setGroup("formatting");
        task.setDescription("Check that Java source files are formatted.");
        task.getExtension().set(extension);
        wireConfigFile(project, extension, task);
        task.getSourceFiles().from(collectJavaSources(project, javaExt));
        task.getStampFile().set(project.getLayout().getBuildDirectory().file("jacet/checkFormatJava.stamp"));
      });
    });
  }

  /**
   * Wires the task's config-file input <em>lazily</em>: an explicit {@code jacet { configFile = ... }} wins, otherwise
   * the nearest {@code .jacet.json} found by walking up from the project directory (mirroring the CLI). The lookup
   * runs inside a provider, not eagerly here — the {@code jacet { }} block (including {@code configFile}) is configured
   * after {@code apply()}, so reading the extension now would miss it. A {@code null} result leaves the property absent.
   */
  private static void wireConfigFile(final Project project, final JacetExtension extension, final AbstractJacetTask task) {
    task.getConfigFile().fileProvider(
      project.provider(() -> {
        if (extension.getConfigFile().isPresent()) {
          return extension.getConfigFile().get().getAsFile();
        }
        final Path located = ConfigLoader.locate(project.getProjectDir().toPath());
        return located != null ? located.toFile() : (File) null;
      })
    );
  }

  private static FileCollection collectJavaSources(final Project project, final JavaPluginExtension javaExt) {
    return javaExt
      .getSourceSets()
      .stream()
      .map(sourceSet -> (FileCollection) sourceSet.getJava().getAsFileTree())
      .reduce(project.files(), FileCollection::plus);
  }
}
