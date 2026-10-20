package io.github.guipalm4.ledger.cqrs.command;

import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

interface CategoryRepository extends Repository<CategoryRow, Short> {

    @Query("SELECT id FROM category WHERE code = :code")
    Optional<Short> findIdByCode(String code);
}
