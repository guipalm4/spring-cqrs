package io.github.guipalm4.ledger.domain;

/** Natureza de um lançamento no ledger. Transferências geram um par OUT/IN que soma zero. */
public enum EntryKind {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER_IN,
    TRANSFER_OUT
}
