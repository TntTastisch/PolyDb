package de.tnttastisch.polydb.testentities;

import de.tnttastisch.polydb.core.annotations.Column;
import de.tnttastisch.polydb.core.annotations.Entity;
import de.tnttastisch.polydb.core.annotations.Id;
import de.tnttastisch.polydb.core.annotations.Table;
import de.tnttastisch.polydb.core.annotations.Transient;

import java.util.UUID;

/**
 * Exercises field filtering: {@code static}, Java {@code transient} and {@link Transient} fields
 * must never become columns.
 */
@Entity
@Table(name = "gadgets")
public class Gadget {

    public static final String CATEGORY = "tools";

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "name")
    private String name;

    private transient String cachedLabel;

    @Transient
    private String computedValue;

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCachedLabel() {
        return cachedLabel;
    }

    public String getComputedValue() {
        return computedValue;
    }
}
