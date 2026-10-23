package io.github.guipalm4.ledger.cqrs.query;

import io.github.guipalm4.ledger.cqrs.Roles;
import io.github.guipalm4.ledger.cqrs.query.Views.CategoryView;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Catálogo de categorias da v3: 13 linhas fixas, criadas pela migração, lidas do Postgres uma vez e mantidas em
 * memória (ADR-0012). Os lançamentos do Mongo guardam só o id, como os blocos da v2. A leitura é no primeiro uso, e
 * não na partida, para o treino do AOT cache continuar sem banco.
 */
@Component
@Profile(Roles.QUERY)
class Categories {

    private record Catalog(Map<Short, CategoryView> byId, Map<String, Short> idByCode) {
    }

    private final JdbcClient jdbc;
    private volatile @Nullable Catalog catalog;

    Categories(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    CategoryView byId(short id) {
        var category = catalog().byId().get(id);
        if (category == null) {
            throw new IllegalStateException("unknown category id " + id);
        }
        return category;
    }

    Optional<Short> idOf(String code) {
        return Optional.ofNullable(catalog().idByCode().get(code));
    }

    private Catalog catalog() {
        var loaded = catalog;
        if (loaded == null) {
            var byId = new HashMap<Short, CategoryView>();
            var idByCode = new HashMap<String, Short>();
            jdbc.sql("SELECT id, code, name FROM category").query(rs -> {
                byId.put(rs.getShort("id"), new CategoryView(rs.getString("code"), rs.getString("name")));
                idByCode.put(rs.getString("code"), rs.getShort("id"));
            });
            loaded = new Catalog(Map.copyOf(byId), Map.copyOf(idByCode));
            catalog = loaded;
        }
        return loaded;
    }
}
