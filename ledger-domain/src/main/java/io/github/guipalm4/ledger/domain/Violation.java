package io.github.guipalm4.ledger.domain;

/**
 * Motivos pelos quais um comando válido é recusado pelas regras do ledger.
 */
public enum Violation {
    ACCOUNT_NOT_FOUND,
    ACCOUNT_ALREADY_EXISTS,
    INSUFFICIENT_FUNDS,
    CURRENCY_MISMATCH,
    SAME_ACCOUNT,
    UNKNOWN_CATEGORY
}
