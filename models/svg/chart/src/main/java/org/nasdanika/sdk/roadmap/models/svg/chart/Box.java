package org.nasdanika.sdk.roadmap.models.svg.chart;

/**
 * A rectangle in pixels, by its edges.
 */
record Box(double left, double top, double right, double bottom) {

	double width() {
		return right - left;
	}

	double height() {
		return bottom - top;
	}

	double centerX() {
		return (left + right) / 2;
	}

	double centerY() {
		return (top + bottom) / 2;
	}

}
