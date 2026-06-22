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

/**
 * Discovers {@link Migration} implementations on the classpath via reflection (Reflections library)
 * and returns them ready to run, sorted by version.
 */
public class MigrationScanner {

    /**
     * Scans {@code packageName} for concrete {@link Migration} subtypes, instantiates each via its
     * public no-argument constructor, and returns them sorted ascending by
     * {@link Migration#getVersion() version} so the runner applies them in order.
     *
     * <p>Classes that cannot be instantiated (e.g. abstract types, or those without an accessible
     * no-arg constructor) are silently skipped rather than aborting the scan. Versions are compared
     * as plain strings, so a sortable naming scheme is required for correct ordering.</p>
     *
     * @param packageName the package to scan
     * @return the discovered migrations, sorted by version
     */
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
                // Skip classes that cannot be instantiated (abstract, no public no-arg ctor, etc.).
            }
        }

        // String comparison of versions: relies on a zero-padded/sortable version scheme.
        return migrations.stream()
                .sorted(Comparator.comparing(Migration::getVersion))
                .collect(Collectors.toList());
    }
}
