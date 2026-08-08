package de.tnttastisch.polydb.migration.executor;

import de.tnttastisch.polydb.core.exception.PolyDBException;
import de.tnttastisch.polydb.migration.operation.SqlStatement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An ordered collection of rendered {@link SqlStatement}s, produced for SQL preview / dry-run without
 * touching the database. It can render itself to a {@code .sql} text (bound parameters are appended as
 * a trailing comment, since they are not inlined) and export that to a file.
 */
public final class SqlScript {

    private final List<SqlStatement> statements;

    public SqlScript(List<SqlStatement> statements) {
        this.statements = Collections.unmodifiableList(new ArrayList<>(statements));
    }

    public List<SqlStatement> getStatements() {
        return statements;
    }

    public boolean isEmpty() {
        return statements.isEmpty();
    }

    /** Renders the script as SQL text; each statement is terminated with {@code ;}. */
    public String toSql() {
        StringBuilder sb = new StringBuilder();
        for (SqlStatement st : statements) {
            sb.append(st.getSql());
            if (!st.getSql().stripTrailing().endsWith(";")) {
                sb.append(";");
            }
            if (st.isParameterized()) {
                sb.append("  -- params: ").append(st.getParameters());
            }
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    /** Writes {@link #toSql()} to {@code file}, creating parent directories as needed. */
    public void writeTo(Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, toSql(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PolyDBException("Failed to write SQL script to " + file, e);
        }
    }

    @Override
    public String toString() {
        return toSql();
    }
}
