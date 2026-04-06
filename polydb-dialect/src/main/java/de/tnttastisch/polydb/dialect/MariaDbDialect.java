package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class MariaDbDialect extends MySqlDialect {

    @Override
    public String getName() {
        return "MariaDB";
    }

    @Override
    public String getSqlType(FieldModel field) {
        String typeName = field.getType().getSimpleName();
        switch (typeName) {
            case "UUID": return "UUID";
            default: return super.getSqlType(field);
        }
    }
}
