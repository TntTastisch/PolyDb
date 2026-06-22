package de.tnttastisch.polydb.schema.model;

/**
 * The kind of association represented by a {@link RelationModel}.
 */
public enum RelationType {
    /** Many entities reference one target; the foreign key lives on this (owning) side. */
    MANY_TO_ONE,
    /** Inverse of {@code MANY_TO_ONE}: one entity owns a collection, the foreign key lives on the child. */
    ONE_TO_MANY,
    /** One-to-one association; the owning side holds a unique foreign key, the inverse side uses {@code mappedBy}. */
    ONE_TO_ONE,
    /** Many-to-many association realised through a join table linking both sides' primary keys. */
    MANY_TO_MANY
}
