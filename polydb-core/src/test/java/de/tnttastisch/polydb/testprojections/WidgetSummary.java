package de.tnttastisch.polydb.testprojections;

/**
 * Record projection: instantiated through its canonical constructor, its components matched by name to
 * entity properties (derived queries) or selected column labels ({@code @Query}).
 */
public record WidgetSummary(String name, int quantity) {
}
