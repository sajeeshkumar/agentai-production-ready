package inc.kodingkrafters.corebank;

/**
 * The things a customer can ask the bank to do. Each capability-bearing endpoint names its
 * capability and calls {@link CustomerAuthorization#require} before doing any work — that is the
 * authorization step. Capabilities not restricted by {@link CustomerAuthorization}'s policy are
 * available to every tier.
 */
public enum Capability {
    BALANCE_ENQUIRY,
    TRANSACTION_DETAILS,
    STATEMENT_REQUEST,
    CHANGE_OF_ADDRESS,
    CHEQUE_BOOK_REQUEST,
    KYC_UPDATE,
    INCREASE_CREDIT_LIMIT
}
