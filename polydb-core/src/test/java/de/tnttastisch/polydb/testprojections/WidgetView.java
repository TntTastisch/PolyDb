package de.tnttastisch.polydb.testprojections;

import de.tnttastisch.polydb.testentities.Widget;

/**
 * Interface projection over {@link Widget}: a proxy serves each getter from the underlying entity (for
 * derived queries) or row (for {@code @Query}), including the enum {@code status}.
 */
public interface WidgetView {

    String getName();

    int getQuantity();

    Widget.Status getStatus();
}
