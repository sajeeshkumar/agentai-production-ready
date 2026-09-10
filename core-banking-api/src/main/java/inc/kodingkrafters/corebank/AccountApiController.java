package inc.kodingkrafters.corebank;

import inc.kodingkrafters.banking.CoreBankApi;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * SecureBank Core Banking API — account-scoped endpoints: balance enquiry, transaction
 * details, statement requests, cheque-book requests, and credit-limit increases.
 *
 * <p>Every call presents the signed-in customer in {@code X-Customer-Id}. Each endpoint first
 * runs the authorization step ({@link CustomerAuthorization#require}) for its {@link Capability},
 * then authorizes the account against the customer, then validates.
 */
@RestController
@RequestMapping("/corebank/v1/accounts/{accountId}")
class AccountApiController {

    private static final Set<String> STATEMENT_FORMATS = Set.of("PDF", "CSV");
    private static final Set<Integer> CHEQUE_BOOK_LEAVES = Set.of(25, 50, 100);
    private static final BigDecimal MAX_CREDIT_LIMIT = new BigDecimal("10000.00");

    private final CoreBankData data;
    private final CustomerAuthorization authorization;

    AccountApiController(CoreBankData data, CustomerAuthorization authorization) {
        this.data = data;
        this.authorization = authorization;
    }

    @GetMapping("/balance")
    CoreBankApi.BalanceResponse balance(@PathVariable String accountId,
                                        @RequestHeader("X-Customer-Id") String customerId) {
        authorization.require(customerId, Capability.BALANCE_ENQUIRY);
        CoreBankData.Account account = authorizedAccount(accountId, customerId);
        return new CoreBankApi.BalanceResponse(account.accountId(), account.accountName(),
                account.accountType(), account.currency(), account.currentBalance(),
                account.availableBalance(), account.creditLimit(), account.status(), Instant.now());
    }

    @GetMapping("/transactions")
    CoreBankApi.TransactionsResponse transactions(@PathVariable String accountId,
                                                  @RequestHeader("X-Customer-Id") String customerId,
                                                  @RequestParam(required = false) LocalDate from,
                                                  @RequestParam(required = false) LocalDate to,
                                                  @RequestParam(defaultValue = "20") int limit) {
        authorization.require(customerId, Capability.TRANSACTION_DETAILS);
        authorizedAccount(accountId, customerId);

        LocalDate today = LocalDate.now();
        LocalDate toDate = to != null ? to : today;
        LocalDate fromDate = from != null ? from : toDate.minusDays(30);

        if (toDate.isAfter(today)) {
            throw new CoreBankException(422, "INVALID_DATE_RANGE", "'to' cannot be in the future.");
        }
        if (fromDate.isAfter(toDate)) {
            throw new CoreBankException(422, "INVALID_DATE_RANGE", "'from' must be on or before 'to'.");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) > 90) {
            throw new CoreBankException(422, "INVALID_DATE_RANGE", "The date range cannot exceed 90 days.");
        }
        if (limit < 1 || limit > 50) {
            throw new CoreBankException(422, "INVALID_LIMIT", "'limit' must be between 1 and 50.");
        }

        List<CoreBankApi.TransactionRecord> hits = data.transactions(accountId).stream()
                .filter(t -> !t.date().isBefore(fromDate) && !t.date().isAfter(toDate))
                .sorted(Comparator.comparing(CoreBankApi.TransactionRecord::date).reversed())
                .limit(limit)
                .toList();

        return new CoreBankApi.TransactionsResponse(accountId, fromDate, toDate, hits.size(), hits);
    }

    @PostMapping("/statements")
    @ResponseStatus(HttpStatus.ACCEPTED)
    CoreBankApi.StatementResponse statement(@PathVariable String accountId,
                                            @RequestHeader("X-Customer-Id") String customerId,
                                            @RequestBody CoreBankApi.StatementRequest request) {
        authorization.require(customerId, Capability.STATEMENT_REQUEST);
        authorizedAccount(accountId, customerId);
        CoreBankData.Customer customer = data.findCustomer(customerId).orElseThrow(
                () -> new CoreBankException(404, "CUSTOMER_NOT_FOUND", "Unknown customer."));

        if (request == null || request.fromDate() == null || request.toDate() == null) {
            throw new CoreBankException(422, "MISSING_FIELD", "'fromDate' and 'toDate' are both required.");
        }
        LocalDate today = LocalDate.now();
        if (!request.fromDate().isBefore(request.toDate())) {
            throw new CoreBankException(422, "INVALID_DATE_RANGE", "'fromDate' must be before 'toDate'.");
        }
        if (request.toDate().isAfter(today)) {
            throw new CoreBankException(422, "INVALID_DATE_RANGE", "'toDate' cannot be in the future.");
        }
        if (ChronoUnit.DAYS.between(request.fromDate(), request.toDate()) > 366) {
            throw new CoreBankException(422, "INVALID_DATE_RANGE", "A statement period cannot exceed 366 days.");
        }
        String format = request.format() == null ? "PDF" : request.format().trim().toUpperCase();
        if (!STATEMENT_FORMATS.contains(format)) {
            throw new CoreBankException(422, "UNSUPPORTED_FORMAT", "Format must be one of " + STATEMENT_FORMATS + ".");
        }

        return new CoreBankApi.StatementResponse(CoreBankRules.reference("STMT"), accountId,
                request.fromDate(), request.toDate(), format, "EMAIL", maskEmail(customer.email()),
                today.plusDays(1));
    }

    @PostMapping("/cheque-books")
    @ResponseStatus(HttpStatus.ACCEPTED)
    CoreBankApi.ChequeBookResponse chequeBook(@PathVariable String accountId,
                                              @RequestHeader("X-Customer-Id") String customerId,
                                              @RequestBody(required = false) CoreBankApi.ChequeBookRequest request) {
        authorization.require(customerId, Capability.CHEQUE_BOOK_REQUEST);
        CoreBankData.Account account = authorizedAccount(accountId, customerId);

        if (!"CURRENT".equals(account.accountType())) {
            throw new CoreBankException(422, "UNSUPPORTED_ACCOUNT_TYPE",
                    "Cheque books are only available on current accounts.");
        }
        if (!"ACTIVE".equals(account.status())) {
            throw new CoreBankException(409, "ACCOUNT_NOT_ACTIVE",
                    "Account " + accountId + " is " + account.status().toLowerCase()
                            + "; a cheque book cannot be issued.");
        }
        int leaves = (request == null || request.leaves() == null) ? 25 : request.leaves();
        if (!CHEQUE_BOOK_LEAVES.contains(leaves)) {
            throw new CoreBankException(422, "INVALID_LEAVES", "'leaves' must be one of " + CHEQUE_BOOK_LEAVES + ".");
        }

        return new CoreBankApi.ChequeBookResponse(CoreBankRules.reference("CHQ"), accountId, leaves,
                data.currentAddress(customerId), LocalDate.now().plusDays(5));
    }

    @PostMapping("/credit-limit")
    CoreBankApi.CreditLimitIncreaseResponse creditLimitIncrease(
            @PathVariable String accountId,
            @RequestHeader("X-Customer-Id") String customerId,
            @RequestBody(required = false) CoreBankApi.CreditLimitIncreaseRequest request) {
        authorization.require(customerId, Capability.INCREASE_CREDIT_LIMIT);
        CoreBankData.Account account = authorizedAccount(accountId, customerId);

        if (!"CURRENT".equals(account.accountType())) {
            throw new CoreBankException(422, "UNSUPPORTED_ACCOUNT_TYPE",
                    "A credit limit applies only to current accounts.");
        }
        if (!"ACTIVE".equals(account.status())) {
            throw new CoreBankException(409, "ACCOUNT_NOT_ACTIVE",
                    "Account " + accountId + " is " + account.status().toLowerCase()
                            + "; its credit limit cannot be changed.");
        }
        if (request == null || request.newLimit() == null) {
            throw new CoreBankException(422, "MISSING_FIELD", "'newLimit' is required.");
        }
        BigDecimal current = account.creditLimit() == null ? BigDecimal.ZERO : account.creditLimit();
        BigDecimal requested = request.newLimit();
        if (requested.compareTo(current) <= 0) {
            throw new CoreBankException(422, "INVALID_CREDIT_LIMIT",
                    "The new limit (" + requested + ") must be greater than the current limit (" + current + ").");
        }
        if (requested.compareTo(MAX_CREDIT_LIMIT) > 0) {
            throw new CoreBankException(422, "INVALID_CREDIT_LIMIT",
                    "The new limit cannot exceed " + MAX_CREDIT_LIMIT + ".");
        }

        data.updateCreditLimit(accountId, requested);
        return new CoreBankApi.CreditLimitIncreaseResponse(CoreBankRules.reference("CLI"), accountId,
                current, requested, LocalDate.now());
    }

    private CoreBankData.Account authorizedAccount(String accountId, String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new CoreBankException(401, "UNAUTHENTICATED", "Missing X-Customer-Id.");
        }
        CoreBankData.Account account = data.findAccount(accountId).orElseThrow(
                () -> new CoreBankException(404, "ACCOUNT_NOT_FOUND", "No account " + accountId + "."));
        if (!account.customerId().equals(customerId)) {
            throw new CoreBankException(403, "ACCOUNT_FORBIDDEN",
                    "Account " + accountId + " does not belong to the signed-in customer.");
        }
        return account;
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
