package de.tnttastisch.polydb.schema.model;

import de.tnttastisch.polydb.core.annotations.CascadeType;
import de.tnttastisch.polydb.core.annotations.FetchType;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Immutable description of an association between two entities. The {@link de.tnttastisch.polydb.dialect.Dialect}
 * decides how (and whether) the relation is realised in the database: SQL dialects emit foreign-key
 * DDL, NoSQL dialects treat relations as a design concern (embedding / referencing / denormalisation).
 */
public class RelationModel {

    private final RelationType type;
    private final Field field;
    private final Class<?> targetEntity;
    private final boolean owningSide;
    private final String joinColumnName;
    private final String referencedColumnName;
    private final String referencedTable;
    private final String mappedBy;
    private final FetchType fetch;
    private final Set<CascadeType> cascade;
    private final boolean optional;
    private final JoinTableInfo joinTable;

    private RelationModel(Builder builder) {
        this.type = builder.type;
        this.field = builder.field;
        this.targetEntity = builder.targetEntity;
        this.owningSide = builder.owningSide;
        this.joinColumnName = builder.joinColumnName;
        this.referencedColumnName = builder.referencedColumnName;
        this.referencedTable = builder.referencedTable;
        this.mappedBy = builder.mappedBy;
        this.fetch = builder.fetch;
        this.cascade = builder.cascade == null || builder.cascade.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(builder.cascade));
        this.optional = builder.optional;
        this.joinTable = builder.joinTable;
    }

    public RelationType getType() {
        return type;
    }

    public Field getField() {
        return field;
    }

    public Class<?> getTargetEntity() {
        return targetEntity;
    }

    public boolean isOwningSide() {
        return owningSide;
    }

    /**
     * The foreign-key column on the owning entity's table (only meaningful for owning
     * {@code MANY_TO_ONE}/{@code ONE_TO_ONE} relations).
     */
    public String getJoinColumnName() {
        return joinColumnName;
    }

    /**
     * The referenced primary-key column on the target table.
     */
    public String getReferencedColumnName() {
        return referencedColumnName;
    }

    /**
     * The target table name this relation references (used to build foreign-key DDL).
     */
    public String getReferencedTable() {
        return referencedTable;
    }

    /**
     * The owning field name on the other side (only set on inverse relations).
     */
    public String getMappedBy() {
        return mappedBy;
    }

    public FetchType getFetch() {
        return fetch;
    }

    public Set<CascadeType> getCascade() {
        return cascade;
    }

    public boolean cascades(CascadeType cascadeType) {
        return cascade.contains(cascadeType) || cascade.contains(CascadeType.ALL);
    }

    public boolean isOptional() {
        return optional;
    }

    /**
     * Join-table metadata for {@code MANY_TO_MANY} relations; {@code null} otherwise.
     */
    public JoinTableInfo getJoinTable() {
        return joinTable;
    }

    public static Builder builder(RelationType type, Field field, Class<?> targetEntity) {
        return new Builder(type, field, targetEntity);
    }

    /**
     * Value object holding the join-table coordinates of a {@code MANY_TO_MANY} relation.
     */
    public static class JoinTableInfo {
        private final String tableName;
        private final String joinColumn;
        private final String inverseJoinColumn;

        public JoinTableInfo(String tableName, String joinColumn, String inverseJoinColumn) {
            this.tableName = tableName;
            this.joinColumn = joinColumn;
            this.inverseJoinColumn = inverseJoinColumn;
        }

        public String getTableName() {
            return tableName;
        }

        public String getJoinColumn() {
            return joinColumn;
        }

        public String getInverseJoinColumn() {
            return inverseJoinColumn;
        }
    }

    public static class Builder {
        private final RelationType type;
        private final Field field;
        private final Class<?> targetEntity;
        private boolean owningSide;
        private String joinColumnName;
        private String referencedColumnName;
        private String referencedTable;
        private String mappedBy;
        private FetchType fetch = FetchType.EAGER;
        private Set<CascadeType> cascade;
        private boolean optional = true;
        private JoinTableInfo joinTable;

        private Builder(RelationType type, Field field, Class<?> targetEntity) {
            this.type = type;
            this.field = field;
            this.targetEntity = targetEntity;
        }

        public Builder owningSide(boolean owningSide) {
            this.owningSide = owningSide;
            return this;
        }

        public Builder joinColumnName(String joinColumnName) {
            this.joinColumnName = joinColumnName;
            return this;
        }

        public Builder referencedColumnName(String referencedColumnName) {
            this.referencedColumnName = referencedColumnName;
            return this;
        }

        public Builder referencedTable(String referencedTable) {
            this.referencedTable = referencedTable;
            return this;
        }

        public Builder mappedBy(String mappedBy) {
            this.mappedBy = mappedBy;
            return this;
        }

        public Builder fetch(FetchType fetch) {
            this.fetch = fetch;
            return this;
        }

        public Builder cascade(Set<CascadeType> cascade) {
            this.cascade = cascade;
            return this;
        }

        public Builder optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        public Builder joinTable(JoinTableInfo joinTable) {
            this.joinTable = joinTable;
            return this;
        }

        public RelationModel build() {
            return new RelationModel(this);
        }
    }
}
