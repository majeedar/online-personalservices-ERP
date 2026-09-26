package edu.university.ops.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    public static final ZonedDateTime NOW = ZonedDateTime.of(2026, 9, 21, 8, 0, 0, 0, ZONE);

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(NOW.toInstant(), ZONE);
    }

    /** A clock tests can move forward (e.g. clock in, then clock out later). Call {@link #reset()} afterwards. */
    public static final class MutableClock extends Clock {

        private volatile Instant instant;
        private final ZoneId zone;

        MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        public void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        public void set(ZonedDateTime time) {
            instant = time.toInstant();
        }

        public void reset() {
            instant = NOW.toInstant();
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
