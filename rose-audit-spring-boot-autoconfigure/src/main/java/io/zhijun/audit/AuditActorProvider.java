package io.zhijun.audit;

/**
 * Supplies the identity and optional display name of the user or system responsible for the current execution.
 */
@FunctionalInterface
public interface AuditActorProvider {
    String currentActorId();

    /**
     * Returns a display-name snapshot when available.
     */
    default String currentActorName() {
        return null;
    }
}
