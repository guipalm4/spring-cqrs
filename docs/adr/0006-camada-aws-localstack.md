# ADR-0006 — Camada AWS com LocalStack

- **Status:** Aceito
- **Data:** 2026-10-18

## Contexto

Queremos mostrar familiaridade com AWS sem custo de nuvem, usando LocalStack. O risco é adicionar
serviços que não reforçam o tema do projeto (CQRS) e só aumentam escopo, ou que distorcem o benchmark.

Restrição do projeto: **o plano gratuito é o norte**. Nada que dependa de plano pago entra.

Fatos verificados em 2026-10-18 (documentação e código-fonte do LocalStack):

- Desde a versão 2026.03.0 existe uma imagem única (`localstack/localstack`), e os serviços liberados
  dependem do token de acesso. **Sem conta e `LOCALSTACK_AUTH_TOKEN`, a imagem não sobe.**
- O plano gratuito é o **Hobby**: uso não comercial (portfólio se encaixa), exige conta.
- Hobby inclui **API Gateway REST (v1)**, Secrets Manager, KMS, S3, IAM, SQS, SNS, Lambda, DynamoDB e
  CloudWatch.
- **API Gateway v2 (HTTP API) exige plano Base, pago** — fora de escopo.
- A REST API do LocalStack implementa a integração **`HTTP_PROXY`** (repassa a requisição para um
  backend HTTP, como os containers da aplicação).
- No emulador, **throttling e cotas de usage plans não são aplicados**, e **cache de stage/método não
  tem efeito**.
- Hobby **não tem persistência de estado**: ao reiniciar o container, os recursos somem.
- Spring Cloud AWS 4.x declara compatibilidade só com Spring Boot 4.0.x; o projeto usa Boot 4.1.

## Decisão

Marco **M7**, posterior ao M6 e fora do caminho crítico:

1. **Terraform + `tflocal`** descreve todos os recursos AWS. Como o Hobby não persiste estado,
   `make aws-up` sobe o LocalStack e aplica o Terraform do zero toda vez. O mesmo código poderia ser
   aplicado numa conta real.
2. **API Gateway REST (v1)** na frente da v3, com integração `HTTP_PROXY`:
   - comandos (`POST`) → `command-api`
   - consultas (`GET`) → `query-api`
   - rotas derivadas do OpenAPI gerado pelo springdoc, para o contrato continuar sendo único.
3. **Secrets Manager** guarda as credenciais de Postgres, Mongo e OpenSearch. As aplicações leem na
   inicialização com o **AWS SDK for Java v2**, por meio de um `EnvironmentPostProcessor` pequeno, sem
   Spring Cloud AWS (incompatível com Boot 4.1 hoje e traria o trem do Spring Cloud junto).
4. **`docs/aws-mapping.md`**: como cada componente iria para a AWS real (RDS, MSK, Amazon OpenSearch
   Service, ECS, DocumentDB ou MongoDB Atlas), com ressalvas — DocumentDB não é 100% compatível com
   MongoDB; o gateway real aplicaria throttling e cache que o emulador ignora.
5. **CI não roda LocalStack.** O token pessoal não deve ir para o CI, e o pipeline das fases v1–v3 não
   pode depender de conta externa. O M7 é validado localmente com `make aws-up` e um smoke test que
   chama as rotas pelo gateway.
6. **Token fora do repositório:** `LOCALSTACK_AUTH_TOKEN` vem do ambiente; o repositório traz só
   `.env.example` e instruções de criação da conta gratuita no README.

Contingência: se o plano Hobby deixar de existir ou mudar de termos, o substituto é o **Moto em modo
servidor** (open source, sem conta), que também emula API Gateway REST e Secrets Manager. O Terraform
continua o mesmo, mudando só o endpoint.

## Alternativas consideradas

| Opção | Por que não |
|---|---|
| API Gateway v2 (HTTP API) | Exige plano pago |
| SQS/SNS no lugar do Kafka | Duplica o transporte e contradiz o ADR-0004 |
| Lambda como projetor | Muda a garantia de ordem por conta e sai do tema |
| DynamoDB | Terceiro store de leitura; dilui o argumento |
| CloudWatch | Duplica Prometheus e Grafana |
| MSK / OpenSearch Service / DocumentDB / RDS emulados | Fora do plano gratuito ou só embrulham containers já existentes |
| Cognito / autorizador | Autenticação está fora de escopo |
| Spring Cloud AWS | Linha 4.x não declara suporte ao Boot 4.1 |
| KMS e S3 | Disponíveis no Hobby, mas ficam como opcionais (ver SCOPE) para não alongar o M7 |

## Consequências

- O benchmark **nunca** passa pelo gateway (ADR-0005, regra 7).
- Throttling e cache não são configurados para "demonstração"; o README lista essas limitações do
  emulador e aponta o comportamento real no `aws-mapping.md`.
- O compose ganha o profile `aws`, opcional; as fases v1–v3 continuam rodando sem LocalStack e sem
  conta.
- Quem clonar o repositório precisa criar uma conta gratuita no LocalStack para rodar o M7 — custo
  aceito e documentado.
