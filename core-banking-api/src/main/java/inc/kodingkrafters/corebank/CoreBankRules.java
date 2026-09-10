package inc.kodingkrafters.corebank;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Shared, deterministic validation helpers for the Core Banking API. Keeping the rules here
 * (not in the bot's prompt) is the point: the model only decides <em>which</em> call to make;
 * whether it is allowed is settled in Java.
 */
final class CoreBankRules {

    /** Loose UK postcode shape, case-insensitive, optional single space. */
    static final Pattern UK_POSTCODE = Pattern.compile("(?i)^[A-Z]{1,2}\\d[A-Z\\d]?\\s?\\d[A-Z]{2}$");

    /** ISO 3166-1 alpha-2 country code. */
    static final Pattern ISO_COUNTRY = Pattern.compile("^[A-Za-z]{2}$");

    private CoreBankRules() {
    }

    static String reference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new CoreBankException(422, "MISSING_FIELD", "Field '" + field + "' is required.");
        }
        return value.trim();
    }
}
