package com.example.thumbnailer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The load generator is reachable by anyone once this application is published, so what it does
 * when nobody asked for it matters as much as what it does when they did.
 */
class LoadControllerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(InstanceInfo.class, () -> new InstanceInfo("test", false))
            .withUserConfiguration(LoadController.class);

    @Test
    void isNotRegisteredByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(LoadController.class));
    }

    @Test
    void isRegisteredOnlyWhenAskedFor() {
        contextRunner.withPropertyValues("thumbnailer.load-endpoint.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(LoadController.class));
    }

    @Test
    void staysOffForAnythingOtherThanTrue() {
        contextRunner.withPropertyValues("thumbnailer.load-endpoint.enabled=yes")
                .run(context -> assertThat(context).doesNotHaveBean(LoadController.class));
    }
}
