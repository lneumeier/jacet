package de.irotation.jacet.document;

/** Print breakContents in BREAK mode, flatContents in FLAT mode. */
public record IfBreak(Document breakContents, Document flatContents) implements Document {}
