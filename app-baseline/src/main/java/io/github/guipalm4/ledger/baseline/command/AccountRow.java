package io.github.guipalm4.ledger.baseline.command;

import io.github.guipalm4.ledger.domain.Account;
import java.util.Currency;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("account")
record AccountRow(@Id UUID id, String holderId, String holderName, String currency, long balance,
        long overdraftLimit) {

    Account toDomain() {
        return new Account(id, holderId, holderName, Currency.getInstance(currency), balance, overdraftLimit);
    }
}
