# ADR-0002 — Evolução em três fases, uma variável por vez

- **Status:** Aceito
- **Data:** 2026-10-18

## Contexto

Comparar diretamente "Postgres único" com "CQRS com banco de leitura separado" muda três variáveis ao
mesmo tempo: modelo de dados, armazenamento e modelo de consistência (síncrono → eventual). Qualquer
ganho medido fica sem causa atribuível, e o experimento vira anedota.

Além disso, um revisor experiente vai checar se a baseline é honesta. Se o problema desaparece com um
índice, o projeto prova o contrário do que pretende.

## Decisão

1. **v1 — Baseline:** modelo único no Postgres, **bem feito**: índices adequados (incluindo `pg_trgm` e
   `tsvector` para Q4), `balance_after` no lançamento, `EXPLAIN ANALYZE` documentado.
2. **v2 — CQRS lógico:** comandos e consultas com modelos separados, projeções em tabelas do **mesmo**
   Postgres, atualizadas **na mesma transação** do comando. Isola o efeito de separar o modelo.
3. **v3 — CQRS físico:** projeções em stores separados (MongoDB, depois OpenSearch), atualizadas de
   forma **assíncrona**. Isola o efeito de separar o armazenamento e introduz consistência eventual.

v2 e v3 vivem no mesmo módulo (`app-cqrs`) e diferem por profile. v1 é um módulo separado e congelado
após o M2, para que o benchmark rode todas as fases a partir do mesmo commit.

Na v3, o mesmo artefato `app-cqrs` sobe em **dois containers**: `command-api` (profile `role=command`)
e `query-api` (profile `role=query`). Cada um expõe só a sua metade do contrato. Isso torna a
separação visível na topologia e permite escalar a leitura sem tocar na escrita (hipótese H7).
Não há segundo artefato: a separação é de implantação, não de código.

## Alternativas consideradas

- **Direto v1 → v3:** menos trabalho, mas mistura variáveis (ver Contexto).
- **Fases como branches/tags apenas:** mostra a evolução no histórico, mas impede rodar v1 e v3 lado a
  lado no mesmo commit. As tags continuam existindo como marcos, não como forma de execução.

## Consequências

- Duplicação controlada: o domínio (`ledger-domain`) é compartilhado; só a camada de aplicação e
  persistência difere.
- O README pode contar a história em três passos com causa e efeito claros.
- Um resultado possível — e aceitável — é que v2 já resolva a maior parte, tornando a v3 difícil de
  justificar para este volume. Isso alimenta o ADR "quando NÃO usar CQRS" (M6).
