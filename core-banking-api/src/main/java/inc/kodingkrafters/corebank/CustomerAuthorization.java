package inc.kodingkrafters.corebank;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The authorization step. Every capability-bearing endpoint calls {@link #require} before doing
 * any work: it resolves the signed-in customer's {@link Tier} and checks it against the policy.
 *
 * <p>The policy is the single source of truth for "who can do what":
 * <ul>
 *   <li>{@link Capability#CHEQUE_BOOK_REQUEST} — {@code PREMIUM} and {@code PRIVILEGED} (there is a fee).</li>
 *   <li>{@link Capability#INCREASE_CREDIT_LIMIT} — {@code PRIVILEGED} only.</li>
 *   <li>everything else — all tiers.</li>
 * </ul>
 */
@Component
public class CustomerAuthorization {

    private static final Map<Capability, Set<Tier>> POLICY = Map.of(
            Capability.CHEQUE_BOOK_REQUEST, EnumSet.of(Tier.PREMIUM, Tier.PRIVILEGED),
            Capability.INCREASE_CREDIT_LIMIT, EnumSet.of(Tier.PRIVILEGED));

    private final CoreBankData data;

    CustomerAuthorization(CoreBankData data) {
        this.data = data;
    }

    /** The signed-in customer's tier, or 401 / 404 if the id is blank / unknown. */
    public Tier tierOf(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new CoreBankException(401, "UNAUTHENTICATED", "Missing X-Customer-Id.");
        }
        return data.findCustomer(customerId)
                .map(CoreBankData.Customer::tier)
                .orElseThrow(() -> new CoreBankException(404, "CUSTOMER_NOT_FOUND", "No customer " + customerId + "."));
    }

    /** Throws {@code 403 CAPABILITY_NOT_PERMITTED} if the customer's tier is not allowed this capability. */
    public void require(String customerId, Capability capability) {
        Tier tier = tierOf(customerId);
        Set<Tier> allowed = POLICY.get(capability);
        if (allowed != null && !allowed.contains(tier)) {
            String allowedList = allowed.stream()
                    .map(t -> t.name().toLowerCase())
                    .sorted()
                    .collect(Collectors.joining(" and "));
            throw new CoreBankException(403, "CAPABILITY_NOT_PERMITTED",
                    "The '" + label(capability) + "' capability is available to " + allowedList
                            + " customers only; this customer is on the " + tier.name().toLowerCase() + " tier.");
        }
    }

    /** The capabilities the customer's tier permits — for the entitlements endpoint. */
    public List<Capability> permitted(String customerId) {
        Tier tier = tierOf(customerId);
        return Arrays.stream(Capability.values())
                .filter(c -> {
                    Set<Tier> allowed = POLICY.get(c);
                    return allowed == null || allowed.contains(tier);
                })
                .toList();
    }

    private static String label(Capability capability) {
        return capability.name().toLowerCase().replace('_', ' ');
    }
}
