package com.cobbletowers.api.modifier;

/**
 * How much a modifier asks of the party, for TDS #2's "meaningful risk/reward".
 *
 * <p>Presentation and drafting read this; nothing computes with it. A card's actual difficulty comes
 * from its effect, and a tier that silently scaled numbers would be a second, hidden place where
 * difficulty is decided.
 */
public enum RiskTier {
    MINOR,
    MODERATE,
    SEVERE
}
