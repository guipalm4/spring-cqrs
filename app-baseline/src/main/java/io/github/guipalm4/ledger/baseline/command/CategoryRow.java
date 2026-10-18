package io.github.guipalm4.ledger.baseline.command;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("category")
record CategoryRow(@Id Short id, String code, String name) {
}
