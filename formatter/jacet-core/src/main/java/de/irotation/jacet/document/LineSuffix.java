package de.irotation.jacet.document;

/** Print contents at the end of the current line (for trailing comments). */
public record LineSuffix(Document contents) implements Document {}
