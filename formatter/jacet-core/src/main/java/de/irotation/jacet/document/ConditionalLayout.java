package de.irotation.jacet.document;

/**
 * Try {@code primary} first; fall back to {@code fallback} if primary's first line wouldn't fit at the current column. Mirrors prettier's
 * {@code conditionalGroup} for two-option cases — typically argument-list layout where layout A keeps the signature inline (with the body
 * wrapping inside) and layout B breaks every argument onto its own line. The selection is made at print time via a fits-check on
 * {@code primary}; the choice is local and doesn't depend on enclosing groups' decisions.
 *
 * <p>When {@code strict} is true, the printer performs a speculative render of the primary layout and checks that ALL lines stay within
 * printWidth (not just the first line). Use this for cases where primary can break internally (e.g. an argument list whose hanging layout
 * includes a lambda that wraps inside itself — the first line fits but subsequent args land at unexpected columns).
 */
public record ConditionalLayout(Document primary, Document fallback, boolean strict) implements Document {}
