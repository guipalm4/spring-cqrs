# v1 com pools separados — 2026-10-20: controle da #11

A v1 com um pool de 2 conexões para comandos e outro de 8 para consultas (profile `split-pools`,
[ADR-0010](../../adr/0010-controle-pools-separados.md)), contra a [v1 medida no mesmo dia](../2026-10-20-v1/), com o
mesmo protocolo: dataset de referência recém-carregado antes de cada uma, escrita a 20 comandos/s, leitura a 0, 25,
50 e 75 consultas/s, 3 repetições intercaladas, Apple M1 Pro / Docker Desktop com outros containers parados.
Ambiente em [env.md](env.md), números em [summary.md](summary.md).

## Resposta

> *Um pool só para comandos, com o mesmo total de conexões, elimina a degradação da escrita sem mudar a arquitetura?*

**Não.** Separar os pools não mudou a frequência do colapso nem protegeu os comandos quando ele aconteceu:

| Carga de leitura | Colapsos, v1 | Colapsos, pools separados | p95 dos comandos nas execuções estáveis, v1 | pools separados |
|---|---|---|---:|---:|
| 0 (S1) | 0 de 3 | 0 de 3 | 22–48 ms | 25–49 ms |
| 25/s | 0 de 3 | 0 de 3 | 16–50 ms | 20–66 ms |
| 50/s | 0 de 3 | 1 de 3 | 19–808 ms | 128–178 ms |
| 75/s | 2 de 3 | 0 de 3 | 38 ms | 54–132 ms |
| 75/s, só leitura (S2) | 1 de 3 | 1 de 3 | — | — |

- **Frequência:** com leitura a 50/s ou mais, 3 colapsos em 9 na v1 e 2 em 9 com pools separados. Com 3
  repetições por cenário, isso não é diferença (teste exato de Fisher, p = 0,50).
- **No colapso, os comandos caem junto:** p95 de 15 s e 23% de falhas com pool próprio (s3-r050 r3), contra 18,8–40,8 s
  e 3–58% de falhas nos colapsos da v1.
- **Fora do colapso, não há ganho consistente:** as faixas se sobrepõem em todas as cargas. Nas execuções estáveis da
  v1 deste dia, a espera média no pool compartilhado ficou em 0,1–111 ms; havia pouca fila para isolar.

## Mecanismo: o canal que sobra é a JVM

O pool de comandos fica livre enquanto o sistema está estável (espera de 0,2–12 ms). No colapso, ele não é o
problema de partida, mas também não protege:

| Execução | Estado | Comando no banco | Comando segurando a conexão | Espera no pool de comandos | JVM parada em GC (de 180 s) |
|---|---|---:|---:|---:|---:|
| pools separados, s3-r050 r3 | colapso | 13 ms | 110 ms | 2.489 ms | 64,3 s |
| v1, s3-r075 r2 | colapso | 13 ms | 348 ms* | 29.183 ms* | 35,3 s |
| v1, s3-r075 r3 | colapso | 5 ms | 84 ms* | 3.344 ms* | 16,7 s |
| pools separados, as 13 execuções estáveis | estável | 2–5 ms | 5–16 ms | 0,2–12 ms | ≤ 2,4 s |
| v1, as 12 execuções estáveis | estável | 1–4 ms | 5–28 ms* | 0,0–111 ms* | ≤ 1,7 s |

\* Na v1, média de comandos e consultas no pool compartilhado.

1. As consultas fazem fila no pool delas, como no M2. Milhares de requisições esperando (até 1.928 no S2) ficam em
   memória, e o heap enche.
2. A JVM passa a pausar em GC: 32–64 s de pausa em 180 s nos dois colapsos com pools separados, com pausas de até
   ~48 s, que derrubam também a coleta do Prometheus (10 instantes sem métrica em s3-r050 r3). Nas execuções
   estáveis, o GC soma no máximo 2,4 s.
3. A pausa de GC para todas as threads, inclusive comandos que já têm conexão. Um comando que roda em 13 ms no
   banco passa a segurar a conexão por 110 ms em média. A 20 comandos/s, são 2,2 conexões ocupadas: mais que as 2
   do pool. O pool de comandos satura também (fila de até 285), e os comandos chegam a 30 s de p95 dentro da
   aplicação.

Leitura combinada antes de medir (ADR-0010): **não basta**. Separar as conexões tira os comandos da fila das
consultas, mas não do heap, da CPU e das pausas da JVM que as duas cargas dividem.

## O que isso muda

- **O mecanismo do M2 estava incompleto.** "O canal é o pool" descreve onde as requisições esperam, não por que o
  colapso não se desfaz: a fila no pool enche o heap, e as pausas de GC atrasam todos. O M2 não media GC; as falhas
  de coleta do Prometheus que ele registrou no colapso são compatíveis com pausas longas, mas isso é inferência.
- **H3 continua em aberto, e com um alvo mais preciso:** o que a v3 tem e esta variante não tem é processo próprio
  para a escrita (heap e GC separados). É isso que o S3 da v3 vai testar.
- **Ainda há uma correção barata não medida: contrapressão.** O ADR-0009 registrou que, com virtual threads, a v1
  aceita requisições sem limite e todas esperam o pool. Limitar as consultas em espera (recusar com 503 em vez de
  enfileirar) atacaria o heap, que é o canal que sobrou. Sem medir isso, parte do ganho da v3 pode vir da
  contrapressão, e não do CQRS.
- **v2 (M3):** projeções no mesmo processo dividem a JVM com os comandos. Se reduzirem o tempo das consultas, podem
  evitar a fila e, com ela, o GC; se não evitarem o colapso, não protegem a escrita.

## Limitações e ameaças à validade

- **A variação entre dias é do tamanho do efeito procurado.** A mesma v1 colapsou em 6 de 9 em 19/10 e em 3 de 9 em
  20/10 (p = 0,17), e os comandos do S1 usaram a conexão por 13,4 ms contra 6,0 ms, com ambiente declarado idêntico.
  Por isso a comparação aqui é só com a v1 medida no mesmo dia, e as próximas fases seguem a mesma regra
  (ADR-0010).
- **3 repetições por cenário** só distinguem diferenças grandes na frequência de colapso. "Não mudou" quer dizer que
  não apareceu diferença; uma diferença pequena, se existir, ficaria escondida.
- **A divisão 2 + 8 foi dimensionada para o uso normal da conexão** (até 30 ms). Com o GC inflando o uso para 110 ms,
  o pool de comandos saturou. Uma terceira conexão daria capacidade para ~27 comandos/s nesse estado, mas não
  evitaria 64 s de pausa em 180 s. A divisão não foi ajustada depois do resultado.
- **Séries temporais exportadas de novo:** depois de ver o colapso, o `timeseries.csv` e o `gc_pause_total_s` desta
  pasta foram extraídos de novo do Prometheus (ainda em execução), com a janela de cada execução tirada do
  `docker-stats.csv`, para incluir o GC. As demais métricas foram geradas durante o `run.sh`.
- **Mesmas limitações do M2:** Gatling no host disputando CPU com a VM do Docker, `Premature close` só no colapso,
  memória da aplicação perto do limite do container.
