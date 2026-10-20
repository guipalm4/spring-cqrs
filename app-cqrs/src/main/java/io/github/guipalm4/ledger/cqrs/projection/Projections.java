package io.github.guipalm4.ledger.cqrs.projection;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atualiza as projeções da v2 com um lançamento recém-gravado, na transação do comando (ADR-0011).
 * <p>
 * O lançamento é lido da {@code ledger_entry} pelo id, com as mesmas funções SQL que o
 * {@code rebuild_projections()} usa: atualizar um a um e reconstruir do zero dão a mesma projeção. Não há
 * disputa entre comandos pelo mesmo bloco: o comando já tem o lock da conta ({@code SELECT … FOR UPDATE}).
 */
@Component
public class Projections {

    private final JdbcClient jdbc;

    Projections(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(long entryId) {
        // O bloco do mês cresce no fim; palavras e contrapartes só servem para descartar blocos na busca, então
        // um nome que já aparece como trecho de outro não precisa ser acrescentado.
        jdbc.sql("""
                        INSERT INTO statement_view AS v (account_id, month, entries, search_vector, counterparties)
                        SELECT e.account_id, ledger_month(e.occurred_at), jsonb_build_array(statement_entry(e)),
                               strip(to_tsvector('simple', e.description || ' ' || e.counterparty_name)),
                               e.counterparty_name
                        FROM ledger_entry e WHERE e.id = :entryId
                        ON CONFLICT (account_id, month) DO UPDATE SET
                            entries = v.entries || excluded.entries,
                            search_vector = v.search_vector || excluded.search_vector,
                            counterparties = CASE WHEN strpos(v.counterparties, excluded.counterparties) > 0
                                                  THEN v.counterparties
                                                  ELSE v.counterparties || E'\\n' || excluded.counterparties END""")
                .param("entryId", entryId)
                .update();
        jdbc.sql("""
                        INSERT INTO monthly_summary AS s (account_id, month, category_id, credits, debits, entries)
                        SELECT account_id, ledger_month(occurred_at), category_id,
                               greatest(amount, 0), greatest(-amount, 0), 1
                        FROM ledger_entry WHERE id = :entryId
                        ON CONFLICT (account_id, month, category_id) DO UPDATE SET
                            credits = s.credits + excluded.credits,
                            debits = s.debits + excluded.debits,
                            entries = s.entries + 1""")
                .param("entryId", entryId)
                .update();
    }
}
