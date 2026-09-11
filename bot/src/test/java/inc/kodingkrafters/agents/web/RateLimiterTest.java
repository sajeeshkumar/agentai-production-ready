package inc.kodingkrafters.agents.web;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit test for {@link RateLimiter} — a fake clock, so no real sleeping. */
class RateLimiterTest {

    @Test
    void allowsUpToTheLimitWithinTheWindow() {
        AtomicLong now = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(2, Duration.ofSeconds(10), now::get);

        assertThat(limiter.tryConsume("k1")).isTrue();
        assertThat(limiter.tryConsume("k1")).isTrue();
        assertThat(limiter.tryConsume("k1")).isFalse();
    }

    @Test
    void resetsOnceTheWindowRollsOver() {
        AtomicLong now = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(1, Duration.ofSeconds(10), now::get);

        assertThat(limiter.tryConsume("k1")).isTrue();
        assertThat(limiter.tryConsume("k1")).isFalse();

        now.addAndGet(Duration.ofSeconds(10).toMillis());

        assertThat(limiter.tryConsume("k1")).isTrue();
    }

    @Test
    void tracksEachKeyIndependently() {
        AtomicLong now = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(1, Duration.ofSeconds(10), now::get);

        assertThat(limiter.tryConsume("a")).isTrue();
        assertThat(limiter.tryConsume("b")).isTrue();
        assertThat(limiter.tryConsume("a")).isFalse();
        assertThat(limiter.tryConsume("b")).isFalse();
    }
}
