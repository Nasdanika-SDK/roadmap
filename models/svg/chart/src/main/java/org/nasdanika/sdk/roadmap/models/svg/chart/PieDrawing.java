package org.nasdanika.sdk.roadmap.models.svg.chart;

import java.util.List;

import org.nasdanika.sdk.roadmap.models.core.chart.Pie;
import org.nasdanika.sdk.roadmap.models.core.chart.PieChart;
import org.nasdanika.sdk.runtime.models.svg.G;
import org.nasdanika.sdk.runtime.models.svg.Path;

/**
 * Pies and rings. Angles are those of Recharts: degrees, zero at three o'clock, counterclockwise.
 */
final class PieDrawing {

	private final Canvas canvas;

	private final PieChart chart;

	PieDrawing(Canvas canvas, PieChart chart) {
		this.canvas = canvas;
		this.chart = chart;
	}

	List<String> names(Pie pie) {
		return canvas.values.labels(pie.getNameKey() == null ? canvas.values.firstColumn() : pie.getNameKey());
	}

	void draw(Box box) {
		boolean labels = chart.getSeries().stream().anyMatch(Pie::isLabel);
		double radius = Math.min(box.width(), box.height()) / 2 - (labels ? canvas.fontSize() * 2 + 8 : 4);
		double cx = box.centerX();
		double cy = box.centerY();
		G group = canvas.group("pies");
		for (Pie pie : chart.getSeries()) {
			List<String> names = names(pie);
			double[] values = canvas.values.numbers(pie.getDataKey());
			double outer = length(pie.getOuterRadius(), radius);
			double inner = length(pie.getInnerRadius(), radius);
			double total = 0;
			int nonZero = 0;
			for (double value : values) {
				if (value > 0) {
					total += value;
					++nonZero;
				}
			}
			double sweep = pie.getEndAngle() - pie.getStartAngle();
			double sign = Math.signum(sweep);
			double padding = pie.getPaddingAngle();
			double available = Math.abs(sweep) - padding * (Math.abs(sweep) >= 360 ? nonZero : nonZero - 1);
			double angle = pie.getStartAngle();
			for (int row = 0; row < values.length; ++row) {
				if (!(values[row] > 0)) {
					continue;
				}
				double end = angle + sign * values[row] / total * available;
				Path slice = canvas.path(group, slice(cx, cy, inner, outer, angle, end));
				slice.setFill(Canvas.cellFill(pie.getCells(), names.get(row), canvas.color(row, null)));
				// A gap in the surface color between slices
				slice.setStroke(canvas.theme.surface());
				slice.setStrokeWidth("2");
				slice.setStrokeLinejoin("round");
				canvas.tooltip(slice, null, names.get(row), Canvas.format(values[row]));
				if (pie.isLabel()) {
					double middle = Math.toRadians((angle + end) / 2);
					double x = cx + (outer + 8) * Math.cos(middle);
					double y = cy - (outer + 8) * Math.sin(middle);
					String text = names.get(row) + " " + Canvas.format(values[row]);
					canvas.centeredText(group, x, y, text, Math.cos(middle) >= 0 ? "start" : "end", canvas.theme.ink());
				}
				angle = end + sign * padding;
			}
		}
	}

	private static double length(String spec, double radius) {
		String trimmed = spec == null ? "0" : spec.trim();
		return trimmed.endsWith("%")
				? radius * Double.parseDouble(trimmed.substring(0, trimmed.length() - 1)) / 100
				: Double.parseDouble(trimmed);
	}

	/** A slice of a pie, or of a ring when the inner radius is greater than zero. */
	static String slice(double cx, double cy, double inner, double outer, double start, double end) {
		if (Math.abs(end - start) >= 359.99) {
			// An arc cannot end where it starts: a whole circle is two halves
			double middle = (start + end) / 2;
			return slice(cx, cy, inner, outer, start, middle) + " " + slice(cx, cy, inner, outer, middle, end);
		}
		String large = Math.abs(end - start) > 180 ? "1" : "0";
		// Angles grow counterclockwise and SVG's y grows down, so a growing angle is sweep 0
		String forward = end > start ? "0" : "1";
		String backward = end > start ? "1" : "0";
		StringBuilder d = new StringBuilder("M ").append(point(cx, cy, outer, start));
		d.append(" A ").append(Canvas.number(outer)).append(" ").append(Canvas.number(outer))
				.append(" 0 ").append(large).append(" ").append(forward).append(" ").append(point(cx, cy, outer, end));
		if (inner > 0) {
			d.append(" L ").append(point(cx, cy, inner, end));
			d.append(" A ").append(Canvas.number(inner)).append(" ").append(Canvas.number(inner))
					.append(" 0 ").append(large).append(" ").append(backward).append(" ").append(point(cx, cy, inner, start));
		} else {
			d.append(" L ").append(Canvas.number(cx)).append(" ").append(Canvas.number(cy));
		}
		return d.append(" Z").toString();
	}

	private static String point(double cx, double cy, double radius, double degrees) {
		double radians = Math.toRadians(degrees);
		return Canvas.number(cx + radius * Math.cos(radians)) + " " + Canvas.number(cy - radius * Math.sin(radians));
	}

}
