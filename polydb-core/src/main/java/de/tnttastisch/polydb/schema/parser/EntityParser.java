package de.tnttastisch.polydb.schema.parser;

import de.tnttastisch.polydb.core.annotations.*;
import de.tnttastisch.polydb.core.exception.PolyDBException;
import de.tnttastisch.polydb.schema.model.*;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reflective front end of the schema pipeline: turns annotated {@code @Entity} classes into
 * dialect-independent {@link EntityModel}s. It reads the PolyDB annotations
 * ({@code @Id}, {@code @Column}, {@code @Index}, the relation annotations, ...) and resolves
 * conventions (default table/column names, default join columns) so the rest of the pipeline can
 * work against a uniform model without touching reflection again.
 *
 * <p>Relations are expanded here: an owning {@code @ManyToOne}/{@code @OneToOne} additionally yields
 * a synthetic scalar foreign-key {@link FieldModel} so the column appears in the generated DDL, while
 * inverse sides ({@code mappedBy}) contribute only relation metadata and no column.</p>
 */
public class EntityParser {

    /**
     * Scans {@code packageName} (and sub-packages) on the classpath for {@code @Entity} classes and
     * parses each one.
     *
     * @return one {@link EntityModel} per discovered entity, ready for comparison/generation.
     */
    public List<EntityModel> parsePackage(String packageName) {
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .forPackage(packageName)
                .addScanners(Scanners.TypesAnnotated));
        Set<Class<?>> entityClasses = reflections.getTypesAnnotatedWith(Entity.class);

