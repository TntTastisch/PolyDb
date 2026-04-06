package de.tnttastisch.polydb.query;

import de.tnttastisch.polydb.schema.model.EntityModel;
import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.parser.EntityParser;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DefaultResultMapper<T> implements ResultMapper<T> {

    private final Class<T> clazz;
    private final EntityModel model;

    public DefaultResultMapper(Class<T> clazz) {
        this.clazz = clazz;
        this.model = new EntityParser().parseEntity(clazz);
    }

    @Override
    public T map(ResultSet rs) throws SQLException {
        try {
            T entity = clazz.getDeclaredConstructor().newInstance();
            for (FieldModel fieldModel : model.getFields()) {
                Field field = fieldModel.getField();
                field.setAccessible(true);
                Object value = rs.getObject(fieldModel.getColumnName());
                
                if (value != null) {
                    field.set(entity, value);
                }
            }
            return entity;
        } catch (Exception e) {
            throw new SQLException("Failed to map ResultSet to " + clazz.getName(), e);
        }
    }
}
