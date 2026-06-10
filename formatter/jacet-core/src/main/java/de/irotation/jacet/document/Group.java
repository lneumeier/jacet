package de.irotation.jacet.document;

/**
 * Try to print contents on one line (FLAT). If it doesn't fit, switch to BREAK.
 *
 * <p>{@code shouldBreak} forces BREAK mode unconditionally, bypassing the {@code fits()} check. It
 * propagates "must break" from inner content (a {@link HardLine}, or a nested {@link Group} that itself has {@code shouldBreak}) up the
 * tree. Without this flag, a {@code fits()} that short-circuits on a forced break inside the group would leave the outer group in FLAT mode
 * while the inner break still emits a newline — producing a mid-line break.
 *
 * <p>Construct via {@link Document#group(Document)} (auto-detects from contents).
 */
public record Group(Document contents, boolean shouldBreak) implements Document {}
