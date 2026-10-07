package com.appcontrol.panel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;

/** Reproduces startup when application.yml is absent from an IDE's classpath. */
class ProjectConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(Config.class);

    @Configuration(proxyBeanMethods=false)
    @Import(DatabaseService.class)
    static class Config {
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
        @Bean static PropertySourcesPlaceholderConfigurer placeholders() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }

    @Test void startsWithoutApplicationYamlOrProjectProperty() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("panel.firebase.project-id")).isNull();
            assertThat(ReflectionTestUtils.getField(context.getBean(DatabaseService.class),"projectId"))
                .isEqualTo("insta-11358");
        });
    }
    @Test void acceptsEnvironmentStyleProjectIdWithoutYamlMapping() {
        runner.withPropertyValues("FIREBASE_PROJECT_ID=test-project").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(ReflectionTestUtils.getField(context.getBean(DatabaseService.class),"projectId"))
                .isEqualTo("test-project");
        });
    }
    @Test void explicitSpringPropertyTakesPrecedence() {
        runner.withPropertyValues("panel.firebase.project-id=explicit-project", "FIREBASE_PROJECT_ID=other-project")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(ReflectionTestUtils.getField(context.getBean(DatabaseService.class),"projectId"))
                    .isEqualTo("explicit-project");
            });
    }
}
