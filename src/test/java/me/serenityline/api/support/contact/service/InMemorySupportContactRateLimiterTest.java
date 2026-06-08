package me.serenityline.api.support.contact.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemorySupportContactRateLimiterTest {

    private MutableClock clock;
    private InMemorySupportContactRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-06-08T10:00:00Z"));
        rateLimiter = new InMemorySupportContactRateLimiter(clock);
    }

    @Test
    void shouldAllowFiveRequestsInWindow() {
        for (int index = 1; index <= 5; index++) {
            assertThatCode(() -> rateLimiter.check("ip:203.0.113.1"))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void shouldRejectSixthRequestInSameWindow() {
        for (int index = 1; index <= 5; index++) {
            rateLimiter.check("ip:203.0.113.1");
        }

        assertThatThrownBy(() -> rateLimiter.check("ip:203.0.113.1"))
                .isInstanceOf(TooManySupportContactRequestsException.class)
                .hasMessage("support.contact.tooManyRequests");
    }

    @Test
    void shouldResetCounterAfterWindowExpiresFromWindowStart() {
        for (int index = 1; index <= 5; index++) {
            rateLimiter.check("ip:203.0.113.1");
        }

        clock.advanceSeconds(601);

        assertThatCode(() -> rateLimiter.check("ip:203.0.113.1"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldTrackDifferentKeysIndependently() {
        for (int index = 1; index <= 5; index++) {
            rateLimiter.check("ip:203.0.113.1");
        }

        assertThatCode(() -> rateLimiter.check("ip:203.0.113.2"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldIgnoreNullBlankAndWhitespaceKeys() {
        assertThatCode(() -> {
            rateLimiter.check(null);
            rateLimiter.check("");
            rateLimiter.check("   ");
        }).doesNotThrowAnyException();
    }

    @Test
    void shouldNormalizeKeyByTrimmingWhitespace() {
        for (int index = 1; index <= 5; index++) {
            rateLimiter.check("ip:203.0.113.1");
        }

        assertThatThrownBy(() -> rateLimiter.check("  ip:203.0.113.1  "))
                .isInstanceOf(TooManySupportContactRequestsException.class);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceSeconds(long seconds) {
            this.instant = this.instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}