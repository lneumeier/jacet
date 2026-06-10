package de.irotation.jacet.document;

/**
 * Like {@link Indent}, but only increases the indent level when the enclosing group breaks. In flat mode the contents render at the current
 * indent. Used for argument lists and similar constructs where the brackets stay flat but the inner block content (e.g. a lambda body
 * block) must not pick up the bracket-list indent.
 */
public record BreakIndent(Document contents) implements Document {}
