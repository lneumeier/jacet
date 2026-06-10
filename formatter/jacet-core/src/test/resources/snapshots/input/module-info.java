import java.util.List;

module de.irotation.jacet {
  requires java.base;
  requires transitive java.logging;
  requires static java.compiler;

  exports de.irotation.jacet;
  exports de.irotation.jacet.config;
  exports de.irotation.jacet.formatting to de.irotation.jacet.cli;

  opens de.irotation.jacet to org.junit.platform.commons;

  uses java.util.spi.ToolProvider;

  provides java.util.spi.ToolProvider with de.irotation.jacet.cli.Main;
}
