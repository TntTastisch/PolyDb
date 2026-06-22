package de.tnttastisch.polydb.schema.model;

/**
 * The kind of association represented by a {@link RelationModel}.
 */
public enum RelationType {
    MANY_TO_ONE,
    ONE_TO_MANY,
    ONE_TO_ONE,
    MANY_TO_MANY
}
