package com.example.trainingsystems.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Verify exact Connector/J property forwarding without opening a connection. */
class TailscaleDataSourceConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
            .withPropertyValues("spring.datasource.url=jdbc:mysql://127.0.0.1:3306/unused",
                    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver");

    @Test
    void dockerStartupPropertiesReachConnectorJ() {
        context.withPropertyValues(
                "spring.datasource.hikari.data-source-properties.socksProxyHost=127.0.0.1",
                "spring.datasource.hikari.data-source-properties.socksProxyPort=1055",
                "spring.datasource.hikari.data-source-properties.connectTimeout=10000",
                "spring.datasource.hikari.data-source-properties.socketTimeout=30000")
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    HikariDataSource dataSource = application.getBean(HikariDataSource.class);
                    assertThat(dataSource.getDataSourceProperties())
                            .containsEntry("socksProxyHost", "127.0.0.1")
                            .containsEntry("socksProxyPort", "1055")
                            .containsEntry("connectTimeout", "10000")
                            .containsEntry("socketTimeout", "30000");
                });
    }

    @Test
    void nonDockerLocalConfigurationDoesNotUseSocks() {
        context.run(application -> {
            assertThat(application).hasNotFailed();
            assertThat(application.getBean(HikariDataSource.class).getDataSourceProperties())
                    .doesNotContainKeys("socksProxyHost", "socksProxyPort");
        });
    }
}
