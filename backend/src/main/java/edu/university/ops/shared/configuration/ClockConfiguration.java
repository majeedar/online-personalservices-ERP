package edu.university.ops.shared.configuration;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A single injectable clock in the university timezone. Code must use
 * {@code LocalDate.now(clock)} / {@code Instant.now(clock)} so tests can fix time.
 */
@Configuration
public class ClockConfiguration {

    @Bean
    Clock clock(OpsProperties properties) {
        return Clock.system(properties.timezone());
    }
}
