package io.github.guipalm4.ledger.baseline;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Controle da #11 (ADR-0010): a v1 com um pool para comandos e outro para consultas, mesmo total de conexões.
 * Desligado por padrão; sem o profile {@value #PROFILE}, a v1 é a baseline do ADR-0009, com um pool compartilhado.
 * <p>
 * O DataSource autoconfigurado vira o pool dos comandos ({@code spring.datasource.hikari}): transações, repositórios
 * do Spring Data JDBC e Flyway continuam nele, sem mudança. O pool das consultas é um segundo DataSource fora da
 * autoconfiguração ({@code defaultCandidate = false}), e o {@link JdbcClient} passa a usá-lo: na v1, só as
 * consultas Q1–Q4 usam {@code JdbcClient}; os comandos usam os repositórios.
 */
@Configuration(proxyBeanMethods = false)
@Profile(SplitPoolsConfiguration.PROFILE)
class SplitPoolsConfiguration {

    static final String PROFILE = "split-pools";

    @Bean(defaultCandidate = false)
    @Qualifier("queries")
    @ConfigurationProperties("ledger.split-pools.queries")
    HikariDataSource queryDataSource(JdbcConnectionDetails connection) {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .driverClassName(connection.getDriverClassName())
                .url(connection.getJdbcUrl())
                .username(connection.getUsername())
                .password(connection.getPassword())
                .build();
    }

    @Bean
    JdbcClient queryJdbcClient(@Qualifier("queries") DataSource queries) {
        return JdbcClient.create(queries);
    }
}
