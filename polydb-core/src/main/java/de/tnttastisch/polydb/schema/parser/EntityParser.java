package de.tnttastisch.polydb.schema.parser;

import de.tnttastisch.polydb.core.annotations.*;
import de.tnttastisch.polydb.core.exception.PolyDBException;
import de.tnttastisch.polydb.schema.model.EntityModel;
import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.model.IndexModel;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class EntityParser {

    public List<EntityModel> parsePackage(String packageName) {
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .forPackage(packageName)
                .addScanners(Scanners.TypesAnnotated));
        Set<Class<?>> entityClasses = reflections.getTypesAnnotatedWith(Entity.class);

        return entityClasses.stream()
                .map(this::parseEntity)
                .collect(Collectors.toList());
    }

    public EntityModel parseEntity(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(Entity.class)) {
            throw new PolyDBException("Class " + clazz.getName() + " is not annotated with @Entity");
        }

        String tableName = clazz.getSimpleName().toLowerCase();
        if (clazz.isAnnotationPresent(Table.class)) {
            Table table = clazz.getAnnotation(Table.class);
            if (!table.name().isEmpty()) {
                tableName = table.name();
            }
        }

        EntityModel entityModel = new EntityModel(clazz.getName(), tableName);

        for (Field field : clazz.getDeclaredFields()) {
            FieldModel fieldModel = parseField(field);
            if (fieldModel != null) {
                entityModel.addField(fieldModel);
            }
        }

        if (clazz.isAnnotationPresent(Index.class)) {
            entityModel.addIndex(parseIndexAnnotation(clazz.getAnnotation(Index.class), null));
        }

        for (Field field : clazz.getDeclaredFields()) {
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
            nullable = column.nullable();
            length = column.length();
            precision = column.precision();
            scale = column.scale();
        }

        return new FieldModel(field, columnName, field.getType(), id, autoIncrement, nullable, unique, length, precision, scale);
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