        // Reflections resolves classpath roots for the package, so it can surface @Entity classes
        // outside it; restrict to the requested package (and its sub-packages).
        return entityClasses.stream()
                .filter(clazz -> clazz.getName().startsWith(packageName + "."))
                .map(this::parseEntity)
                .collect(Collectors.toList());
    }

    /**
     * Parses a single entity class into its {@link EntityModel}. Declared fields are split into
     * scalar columns and relations; indexes are collected from both the class-level {@code @Index}
     * and field-level {@code @Index} annotations.
     *
     * <p>Note that fields are walked twice: once to build columns/relations, and a second time to
     * collect field-level indexes — the index pass runs after column parsing so it can reuse the
     * resolved column name (honouring {@code @Column.name()}).</p>
     *
     * @throws PolyDBException if {@code clazz} is not an {@code @Entity}, or a class-level index
     *                         declares no columns.
     */
    public EntityModel parseEntity(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(Entity.class)) {
            throw new PolyDBException("Class " + clazz.getName() + " is not annotated with @Entity");
        }

        EntityModel entityModel = new EntityModel(clazz.getName(), tableNameOf(clazz));

        // A throwaway instance used only to read field initialisers as column defaults (see
        // parseField). Null when the entity has no usable no-arg constructor, in which case columns
        // simply fall back to their explicit @Column.defaultValue() (if any).
        Object template = instantiateTemplate(clazz);

        for (Field field : clazz.getDeclaredFields()) {
            if (isSkipped(field)) {
                continue;
            }
            if (isRelation(field)) {
                parseRelation(field, clazz, entityModel);
                continue;
            }

            entityModel.addField(parseField(field, template));
        }

        if (clazz.isAnnotationPresent(Index.class)) {
            IndexModel index = parseIndexAnnotation(clazz.getAnnotation(Index.class), null);
            if (index.getColumns().isEmpty()) {
                throw new PolyDBException("Class-level @Index on " + clazz.getSimpleName()
                        + " must declare at least one column");
            }
            entityModel.addIndex(index);
        }

        for (Field field : clazz.getDeclaredFields()) {
            if (isSkipped(field) || isRelation(field)) {
                continue;
            }
            if (field.isAnnotationPresent(Index.class)) {
                String colName = field.getName().toLowerCase();
                if (field.isAnnotationPresent(Column.class)) {
                    Column col = field.getAnnotation(Column.class);
                    if (!col.name().isEmpty()) colName = col.name();
                }
                entityModel.addIndex(parseIndexAnnotation(field.getAnnotation(Index.class), colName));
            }
        }

        return entityModel;
    }

    /**
     * Fields that are never persisted: static, synthetic, Java {@code transient}, or {@link Transient}.
     */
    private boolean isSkipped(Field field) {
        int mods = field.getModifiers();
        return Modifier.isStatic(mods)
                || Modifier.isTransient(mods)
                || field.isSynthetic()
                || field.isAnnotationPresent(Transient.class);
    }

    /** A field is a relation if it carries exactly one of the four association annotations. */
    private boolean isRelation(Field field) {
        return field.isAnnotationPresent(ManyToOne.class)
                || field.isAnnotationPresent(OneToMany.class)
                || field.isAnnotationPresent(OneToOne.class)
                || field.isAnnotationPresent(ManyToMany.class);
    }

    /**
     * Builds the {@link FieldModel} for a scalar (non-relation) field, applying annotation overrides
     * over convention defaults (lower-cased field name as column, length 255, nullable). An
     * {@code @Id} field is forced NOT NULL and UNIQUE regardless of any {@code @Column} settings.
     *
     * <p>The column default is taken from {@code @Column.defaultValue()} if set, otherwise derived
     * from the field's initialised value on {@code template} (see {@link #derivedDefault}).</p>
     */
    private FieldModel parseField(Field field, Object template) {
        String columnName = field.getName().toLowerCase();
        boolean id = field.isAnnotationPresent(Id.class);
        boolean autoIncrement = false;
        boolean nullable = true;
        int length = 255;
        int precision = 0;
        int scale = 0;
        String defaultValue = "";

        boolean unique = field.isAnnotationPresent(Unique.class);

        if (id) {
            Id idAnnotation = field.getAnnotation(Id.class);
            autoIncrement = idAnnotation.autoIncrement();
            nullable = false;
            unique = true;
        }

        if (field.isAnnotationPresent(Column.class)) {
            Column column = field.getAnnotation(Column.class);
            if (!column.name().isEmpty()) {
                columnName = column.name();
            }
            // An @Id column is always NOT NULL; @Column.nullable() must not relax it.
            if (!id) {
                nullable = column.nullable();
            }
            length = column.length();
            precision = column.precision();
            scale = column.scale();
            defaultValue = column.defaultValue();
        }

        // When no explicit default is declared, derive one from the field's initialised value on a
        // freshly constructed instance (e.g. {@code private boolean notify = false} -> DEFAULT false).
        // Skipped for @Id and auto-increment columns, whose values are assigned by the database.
        if (defaultValue.isEmpty() && !id && !autoIncrement) {
            defaultValue = derivedDefault(field, template);
        }

        return new FieldModel(field, columnName, field.getType(), id, autoIncrement, nullable, unique, length, precision, scale, defaultValue);
    }

    /**
     * Reads {@code field}'s value from the throwaway {@code template} instance and renders it as a SQL
     * literal, or {@code ""} when there is no template, the value is {@code null}, or its type has no
     * obvious literal form (UUIDs, dates, collections, nested entities — use an explicit
     * {@code @Column.defaultValue()} for those).
     */
    private String derivedDefault(Field field, Object template) {
        if (template == null) {
            return "";
        }
        Object value;
        try {
            field.setAccessible(true);
            value = field.get(template);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        if (value instanceof CharSequence || value instanceof Enum<?>) {
            String text = value instanceof Enum<?> e ? e.name() : value.toString();
            return "'" + text.replace("'", "''") + "'";
        }
        return "";
    }

    /**
     * Builds a throwaway instance via the no-arg constructor purely so {@link #derivedDefault} can read
     * field initialisers. Returns {@code null} (defaults simply fall back to {@code @Column}) when the
     * entity has no accessible no-arg constructor or constructing it throws.
     */
    private Object instantiateTemplate(Class<?> clazz) {
        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ relations

    /**
     * Dispatches a relation field to the parser for its specific association type, after rejecting
     * fields that carry more than one relation annotation.
     */
    private void parseRelation(Field field, Class<?> owner, EntityModel entityModel) {
        validateSingleRelationAnnotation(field);

        if (field.isAnnotationPresent(ManyToOne.class)) {
            parseManyToOne(field, entityModel);
            return;
        }

        if (field.isAnnotationPresent(OneToOne.class)) {
            parseOneToOne(field, entityModel);
            return;
        }

        if (field.isAnnotationPresent(OneToMany.class)) {
            parseOneToMany(field, entityModel);
            return;
        }

        if (field.isAnnotationPresent(ManyToMany.class)) {
            parseManyToMany(field, owner, entityModel);
        }
    }

    /** Always the owning side: registers the relation and adds its scalar foreign-key column. */
    private void parseManyToOne(Field field, EntityModel entityModel) {
        ManyToOne annotation = field.getAnnotation(ManyToOne.class);
        Class<?> target = resolveTarget(annotation.targetEntity(), field.getType(), field);
        requireEntityWithId(target, field);

        Set<CascadeType> cascade = toCascadeSet(annotation.cascade());
        RelationModel relation = buildOwningRelation(RelationType.MANY_TO_ONE, field, target,
                annotation.optional(), annotation.fetch(), cascade);
        entityModel.addRelation(relation);
        entityModel.addField(foreignKeyColumn(relation, target));
    }

    /**
     * Owning side (no {@code mappedBy}) gets a unique foreign-key column; the inverse side only
     * records relation metadata pointing back via {@code mappedBy}.
     */
    private void parseOneToOne(Field field, EntityModel entityModel) {
        OneToOne annotation = field.getAnnotation(OneToOne.class);
        Class<?> target = resolveTarget(annotation.targetEntity(), field.getType(), field);
        requireEntityWithId(target, field);
        Set<CascadeType> cascade = toCascadeSet(annotation.cascade());

        if (annotation.mappedBy().isEmpty()) {
            RelationModel relation = buildOwningRelation(RelationType.ONE_TO_ONE, field, target,
                    annotation.optional(), annotation.fetch(), cascade);
            entityModel.addRelation(relation);
            entityModel.addField(foreignKeyColumn(relation, target));
            return;
        }

        entityModel.addRelation(RelationModel.builder(RelationType.ONE_TO_ONE, field, target)
                .owningSide(false)
                .mappedBy(annotation.mappedBy())
                .fetch(annotation.fetch())
                .cascade(cascade)
                .optional(annotation.optional())
                .build());
    }

    /**
     * Always the inverse side: a one-to-many is mandatory {@code mappedBy} (the foreign key lives on
     * the child's many-to-one side), so no column is added here — only relation metadata. The target
     * type is taken from the collection's element type.
     */
    private void parseOneToMany(Field field, EntityModel entityModel) {
        OneToMany annotation = field.getAnnotation(OneToMany.class);
        if (annotation.mappedBy().isEmpty()) {
            throw new PolyDBException("@OneToMany on " + describe(field) + " requires a non-empty mappedBy()");
        }
        Class<?> target = resolveTarget(annotation.targetEntity(), collectionElementType(field), field);
        requireEntityWithId(target, field);

        entityModel.addRelation(RelationModel.builder(RelationType.ONE_TO_MANY, field, target)
                .owningSide(false)
                .mappedBy(annotation.mappedBy())
                .fetch(annotation.fetch())
                .cascade(toCascadeSet(annotation.cascade()))
                .build());
    }

    /**
     * Owning side (no {@code mappedBy}) resolves the join table linking both ids; the inverse side
     * records only relation metadata. Neither side adds a column on the entity's own table — the
     * link columns live on the synthesised join table built later by the comparator.
     */
    private void parseManyToMany(Field field, Class<?> owner, EntityModel entityModel) {
        ManyToMany annotation = field.getAnnotation(ManyToMany.class);
        Class<?> target = resolveTarget(annotation.targetEntity(), collectionElementType(field), field);
        requireEntityWithId(target, field);
        Set<CascadeType> cascade = toCascadeSet(annotation.cascade());

        if (annotation.mappedBy().isEmpty()) {
            RelationModel.JoinTableInfo joinTable = resolveJoinTable(field, owner, target);
            entityModel.addRelation(RelationModel.builder(RelationType.MANY_TO_MANY, field, target)
                    .owningSide(true)
                    .referencedColumnName(idColumnNameOf(target))
                    .referencedTable(tableNameOf(target))
                    .fetch(annotation.fetch())
                    .cascade(cascade)
                    .joinTable(joinTable)
                    .build());
            return;
        }

        entityModel.addRelation(RelationModel.builder(RelationType.MANY_TO_MANY, field, target)
                .owningSide(false)
                .mappedBy(annotation.mappedBy())
                .fetch(annotation.fetch())
                .cascade(cascade)
                .build());
    }

    /**
     * Builds the relation metadata for an owning to-one association, applying {@code @JoinColumn}
     * overrides or falling back to conventions ({@code <field>_id} for the column, the target's id
     * column as the referenced column). The column is nullable only when the relation is optional
     * <em>and</em> the join column is not explicitly marked non-null.
     */
    private RelationModel buildOwningRelation(RelationType type, Field field, Class<?> target,
                                              boolean optional, FetchType fetch, Set<CascadeType> cascade) {
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
        String columnName = joinColumn != null && !joinColumn.name().isEmpty()
                ? joinColumn.name()
                : field.getName().toLowerCase() + "_id";
        String referencedColumn = joinColumn != null && !joinColumn.referencedColumnName().isEmpty()
                ? joinColumn.referencedColumnName()
                : idColumnNameOf(target);
        boolean nullable = optional && (joinColumn == null || joinColumn.nullable());

        return RelationModel.builder(type, field, target)
                .owningSide(true)
                .joinColumnName(columnName)
                .referencedColumnName(referencedColumn)
                .referencedTable(tableNameOf(target))
                .fetch(fetch)
                .cascade(cascade)
                .optional(nullable)
                .build();
    }

    /**
     * Builds the scalar foreign-key column for an owning relation. The column type is the target
     * entity's id type so the dialect resolves the matching SQL type (UUID, BIGINT, ...).
     */
    private FieldModel foreignKeyColumn(RelationModel relation, Class<?> target) {
        Field idField = requireIdField(target);
        // An owning one-to-one must enforce one-to-one cardinality with a UNIQUE foreign key;
        // a many-to-one foreign key is not unique.
        boolean unique = relation.getType() == RelationType.ONE_TO_ONE;
        FieldModel fk = new FieldModel(relation.getField(), relation.getJoinColumnName(), idField.getType(),
                false, false, relation.isOptional(), unique, 255, 0, 0);
        fk.setRelation(relation);
        return fk;
    }

    // ------------------------------------------------------------------ helpers

    private void validateSingleRelationAnnotation(Field field) {
        int count = 0;
        if (field.isAnnotationPresent(ManyToOne.class)) count++;
        if (field.isAnnotationPresent(OneToMany.class)) count++;
        if (field.isAnnotationPresent(OneToOne.class)) count++;
        if (field.isAnnotationPresent(ManyToMany.class)) count++;
        if (count > 1) {
            throw new PolyDBException("Field " + describe(field) + " carries multiple conflicting relation annotations");
        }
    }

    /**
     * Picks the target entity class: an explicit {@code targetEntity()} wins, otherwise the field's
     * own type (or collection element type) is used. {@code void.class} is the annotation's "unset"
     * sentinel and is treated as absent.
     */
    private Class<?> resolveTarget(Class<?> declared, Class<?> fallback, Field field) {
        Class<?> target = declared != null && declared != void.class ? declared : fallback;
        if (target == null || target == void.class) {
            throw new PolyDBException("Could not determine target entity for relation on " + describe(field)
                    + "; specify targetEntity() explicitly");
        }
        return target;
    }

    /**
     * Extracts {@code E} from a {@code Collection<E>}-typed field via its generic signature; returns
     * {@code null} when the field is not a collection or its element type cannot be resolved to a
     * concrete class (e.g. a wildcard or type variable).
     */
    private Class<?> collectionElementType(Field field) {
        if (!Collection.class.isAssignableFrom(field.getType())) {
            return null;
        }
        Type generic = field.getGenericType();
        if (generic instanceof ParameterizedType parameterized) {
            Type[] args = parameterized.getActualTypeArguments();
            if (args.length == 1 && args[0] instanceof Class<?> elementType) {
                return elementType;
            }
        }
        return null;
    }

    private void requireEntityWithId(Class<?> target, Field field) {
        if (!target.isAnnotationPresent(Entity.class)) {
            throw new PolyDBException("Target " + target.getName() + " of relation on " + describe(field)
                    + " is not annotated with @Entity");
        }
        requireIdField(target);
    }

    private Field requireIdField(Class<?> target) {
        Field idField = findIdField(target);
        if (idField == null) {
            throw new PolyDBException("Target entity " + target.getName() + " has no @Id field");
        }
        return idField;
    }

    private Field findIdField(Class<?> target) {
        for (Field field : target.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                return field;
            }
        }
        return null;
    }

    /** Resolves the column name of the target's {@code @Id} field (honouring {@code @Column.name()}). */
    private String idColumnNameOf(Class<?> target) {
        Field idField = requireIdField(target);
        if (idField.isAnnotationPresent(Column.class)) {
            Column column = idField.getAnnotation(Column.class);
            if (!column.name().isEmpty()) {
                return column.name();
            }
        }
        return idField.getName().toLowerCase();
    }

    /** Table name from {@code @Table.name()}, defaulting to the lower-cased simple class name. */
    private String tableNameOf(Class<?> clazz) {
        if (clazz.isAnnotationPresent(Table.class)) {
            Table table = clazz.getAnnotation(Table.class);
            if (!table.name().isEmpty()) {
                return table.name();
            }
        }
        return clazz.getSimpleName().toLowerCase();
    }

    /**
     * Resolves the join-table coordinates for a many-to-many: explicit {@code @JoinTable} values, or
     * conventions ({@code owner_target} table, {@code owner_id}/{@code target_id} link columns).
     */
    private RelationModel.JoinTableInfo resolveJoinTable(Field field, Class<?> owner, Class<?> target) {
        if (field.isAnnotationPresent(JoinTable.class)) {
            JoinTable jt = field.getAnnotation(JoinTable.class);
            return new RelationModel.JoinTableInfo(jt.name(), jt.joinColumn(), jt.inverseJoinColumn());
        }
        String tableName = tableNameOf(owner) + "_" + tableNameOf(target);
        String joinColumn = owner.getSimpleName().toLowerCase() + "_id";
        String inverseJoinColumn = target.getSimpleName().toLowerCase() + "_id";
        return new RelationModel.JoinTableInfo(tableName, joinColumn, inverseJoinColumn);
    }

    private Set<CascadeType> toCascadeSet(CascadeType[] cascade) {
        if (cascade == null || cascade.length == 0) {
            return Collections.emptySet();
        }
        return EnumSet.copyOf(Arrays.asList(cascade));
    }

    private String describe(Field field) {
        return field.getDeclaringClass().getSimpleName() + "." + field.getName();
    }

    /**
     * Converts an {@code @Index} annotation to an {@link IndexModel}. When the annotation lists no
     * columns (a field-level index), {@code defaultColumn} is used as the single covered column.
     */
    private IndexModel parseIndexAnnotation(Index index, String defaultColumn) {
        String name = index.name();
        List<String> columns = Arrays.asList(index.columns());
        if (columns.isEmpty() && defaultColumn != null) {
            columns = Collections.singletonList(defaultColumn);
        }
        return new IndexModel(name, columns, index.unique());
    }
}
