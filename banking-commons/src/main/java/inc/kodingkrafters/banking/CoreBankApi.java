package inc.kodingkrafters.banking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The wire contract for the <b>SecureBank Core Banking API</b> — the downstream service the
 * agent's tools call over HTTP.
 *
 * <p>In production this service is a separate deployment reached through an API gateway. Here
 * it is co-hosted with the bot for convenience, but the tool path is still HTTP in → HTTP out
 * (a specialist's {@code …Tools} → {@code CoreBankClient} → {@code RestClient} → these
 * controllers), so the integration behaves exactly as it would against a real core-banking
 * platform: timeouts, status codes, error bodies, and authorization on every call.
 *
 * <p>Every request carries the signed-in customer in an {@code X-Customer-Id} header; the
 * service authorizes each account against it. The bot never lets the model pick the customer.
 */
public final class CoreBankApi {

    private CoreBankApi() {
    }

    /** Response of {@code GET /corebank/v1/accounts/{accountId}/balance}. */
    public record BalanceResponse(
            String accountId,
            String accountName,
            String accountType,
            String currency,
            BigDecimal currentBalance,
            BigDecimal availableBalance,
            BigDecimal creditLimit,
            String status,
            Instant asOf) {
    }

    /** One posted entry on an account. */
    public record TransactionRecord(
            String transactionId,
            LocalDate date,
            String description,
            String direction,
            BigDecimal amount,
            String currency) {
    }

    /** Response of {@code GET /corebank/v1/accounts/{accountId}/transactions}. */
    public record TransactionsResponse(
            String accountId,
            LocalDate fromDate,
            LocalDate toDate,
            int count,
            List<TransactionRecord> transactions) {
    }

    /** Body of {@code POST /corebank/v1/accounts/{accountId}/statements}. */
    public record StatementRequest(LocalDate fromDate, LocalDate toDate, String format) {
    }

    /** Response of a statement request (HTTP 202). */
    public record StatementResponse(
            String reference,
            String accountId,
            LocalDate fromDate,
            LocalDate toDate,
            String format,
            String deliveryChannel,
            String deliveryTarget,
            LocalDate estimatedDelivery) {
    }

    /** Body of {@code POST /corebank/v1/accounts/{accountId}/cheque-books}. */
    public record ChequeBookRequest(Integer leaves) {
    }

    /** Response of a cheque-book request (HTTP 202). */
    public record ChequeBookResponse(
            String reference,
            String accountId,
            int leaves,
            String deliveryAddress,
            LocalDate estimatedDelivery) {
    }

    /** Body of {@code POST /corebank/v1/customers/{customerId}/address}. */
    public record AddressUpdateRequest(
            String line1,
            String line2,
            String city,
            String postcode,
            String country,
            LocalDate effectiveDate) {
    }

    /** Response of a change-of-address request. */
    public record AddressUpdateResponse(
            String reference,
            String customerId,
            String status,
            String formattedAddress,
            LocalDate effectiveDate) {
    }

    /** Body of {@code POST /corebank/v1/customers/{customerId}/kyc}. */
    public record KycUpdateRequest(
            String documentType,
            String documentNumber,
            String issuingCountry,
            LocalDate expiryDate) {
    }

    /** Response of a KYC update (HTTP 202). */
    public record KycUpdateResponse(
            String reference,
            String customerId,
            String documentType,
            String status,
            LocalDate reviewBy) {
    }

    /** Body of {@code POST /corebank/v1/accounts/{accountId}/credit-limit}. Privileged customers only. */
    public record CreditLimitIncreaseRequest(BigDecimal newLimit) {
    }

    /** Response of a credit-limit increase (applied immediately). */
    public record CreditLimitIncreaseResponse(
            String reference,
            String accountId,
            BigDecimal previousLimit,
            BigDecimal newLimit,
            LocalDate effectiveDate) {
    }

    /** Response of {@code GET /corebank/v1/customers/{customerId}/entitlements}. */
    public record EntitlementsResponse(
            String customerId,
            String tier,
            List<String> permittedCapabilities) {
    }

    /** Error body returned for every non-2xx response. */
    public record ApiError(int status, String code, String message) {
    }
}
