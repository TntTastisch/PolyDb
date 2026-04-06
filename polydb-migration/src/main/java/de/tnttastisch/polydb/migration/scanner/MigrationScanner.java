package de.tnttastisch.polydb.migration.scanner;

import de.tnttastisch.polydb.migration.core.Migration;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MigrationScanner {

    public List<Migration> scanJavaMigrations(String packageName) {
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .forPackage(packageName)
                .addScanners(Scanners.SubTypes));
        Set<Class<? extends Migration>> classes = reflections.getSubTypesOf(Migration.class);

        List<Migration> migrations = new ArrayList<>();
        for (Class<? extends Migration> clazz : classes) {
            try {
                migrations.add(clazz.getDeclaredConstructor().newInstance());
            } catch (Exception e) {
            }
        }

        return migrations.stream()
                .sorted(Comparator.comparing(Migration::getVersion))
                .collect(Collectors.toList());
    }
}
