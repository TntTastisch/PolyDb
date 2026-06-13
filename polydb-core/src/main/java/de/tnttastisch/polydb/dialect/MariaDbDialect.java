package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;

public class MariaDbDialect extends MySqlDialect {

    @Override
    public String getName() {
        return "MariaDB";
    }

    @Override
    public String getSqlType(FieldModel field) {
        String typeName = field.getType().getSimpleName();
        switch (typeName) {
            case "UUID":
                return "UUID";
            default:
                return super.getSqlType(field);
        }
    }
}
