package io.github.guipalm4.ledger.cqrs;

import com.mongodb.observability.ObservabilitySettings;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Um span por comando do Mongo, só no profile {@code trace} (ADR-0012): o Boot não instrumenta o driver, que tem o
 * próprio suporte a Micrometer. Fora desse profile, o benchmark não paga uma observação por comando.
 */
@Configuration(proxyBeanMethods = false)
@Profile(MongoTracing.PROFILE)
class MongoTracing {

    static final String PROFILE = "trace";

    @Bean
    MongoClientSettingsBuilderCustomizer mongoObservations(ObservationRegistry registry) {
        return settings -> settings.observabilitySettings(
                ObservabilitySettings.micrometerBuilder().observationRegistry(registry).build());
    }
}
