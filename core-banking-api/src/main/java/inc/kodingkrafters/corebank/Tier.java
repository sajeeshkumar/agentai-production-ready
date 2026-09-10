package inc.kodingkrafters.corebank;

/**
 * A customer's service tier. Determines which capabilities they may execute (see
 * {@link CustomerAuthorization}). {@code PRIVILEGED} &gt; {@code PREMIUM} &gt; {@code STANDARD}.
 */
public enum Tier {
    STANDARD,
    PREMIUM,
    PRIVILEGED
}
