package inc.kodingkrafters.corebank;

/**
 * Thrown inside the Core Banking API when a request is rejected. Carries the HTTP status and a
 * stable machine-readable {@code code} that {@link CoreBankApiExceptionHandler} renders into a
 * {@link CoreBankApi.ApiError} body.
 *
 * <p>All business rules of the mock service raise this rather than returning ad-hoc responses,
 * keeping validation deterministic and in one place.
 */
public class CoreBankException extends RuntimeException {

    private final int status;
    private final String code;

    public CoreBankException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }
}
