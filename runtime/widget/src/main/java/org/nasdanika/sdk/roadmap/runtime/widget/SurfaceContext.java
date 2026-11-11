package org.nasdanika.sdk.roadmap.runtime.widget;

/**
 * A context that is an area: a rectangle that widgets draw into and divide among the widgets
 * inside them. The terminal is one case, with cells as its unit, and a Draw.io page is another,
 * with pixels. Coordinates are doubles so that both fit, as they do in AWT's and Draw.io's
 * rectangles.
 *
 * <p>
 * Only what every surface has is here: the size, and deriving a context for a part of the area.
 * Drawing belongs to the contexts below, because a terminal draws text into cells and a diagram
 * draws shapes.
 */
// tag::surface[]
public interface SurfaceContext extends RenderContext {

	double getWidth();

	double getHeight();

	/**
	 * A context for a part of this area, with coordinates relative to this area. The part is clipped
	 * to this area: a widget cannot draw outside what it was given.
	 */
	SurfaceContext area(double x, double y, double width, double height);

}
// end::surface[]
