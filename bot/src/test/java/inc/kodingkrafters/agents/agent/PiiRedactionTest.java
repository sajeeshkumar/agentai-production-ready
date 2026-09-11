package inc.kodingkrafters.agents.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit test for {@link PiiRedaction} — no Spring context, no network. */
class PiiRedactionTest {

    @Test
    void redactsEmailAddresses() {
        assertThat(PiiRedaction.redact("contact me at priya.nair@example.com please"))
                .isEqualTo("contact me at [redacted-email] please");
    }

    @Test
    void redactsUkPostcodes() {
        assertThat(PiiRedaction.redact("my new address is 12 High Street, London, SW1A 1AA, GB"))
                .isEqualTo("my new address is 12 High Street, London, [redacted-postcode], GB");
    }

    @Test
    void redactsLongDigitRuns() {
        assertThat(PiiRedaction.redact("my passport number is 123456789, DOB 01012025"))
                .isEqualTo("my passport number is [redacted-number], DOB [redacted-number]");
    }

    @Test
    void leavesShortDigitRunsAndAccountIdsAlone() {
        assertThat(PiiRedaction.redact("what's the balance on account ACC-1001-001"))
                .isEqualTo("what's the balance on account ACC-1001-001");
    }

    @Test
    void leavesOrdinaryRequestsUnchanged() {
        String request = "Order 50 leaves for the cheque book on my current account";
        assertThat(PiiRedaction.redact(request)).isEqualTo(request);
    }

    @Test
    void handlesNullAndBlank() {
        assertThat(PiiRedaction.redact(null)).isNull();
        assertThat(PiiRedaction.redact("")).isEmpty();
    }
}
