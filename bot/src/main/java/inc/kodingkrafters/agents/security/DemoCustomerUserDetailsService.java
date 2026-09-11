package inc.kodingkrafters.agents.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * An in-memory login for the three seeded demo customers (see {@code README.md}'s "Demo data"
 * table) — a stand-in until a real identity provider is wired up. Every demo account shares the
 * same demo-only password; there is nothing behind this that resembles production credential
 * storage, and it must not be mistaken for one.
 *
 * <p>This is what makes {@code customerId} trustworthy for the rest of the bot: a customer logs
 * in as themselves here, and {@link DemoCustomerPrincipal#customerId()} is the only place {@code
 * customerId} comes from from this point on.
 */
@Service
class DemoCustomerUserDetailsService implements UserDetailsService {

    /** DEMO ONLY — never a real credential. Shared across all three seeded demo accounts. */
    static final String DEMO_PASSWORD = "SecureBank-Demo1";

    private final Map<String, DemoCustomerPrincipal> byUsername;

    DemoCustomerUserDetailsService(PasswordEncoder passwordEncoder) {
        String encoded = passwordEncoder.encode(DEMO_PASSWORD);
        byUsername = Map.of(
                "priya.nair@example.com", new DemoCustomerPrincipal("CUST-1001", "priya.nair@example.com", encoded),
                "tom.baker@example.com", new DemoCustomerPrincipal("CUST-1002", "tom.baker@example.com", encoded),
                "dan.shaw@example.com", new DemoCustomerPrincipal("CUST-1003", "dan.shaw@example.com", encoded));
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        DemoCustomerPrincipal principal = byUsername.get(username == null ? null : username.trim().toLowerCase());
        if (principal == null) {
            throw new UsernameNotFoundException("No demo customer '" + username + "'.");
        }
        return principal;
    }
}
