package de.tnttastisch.polydb.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the join table for the owning side of a {@link ManyToMany} association.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JoinTable {

    /**
     * Name of the join table.
     */
    String name();

    /**
     * Join-table column referencing this (owning) entity's primary key.
     */
    String joinColumn();

    /**
     * Join-table column referencing the target entity's primary key.
     */
    String inverseJoinColumn();
}
