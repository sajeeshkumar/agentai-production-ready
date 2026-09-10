package inc.kodingkrafters.banking;

/**
 * Raised by {@link CoreBankClient} when the Core Banking API returns a non-2xx response. Carries
 * the HTTP status and the service's {@code code} so the tool layer can relay a precise,
 * non-fabricated explanation to the model (and the customer).
 */
public class CoreBankClientException extends RuntimeException {

    private final int status;
    private final String code;

    public CoreBankClientException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
