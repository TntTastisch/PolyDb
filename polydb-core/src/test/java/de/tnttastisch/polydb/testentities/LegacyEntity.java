package de.tnttastisch.polydb.testentities;

import de.tnttastisch.polydb.core.annotations.Column;
import de.tnttastisch.polydb.core.annotations.Entity;
import de.tnttastisch.polydb.core.annotations.Id;
import de.tnttastisch.polydb.core.annotations.Table;

import java.util.UUID;

/**
 * Test fixture with no no-arg constructor: the parser cannot build a template instance, so field
 * initialisers are unreadable. An explicit {@link Column#defaultValue()} ({@code status}) still
 * applies, while an initialised field with no explicit default ({@code active}) gets no {@code DEFAULT}.
 *
 * <p>Used by {@code EntityParserDefaultValueTest} to verify the template-instantiation fallback.</p>
 */
@Entity
@Table(name = "legacy")
public class LegacyEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "status", nullable = false, defaultValue = "'NEW'")
    private String status;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public LegacyEntity(UUID id, String status, boolean active) {
        this.id = id;
        this.status = status;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public boolean isActive() {
        return active;
    }
}
