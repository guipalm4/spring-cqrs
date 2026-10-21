package io.github.guipalm4.ledger.cqrs;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;

@TestConfiguration(proxyBeanMethods = false)
class KafkaTestcontainer {

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer(System.getProperty("kafka.image", "apache/kafka:4.2.2"));
    }
}
