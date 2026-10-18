# ADR-0004 — Sincronização: outbox do Spring Modulith → Kafka

- **Status:** Aceito
- **Data:** 2026-10-18

## Contexto

Na v3 o comando grava no Postgres e as projeções vivem em outros stores. Gravar no banco e publicar no
broker em passos separados é um *dual write*: se o processo cair entre os dois, a projeção diverge para
sempre. É preciso atomicidade entre "estado mudou" e "evento será publicado".

## Decisão

- **Transactional outbox via Spring Modulith:** o evento de domínio é registrado no Event Publication
  Registry (JDBC) **na mesma transação** do comando; a externalização para Kafka ocorre após o commit.
  Publicações incompletas são reenviadas via `IncompleteEventPublications`.
- **Kafka** em modo KRaft, broker único (alta disponibilidade fora de escopo).
- **Evento por lançamento** (`EntryPosted`), não por transferência: cada evento afeta uma única conta.
  **Chave de partição = `accountId`**, garantindo ordem por conta.
- Cada evento carrega **`accountSequence`** monotônico. O projetor guarda a última sequência aplicada
  por conta e descarta duplicatas (`seq ≤ última`) — idempotência sob entrega *at-least-once*.
- Um consumer group por projeção (`projector-mongo`, `projector-opensearch`): atraso e reprocessamento
  independentes.
- Na v2, o mesmo evento é tratado por listener **síncrono na transação** — código de projeção
  reaproveitado, só muda o transporte.

## Alternativas consideradas

| Opção | Por que não agora |
|---|---|
| Outbox + Debezium (CDC) | Padrão muito citado em finanças, mas adiciona Kafka Connect e mais containers antes de qualquer resultado. Fica como stretch, documentado |
| Eventos assíncronos em processo, sem broker | Mais simples, mas não demonstra separação física nem consumers independentes |
| Publicar direto no Kafka dentro da transação | Dual write — é exatamente o problema a evitar |

## Consequências

- Métrica nova e obrigatória: **atraso da projeção** (do commit até a visibilidade na leitura).
- Projeção pode ser reconstruída a partir do ledger no Postgres (fonte da verdade), não do Kafka — o
  Kafka não é event store (sem event sourcing). Reconstrução é stretch.
- Modo de conclusão do registro de publicações: **`DELETE`**. Publicação concluída é apagada, então a
  tabela fica pequena e não distorce o Postgres no benchmark (milhões de eventos por execução). O
  histórico de eventos não se perde: a fonte da verdade é o ledger. Falhas continuam visíveis, porque
  publicações incompletas não são apagadas.
