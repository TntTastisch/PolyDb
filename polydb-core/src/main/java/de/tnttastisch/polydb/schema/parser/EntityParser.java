package de.tnttastisch.polydb.schema.parser;

import de.tnttastisch.polydb.core.annotations.*;
import de.tnttastisch.polydb.core.exception.PolyDBException;
import de.tnttastisch.polydb.schema.model.EntityModel;
import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.model.IndexModel;
import de.tnttastisch.polydb.schema.model.RelationModel;
import de.tnttastisch.polydb.schema.model.RelationType;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class EntityParser {

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

    public EntityModel parseEntity(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(Entity.class)) {
            throw new PolyDBException("Class " + clazz.getName() + " is not annotated with @Entity");
        }

        EntityModel entityModel = new EntityModel(clazz.getName(), tableNameOf(clazz));

        for (Field field : clazz.getDeclaredFields()) {
            if (isSkipped(field)) {
                continue;
            }
            if (isRelation(field)) {
                parseRelation(field, clazz, entityModel);
            } else {
                entityModel.addField(parseField(field));
            }
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

    private boolean isRelation(Field field) {
        return field.isAnnotationPresent(ManyToOne.class)
                || field.isAnnotationPresent(OneToMany.class)
                || field.isAnnotationPresent(OneToOne.class)
                || field.isAnnotationPresent(ManyToMany.class);
    }

    private FieldModel parseField(Field field) {
        String columnName = field.getName().toLowerCase();
        boolean id = field.isAnnotationPresent(Id.class);
        boolean autoIncrement = false;
        boolean nullable = true;
        int length = 255;
        int precision = 0;
        int scale = 0;

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
        }

        return new FieldModel(field, columnName, field.getType(), id, autoIncrement, nullable, unique, length, precision, scale);
    }

    // ------------------------------------------------------------------ relations

    private void parseRelation(Field field, Class<?> owner, EntityModel entityModel) {
        validateSingleRelationAnnotation(field);

        if (field.isAnnotationPresent(ManyToOne.class)) {
            parseManyToOne(field, entityModel);
        } else if (field.isAnnotationPresent(OneToOne.class)) {
            parseOneToOne(field, entityModel);
        } else if (field.isAnnotationPresent(OneToMany.class)) {
            parseOneToMany(field, entityModel);
        } else if (field.isAnnotationPresent(ManyToMany.class)) {
            parseManyToMany(field, owner, entityModel);
        }
    }

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
        } else {
            entityModel.addRelation(RelationModel.builder(RelationType.ONE_TO_ONE, field, target)
                    .owningSide(false)
                    .mappedBy(annotation.mappedBy())
                    .fetch(annotation.fetch())
                    .cascade(cascade)
                    .optional(annotation.optional())
                    .build());
        }
    }

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
        } else {
            entityModel.addRelation(RelationModel.builder(RelationType.MANY_TO_MANY, field, target)
                    .owningSide(false)
                    .mappedBy(annotation.mappedBy())
                    .fetch(annotation.fetch())
                    .cascade(cascade)
                    .build());
        }
    }

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

    private Class<?> resolveTarget(Class<?> declared, Class<?> fallback, Field field) {
        Class<?> target = declared != null && declared != void.class ? declared : fallback;
        if (target == null || target == void.class) {
            throw new PolyDBException("Could not determine target entity for relation on " + describe(field)
                    + "; specify targetEntity() explicitly");
        }
        return target;
    }

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

    private String tableNameOf(Class<?> clazz) {
        if (clazz.isAnnotationPresent(Table.class)) {
            Table table = clazz.getAnnotation(Table.class);
            if (!table.name().isEmpty()) {
                return table.name();
            }
        }
        return clazz.getSimpleName().toLowerCase();
    }

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

    private IndexModel parseIndexAnnotation(Index index, String defaultColumn) {
        String name = index.name();
        List<String> columns = Arrays.asList(index.columns());
        if (columns.isEmpty() && defaultColumn != null) {
            columns = Collections.singletonList(defaultColumn);
        }
        return new IndexModel(name, columns, index.unique());
    }
}
