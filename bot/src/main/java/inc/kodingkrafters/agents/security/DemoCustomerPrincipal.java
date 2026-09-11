package inc.kodingkrafters.agents.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * The authenticated principal for a signed-in demo customer: the username (their email) plus the
 * one thing the rest of the bot actually needs, {@code customerId}. This is what closes the
 * impersonation hole — {@link inc.kodingkrafters.agents.web.ChatController} reads {@code
 * customerId} from this authenticated principal, never from the request body, so no caller can
 * act for a customer they didn't log in as.
 */
public final class DemoCustomerPrincipal implements UserDetails {

    private final String customerId;
    private final String username;
    private final String password;

    public DemoCustomerPrincipal(String customerId, String username, String password) {
        this.customerId = customerId;
        this.username = username;
        this.password = password;
    }

    public String customerId() {
        return customerId;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AuthorityUtils.createAuthorityList("ROLE_CUSTOMER");
    }
}
