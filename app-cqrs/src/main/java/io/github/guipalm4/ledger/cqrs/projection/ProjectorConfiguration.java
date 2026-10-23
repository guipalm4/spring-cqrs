package io.github.guipalm4.ledger.cqrs.projection;

import io.github.guipalm4.ledger.cqrs.Roles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
@Profile(Roles.PROJECTOR)
class ProjectorConfiguration {

    /**
     * Falha ao aplicar (Mongo fora do ar, por exemplo) repete o mesmo lançamento até dar certo: pular um lançamento
     * deixaria a projeção errada para sempre, e o atraso crescente aparece no {@code ledger.projection.lag}.
     */
    @Bean
    DefaultErrorHandler retryUntilApplied() {
        return new DefaultErrorHandler(new FixedBackOff(1_000, FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
