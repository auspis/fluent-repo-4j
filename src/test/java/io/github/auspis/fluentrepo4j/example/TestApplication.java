package io.github.auspis.fluentrepo4j.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.github.auspis.fluentrepo4j.config.EnableFluentRepositories;

/**
 * Spring Boot application for integration testing fluent-repo4j.
 *
 * Spring Boot auto-configuration automatically:
 * - Loads FluentRepositoriesAutoConfiguration (via spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports)
 * - Enables @EnableFluentRepositories annotation
 * - Creates FluentConnectionProvider bean (when DataSource is available)
 * - Auto-detects database dialect via DialectDetector
 * - Scans and registers repository beans via FluentRepositoriesRegistrar
 */
@SpringBootApplication
@EnableFluentRepositories(basePackages = "io.github.auspis.fluentrepo4j.example")
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
