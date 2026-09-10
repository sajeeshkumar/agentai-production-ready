package inc.kodingkrafters.mcp.service;

import inc.kodingkrafters.banking.CoreBankApi;
import inc.kodingkrafters.banking.CoreBankClient;
import inc.kodingkrafters.banking.CoreBankClientException;
import inc.kodingkrafters.banking.ToolSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * The Service MCP server's tools: change of address, cheque-book requests, KYC updates, and
 * credit-limit increases. Exposed over SSE; each call reaches the Core Banking API over HTTP
 * through {@link CoreBankClient}.
 *
 * <p>{@code customerId} is a tool argument the coordinator's MCP client overwrites with the
 * authenticated customer before every call. Tier gating (cheque book = Premium/Privileged;
 * credit-limit increase = Privileged) is enforced by the Core Banking API, which returns a
 * {@code CAPABILITY_NOT_PERMITTED} error the calling agent relays.
 */
@Component
public class ServiceTools {

    private static final Logger log = LoggerFactory.getLogger(ServiceTools.class);

    private final CoreBankClient coreBank;

    public ServiceTools(CoreBankClient coreBank) {
        this.coreBank = coreBank;
    }

    @Tool(name = "change_of_address", description = """
            Update the signed-in customer's registered postal address. Read the full address back to
            the customer and get their confirmation before calling. UK postcode and 2-letter ISO
            country code required.""")
    public Object changeOfAddress(
            @ToolParam(description = "Signed-in customer id; injected by the coordinator, do not populate.")
            String customerId,
            @ToolParam(description = "First address line, e.g. house number and street.") String line1,
            @ToolParam(required = false, description = "Second address line. Optional.") String line2,
            @ToolParam(description = "Town or city.") String city,
            @ToolParam(description = "UK postcode.") String postcode,
            @ToolParam(description = "2-letter ISO country code, e.g. GB.") String country,
            @ToolParam(required = false, description = "Effective date, ISO yyyy-MM-dd. Defaults to today; "
                    + "cannot be in the past.") String effectiveDate) {
        log.info("tool=change_of_address customer={} city={} country={}", customerId, city, country);
        LocalDate parsedEffective;
        try {
            parsedEffective = ToolSupport.parseDate(effectiveDate);
        } catch (DateTimeParseException ex) {
            return ToolSupport.invalidDate("effectiveDate must be ISO yyyy-MM-dd.");
        }
        try {
            return coreBank.changeAddress(customerId, new CoreBankApi.AddressUpdateRequest(
                    line1, line2, city, postcode, country, parsedEffective));
        } catch (CoreBankClientException ex) {
            return ToolSupport.error(ex);
        }
    }

    @Tool(name = "cheque_book_request", description = """
            Order a cheque book for one of the signed-in customer's current accounts, delivered to
            the address on file. Only current accounts are eligible, and only Premium and
            Privileged customers (there is a fee). Confirm with the customer before calling.""")
    public Object chequeBookRequest(
            @ToolParam(description = "Signed-in customer id; injected by the coordinator, do not populate.")
            String customerId,
            @ToolParam(description = "Account id such as ACC-1001-001.") String accountId,
            @ToolParam(required = false, description = "Number of leaves: 25, 50 or 100. Defaults to 25.")
            Integer leaves) {
        log.info("tool=cheque_book_request customer={} account={} leaves={}", customerId, accountId, leaves);
        try {
            return coreBank.requestChequeBook(customerId, accountId,
                    new CoreBankApi.ChequeBookRequest(leaves));
        } catch (CoreBankClientException ex) {
            return ToolSupport.error(ex);
        }
    }

    @Tool(name = "kyc_update", description = """
            Submit a KYC identity-document update for the signed-in customer. The document goes for
            review; it is not applied immediately. Confirm the details with the customer before
            calling. Accepted types: PASSPORT, DRIVING_LICENCE, NATIONAL_ID.""")
    public Object kycUpdate(
            @ToolParam(description = "Signed-in customer id; injected by the coordinator, do not populate.")
            String customerId,
            @ToolParam(description = "PASSPORT, DRIVING_LICENCE or NATIONAL_ID.") String documentType,
            @ToolParam(description = "The document number.") String documentNumber,
            @ToolParam(description = "2-letter ISO code of the issuing country, e.g. GB.") String issuingCountry,
            @ToolParam(description = "Document expiry date, ISO yyyy-MM-dd. Must be in the future.")
            String expiryDate) {
        log.info("tool=kyc_update customer={} documentType={} issuingCountry={}",
                customerId, documentType, issuingCountry);
        LocalDate parsedExpiry;
        try {
            parsedExpiry = ToolSupport.parseDate(expiryDate);
        } catch (DateTimeParseException ex) {
            return ToolSupport.invalidDate("expiryDate must be ISO yyyy-MM-dd.");
        }
        try {
            return coreBank.updateKyc(customerId, new CoreBankApi.KycUpdateRequest(
                    documentType, documentNumber, issuingCountry, parsedExpiry));
        } catch (CoreBankClientException ex) {
            return ToolSupport.error(ex);
        }
    }

    @Tool(name = "increase_credit_limit", description = """
            Increase the credit limit on one of the signed-in customer's active current accounts.
            Privileged customers only; the new limit must be higher than the current one and
            within the bank's cap. Read the requested limit back and confirm with the customer
            before calling.""")
    public Object increaseCreditLimit(
            @ToolParam(description = "Signed-in customer id; injected by the coordinator, do not populate.")
            String customerId,
            @ToolParam(description = "Account id such as ACC-1001-001.") String accountId,
            @ToolParam(description = "The requested new credit limit, e.g. 3000.") BigDecimal newLimit) {
        log.info("tool=increase_credit_limit customer={} account={} newLimit={}", customerId, accountId, newLimit);
        try {
            return coreBank.increaseCreditLimit(customerId, accountId,
                    new CoreBankApi.CreditLimitIncreaseRequest(newLimit));
        } catch (CoreBankClientException ex) {
            return ToolSupport.error(ex);
        }
    }
}
