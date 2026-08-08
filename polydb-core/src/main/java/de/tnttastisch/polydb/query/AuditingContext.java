package de.tnttastisch.polydb.query;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Supplies the current auditor for {@code @CreatedBy}/{@code @LastModifiedBy} fields. Without a
 * dependency-injection container, PolyDB reads the auditor from this thread-safe static holder: an
 * application configures it once (e.g. {@code AuditingContext.setAuditorProvider(() -> currentUser())}),
 * and the value is resolved on each write. When unset, auditor fields are left untouched.
 */
public final class AuditingContext {

    private static volatile Supplier<Object> auditorProvider = () -> null;

    private AuditingContext() {
    }

    /** Sets the provider queried for the current auditor on each write. */
    public static void setAuditorProvider(Supplier<Object> provider) {
        auditorProvider = provider == null ? () -> null : provider;
    }

    /** Sets a fixed current auditor (convenience over {@link #setAuditorProvider}). */
    public static void setAuditor(Object auditor) {
        auditorProvider = () -> auditor;
    }

    /** Clears the configured auditor. */
    public static void clear() {
        auditorProvider = () -> null;
    }

    /** The current auditor, if any. */
    public static Optional<Object> getAuditor() {
        return Optional.ofNullable(auditorProvider.get());
    }
}
