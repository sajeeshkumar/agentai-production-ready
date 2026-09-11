package inc.kodingkrafters.agents.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * Session-based login for the bot. This is what makes {@code customerId} trustworthy: every
 * request to {@code /api/**} must carry an authenticated session, and {@code ChatController}
 * reads {@code customerId} off that session's principal — never off anything the caller sent.
 *
 * <p>The login itself is the demo-only {@link DemoCustomerUserDetailsService}; swapping it for a
 * real identity provider is the only change a later iteration needs here — everything downstream
 * (the coordinator, the specialists, the MCP servers, the Core Banking API) already trusts
 * {@code customerId} exactly this way and needs no change.
 *
 * <p>CSRF stays on (the {@code /api/chat} and {@code /logout} calls are session-cookie-based, so
 * they need it): the token is issued as a readable {@code XSRF-TOKEN} cookie so the static chat
 * page's JavaScript can echo it back as {@code X-XSRF-TOKEN} — the standard pattern for a
 * same-origin single-page app with no server-side templating. {@link SpaCsrfTokenRequestHandler}
 * is required alongside the cookie repository: Spring Security's default handler BREACH-protects
 * (XOR-masks) the token it hands to a rendered form, so the raw value a page reads back out of
 * the cookie would never match unless header-submitted tokens are explicitly exempted from that
 * masking — this is Spring's own documented fix for exactly this combination.
 *
 * <p>{@code /api/**} gets a plain {@code 401} instead of Spring's default login-page redirect
 * when unauthenticated, since the caller there is {@code fetch()}, not a browser navigation.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults())
                .logout(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.pathPattern("/api/**")));
        return http.build();
    }

    /**
     * Resolves a header-submitted CSRF token (our fetch() calls) against the raw, unmasked value
     * — the one the {@code XSRF-TOKEN} cookie actually holds — while still requiring the normal
     * BREACH-protected (XOR-masked) value for anything submitted as a form/request parameter
     * (Spring's own generated {@code /login} page included). Copied from Spring Security's
     * reference docs on CSRF for single-page applications.
     */
    private static final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

        private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            this.delegate.handle(request, response, csrfToken);
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            String headerValue = request.getHeader(csrfToken.getHeaderName());
            return StringUtils.hasText(headerValue)
                    ? super.resolveCsrfTokenValue(request, csrfToken)
                    : this.delegate.resolveCsrfTokenValue(request, csrfToken);
        }
    }
}
