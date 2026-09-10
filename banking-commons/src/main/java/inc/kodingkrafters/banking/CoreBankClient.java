package inc.kodingkrafters.banking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * HTTP client for the SecureBank Core Banking API. One method per endpoint; every call sends the
 * signed-in customer in {@code X-Customer-Id} and maps a non-2xx response onto a
 * {@link CoreBankClientException} carrying the service's own error {@code code} and message
 * (decoded with the {@link RestClient}'s own converters).
 *
 * <p>This is the boundary an MCP server's tools sit behind: they never build URLs or parse HTTP
 * — they call these methods and hand the result (or the translated error) back to the model.
 * Not a Spring component; each MCP server declares its own {@code @Bean} via {@link #create}.
 */
public class CoreBankClient {

    private static final Logger log = LoggerFactory.getLogger(CoreBankClient.class);
    private static final String CUSTOMER_HEADER = "X-Customer-Id";

    private final RestClient http;

    public CoreBankClient(RestClient coreBankRestClient) {
        this.http = coreBankRestClient;
    }

    /**
     * Builds a client pointed at {@code baseUrl} with the given connect/read timeouts.
     */
    public static CoreBankClient create(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return new CoreBankClient(RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build());
    }

    public CoreBankApi.BalanceResponse balance(String customerId, String accountId) {
        return call(() -> http.get()
                .uri("/corebank/v1/accounts/{accountId}/balance", accountId)
                .header(CUSTOMER_HEADER, customerId)
                .retrieve()
                .body(CoreBankApi.BalanceResponse.class));
    }

    public CoreBankApi.TransactionsResponse transactions(String customerId, String accountId,
                                                         LocalDate from, LocalDate to, Integer limit) {
        return call(() -> http.get()
                .uri(uri -> {
                    uri.path("/corebank/v1/accounts/{accountId}/transactions");
                    if (from != null) {
                        uri.queryParam("from", from);
                    }
                    if (to != null) {
                        uri.queryParam("to", to);
                    }
                    if (limit != null) {
                        uri.queryParam("limit", limit);
                    }
                    return uri.build(accountId);
                })
                .header(CUSTOMER_HEADER, customerId)
                .retrieve()
                .body(CoreBankApi.TransactionsResponse.class));
    }

    public CoreBankApi.StatementResponse requestStatement(String customerId, String accountId,
                                                          CoreBankApi.StatementRequest request) {
        return call(() -> http.post()
                .uri("/corebank/v1/accounts/{accountId}/statements", accountId)
                .header(CUSTOMER_HEADER, customerId)
                .body(request)
                .retrieve()
                .body(CoreBankApi.StatementResponse.class));
    }

    public CoreBankApi.ChequeBookResponse requestChequeBook(String customerId, String accountId,
                                                            CoreBankApi.ChequeBookRequest request) {
        return call(() -> http.post()
                .uri("/corebank/v1/accounts/{accountId}/cheque-books", accountId)
                .header(CUSTOMER_HEADER, customerId)
                .body(request)
                .retrieve()
                .body(CoreBankApi.ChequeBookResponse.class));
    }

    public CoreBankApi.AddressUpdateResponse changeAddress(String customerId,
                                                           CoreBankApi.AddressUpdateRequest request) {
        return call(() -> http.post()
                .uri("/corebank/v1/customers/{customerId}/address", customerId)
                .header(CUSTOMER_HEADER, customerId)
                .body(request)
                .retrieve()
                .body(CoreBankApi.AddressUpdateResponse.class));
    }

    public CoreBankApi.KycUpdateResponse updateKyc(String customerId, CoreBankApi.KycUpdateRequest request) {
        return call(() -> http.post()
                .uri("/corebank/v1/customers/{customerId}/kyc", customerId)
                .header(CUSTOMER_HEADER, customerId)
                .body(request)
                .retrieve()
                .body(CoreBankApi.KycUpdateResponse.class));
    }

    public CoreBankApi.CreditLimitIncreaseResponse increaseCreditLimit(String customerId, String accountId,
                                                                       CoreBankApi.CreditLimitIncreaseRequest request) {
        return call(() -> http.post()
                .uri("/corebank/v1/accounts/{accountId}/credit-limit", accountId)
                .header(CUSTOMER_HEADER, customerId)
                .body(request)
                .retrieve()
                .body(CoreBankApi.CreditLimitIncreaseResponse.class));
    }

    public CoreBankApi.EntitlementsResponse entitlements(String customerId) {
        return call(() -> http.get()
                .uri("/corebank/v1/customers/{customerId}/entitlements", customerId)
                .header(CUSTOMER_HEADER, customerId)
                .retrieve()
                .body(CoreBankApi.EntitlementsResponse.class));
    }

    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            CoreBankApi.ApiError error = decodeError(ex);
            if (error != null && error.code() != null) {
                log.warn("Core Banking API -> {} {} : {}", status, error.code(), error.message());
                throw new CoreBankClientException(status, error.code(), error.message());
            }
            log.warn("Core Banking API -> {} (no parseable error body)", status);
            throw new CoreBankClientException(status, "CORE_BANK_ERROR",
                    "The Core Banking API returned HTTP " + status + ".");
        }
    }

    private CoreBankApi.ApiError decodeError(RestClientResponseException ex) {
        try {
            return ex.getResponseBodyAs(CoreBankApi.ApiError.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
