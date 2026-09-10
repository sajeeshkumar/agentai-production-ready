package inc.kodingkrafters.corebank;

import inc.kodingkrafters.banking.CoreBankApi;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns every rejection from the Core Banking API into a consistent {@link CoreBankApi.ApiError}
 * body with the right status code — the shape {@code CoreBankClient} parses back into a
 * {@link inc.kodingkrafters.banking.CoreBankClientException}.
 */
@RestControllerAdvice(basePackages = "inc.kodingkrafters.corebank")
public class CoreBankApiExceptionHandler {

    @ExceptionHandler(CoreBankException.class)
    public ResponseEntity<CoreBankApi.ApiError> handleCoreBank(CoreBankException ex) {
        return ResponseEntity.status(ex.status())
                .body(new CoreBankApi.ApiError(ex.status(), ex.code(), ex.getMessage()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<CoreBankApi.ApiError> handleMissingHeader(MissingRequestHeaderException ex) {
        int status = HttpStatus.UNAUTHORIZED.value();
        return ResponseEntity.status(status).body(new CoreBankApi.ApiError(
                status, "UNAUTHENTICATED", "Missing required header '" + ex.getHeaderName() + "'."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CoreBankApi.ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        int status = HttpStatus.BAD_REQUEST.value();
        return ResponseEntity.status(status).body(new CoreBankApi.ApiError(
                status, "MALFORMED_BODY", "Request body is missing or not valid JSON."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CoreBankApi.ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        int status = HttpStatus.BAD_REQUEST.value();
        return ResponseEntity.status(status).body(new CoreBankApi.ApiError(
                status, "INVALID_PARAMETER", "Parameter '" + ex.getName() + "' has an invalid value."));
    }
}
