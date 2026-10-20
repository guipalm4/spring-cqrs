package io.github.guipalm4.ledger.cqrs.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    /** Título e versão do contrato, e não da fase: v1, v2 e v3 publicam o mesmo documento (ADR-0005). */
    @Bean
    OpenAPI ledgerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Ledger API")
                .version("1")
                .description("Contrato REST comum às fases v1, v2 e v3. Valores em unidades menores da moeda."));
    }
}
