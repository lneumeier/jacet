package de.irotation.jacet.document;

/**
 * Conditional indent: adds {@code tabWidth} to the indent counter for {@code contents} if and only if {@code group} resolved to BREAK mode
 * during rendering. Lets a sibling document react to a group's flat/break decision without being processed inside that group's mode
 * propagation.
 *
 * <p>Use case: assignment wrapping. The line-break group sits separately from the RHS so the RHS keeps its own internal break decisions
 * (independent of whether `=` is followed by space or by newline). When the line-break group breaks, the RHS still needs to render at
 * indent+1 — IndentIfBreak supplies that.
 *
 * <p>Construct via {@link Document#indentIfBreak(Group, Document)}.
 */
public record IndentIfBreak(Group group, Document contents) implements Document {}
