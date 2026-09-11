package inc.kodingkrafters.agents.agent;

import java.util.regex.Pattern;

/**
 * Best-effort redaction for free-text customer requests before they reach a logger.
 *
 * <p>Each specialist agent logs the plain-language request the coordinator delegated to it (see
 * {@code AccountsAgent}, {@code TransactionAgent}, {@code ServiceAgent}). For {@code ServiceAgent}
 * in particular that text can legitimately carry PII the customer typed or the coordinator read
 * back for confirmation — an address, a KYC document number, a date of birth. {@link AgentLogging}
 * only ever logs token counts; this is the one place free text reaches a log line, so it is the
 * one place PII needs to be stripped first.
 *
 * <p>This is defense in depth, not a compliance-grade DLP filter: it catches high-confidence
 * shapes (email addresses, UK postcodes, and runs of 6+ digits — phone numbers, numeric document
 * numbers, dates of birth written as digits). It will not catch prose like a spelled-out street
 * address or an alphanumeric document number. Free-text request logging should still be treated
 * as sensitive wherever it flows: don't raise these loggers above INFO and don't wire an
 * unredacted sink (e.g. a log-shipping agent) to them without the same care.
 */
public final class PiiRedaction {

    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[\\w.+-]+@[\\w-]+\\.[\\w.-]+\\b");
    private static final Pattern UK_POSTCODE = Pattern.compile("(?i)\\b[A-Z]{1,2}\\d[A-Z\\d]?\\s?\\d[A-Z]{2}\\b");
    private static final Pattern LONG_DIGIT_RUN = Pattern.compile("\\b\\d{6,}\\b");

    private PiiRedaction() {
    }

    /** Replaces emails, UK postcodes, and 6+ digit runs in {@code text} with redaction markers. */
    public static String redact(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String redacted = EMAIL.matcher(text).replaceAll("[redacted-email]");
        redacted = UK_POSTCODE.matcher(redacted).replaceAll("[redacted-postcode]");
        redacted = LONG_DIGIT_RUN.matcher(redacted).replaceAll("[redacted-number]");
        return redacted;
    }
}
