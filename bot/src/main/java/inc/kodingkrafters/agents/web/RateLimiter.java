package inc.kodingkrafters.agents.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * A simple in-memory, fixed-window rate limiter for {@code /api/chat} — a cap on LLM spend and
 * abuse until real authentication (a later iteration) lets requests be throttled per authenticated
 * customer instead of per caller IP. Each key gets a budget of {@code maxRequests} within a
 * rolling {@code window}; once spent, {@link #tryConsume(String)} returns {@code false} until the
 * window rolls over.
 *
 * <p>Process-local and unbounded, like {@link inc.kodingkrafters.agents.agent.CoordinatorAgent}'s
 * chat memory — fine for one instance behind modest traffic, not for a fleet or a public endpoint
 * under sustained attack (the key set only grows). A shared, bounded store (e.g. Redis) is the
 * natural upgrade once this runs behind a load balancer.
 */
@Component
public class RateLimiter {

    private final int maxRequests;
    private final long windowMillis;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public RateLimiter(
            @Value("${chat.rate-limit.max-requests:20}") int maxRequests,
            @Value("${chat.rate-limit.window:1m}") Duration window) {
        this(maxRequests, window, System::currentTimeMillis);
    }

    /** Visible for testing: a controllable clock avoids real sleeps in the window-rollover test. */
    RateLimiter(int maxRequests, Duration window, LongSupplier clock) {
        this.maxRequests = maxRequests;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /** @return {@code true} if {@code key} still has budget in the current window. */
    public boolean tryConsume(String key) {
        return windows.computeIfAbsent(key, k -> new Window(clock.getAsLong())).tryConsume(clock.getAsLong());
    }

    private final class Window {
        private long startMillis;
        private int count;

        Window(long startMillis) {
            this.startMillis = startMillis;
        }

        synchronized boolean tryConsume(long now) {
            if (now - startMillis >= windowMillis) {
                startMillis = now;
                count = 0;
            }
            if (count >= maxRequests) {
                return false;
            }
            count++;
            return true;
        }
    }
}
