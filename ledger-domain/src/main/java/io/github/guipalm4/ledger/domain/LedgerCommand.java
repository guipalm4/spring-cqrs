package io.github.guipalm4.ledger.domain;

/**
 * Comandos aceitos pelo ledger. A hierarquia fechada deixa o despacho exaustivo em tempo de compilação.
 * Os construtores validam a forma da entrada; as regras de negócio ficam em {@link Account}.
 */
public sealed interface LedgerCommand permits OpenAccount, Deposit, Withdraw, Transfer {
}
