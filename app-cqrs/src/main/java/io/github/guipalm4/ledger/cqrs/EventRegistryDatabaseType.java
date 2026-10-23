package io.github.guipalm4.ledger.cqrs;

import java.util.Arrays;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fixa em PostgreSQL o tipo de banco do registro de eventos do Modulith, como o {@code spring.data.jdbc.dialect}
 * no application.yaml. Sem isso, o Modulith abre uma conexão na partida para descobrir o banco, e o treino do AOT
 * cache no Dockerfile, que roda sem banco, falha.
 * <p>
 * Não há propriedade para isso, e o tipo ({@code DatabaseType}) é package-private, daí o nome do bean e a
 * reflexão. Se uma versão nova do Modulith mudar um dos dois, a partida falha aqui, e os testes pegam.
 */
@Configuration(proxyBeanMethods = false)
class EventRegistryDatabaseType {

    private static final String BEAN = "databaseType";
    private static final String TYPE = "org.springframework.modulith.events.jdbc.DatabaseType";

    @Bean
    static BeanFactoryPostProcessor fixedEventRegistryDatabaseType() {
        return beanFactory -> {
            var definition = (AbstractBeanDefinition) beanFactory.getBeanDefinition(BEAN);
            var postgres = postgres();
            definition.setInstanceSupplier(() -> postgres);
        };
    }

    private static Object postgres() {
        try {
            return Arrays.stream(Class.forName(TYPE).getEnumConstants())
                    .filter(constant -> ((Enum<?>) constant).name().equals("POSTGRES"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(TYPE + " has no POSTGRES constant"));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
