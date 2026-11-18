package org.nasdanika.sdk.roadmap.models.svg.chart;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.nasdanika.sdk.roadmap.models.core.chart.Border;
import org.nasdanika.sdk.roadmap.models.core.chart.Encoding;
import org.nasdanika.sdk.roadmap.models.core.chart.Scatter;
import org.nasdanika.sdk.roadmap.models.core.chart.Shade;
import org.nasdanika.sdk.roadmap.models.core.chart.ZAxis;
import org.nasdanika.sdk.roadmap.models.svg.chart.Canvas.LegendEntry;
import org.nasdanika.sdk.roadmap.models.svg.chart.Canvas.Marker;

/**
 * The size, the fill and the border that the points of a scatter take from columns, row by row,
 * and the legend that says how to read them.
 */
final class Encodings {

	/** The area of a point without a z axis, in square pixels: a dot of 9 pixels. */
	static final double AREA = 64;

	/** The least and the greatest area of a point when a z axis has no range. */
	private static final int[] RANGE = { 64, 1024 };

	private final Canvas canvas;

	private final Scatter scatter;

	private final String color;

	private final double[] sizes;

	private double sizeFrom;

	private double sizeTo;

	private int[] range = RANGE;

	private final double[] shades;

	private double shadeFrom;

	private double shadeTo;

	private List<String> ramp;

	private final List<String> borders;

	Encodings(Canvas canvas, Scatter scatter, String color) {
		this.canvas = canvas;
		this.scatter = scatter;
		this.color = color;
		ZAxis zAxis = scatter.getZAxis();
		if (zAxis == null) {
			sizes = null;
		} else {
			sizes = canvas.values.numbers(zAxis.getDataKey());
			double[] extent = extent(sizes);
			// By area from zero: twice the value is twice the ink
			sizeFrom = Math.min(0, extent[0]);
			sizeTo = extent[1];
			if (zAxis.getRange().size() == 2) {
				range = new int[] { zAxis.getRange().get(0), zAxis.getRange().get(1) };
			}
		}
		Shade shade = scatter.getShade();
		if (shade == null) {
			shades = null;
		} else {
			shades = canvas.values.numbers(shade.getDataKey());
			double[] extent = extent(shades);
			shadeFrom = extent[0];
			shadeTo = extent[1];
			List<String> theme = canvas.theme.shades();
			ramp = shade.getLow() == null && shade.getHigh() == null
					? theme
					: List.of(shade.getLow() == null ? theme.getFirst() : shade.getLow(), shade.getHigh() == null ? theme.getLast() : shade.getHigh());
		}
		borders = scatter.getBorder() == null ? null : canvas.values.labels(scatter.getBorder().getDataKey());
	}

	private static double[] extent(double[] values) {
		double low = Double.POSITIVE_INFINITY;
		double high = Double.NEGATIVE_INFINITY;
		for (double value : values) {
			if (!Double.isNaN(value)) {
				low = Math.min(low, value);
				high = Math.max(high, value);
			}
		}
		return low > high ? new double[] { 0, 1 } : new double[] { low, high };
	}

	/** Where a value is between two ends, from 0 to 1, or the middle when the ends are one. */
	private static double fraction(double value, double from, double to) {
		return to == from ? 0.5 : Math.clamp((value - from) / (to - from), 0, 1);
	}

	// -- Size --

	private double radiusOf(double value) {
		double area = Double.isNaN(value) ? range[0] : range[0] + (range[1] - range[0]) * fraction(value, sizeFrom, sizeTo);
		return Math.sqrt(area / Math.PI);
	}

	/** The radius of the point of a row. */
	double radius(int row) {
		return sizes == null ? Math.sqrt(AREA / Math.PI) : radiusOf(sizes[row]);
	}

	// -- Shade --

	/** The color a fraction of the way along the ramp, from light to dark. */
	private String shadeOf(double fraction) {
		double position = fraction * (ramp.size() - 1);
		int index = (int) Math.min(Math.floor(position), ramp.size() - 2);
		return mix(ramp.get(index), ramp.get(index + 1), position - index);
	}

	/** The fill of the point of a row: its shade, grey when its value is missing, or the color of the series. */
	String fill(int row) {
		if (shades == null) {
			return color;
		}
		return Double.isNaN(shades[row]) ? canvas.theme.axis() : shadeOf(fraction(shades[row], shadeFrom, shadeTo));
	}

	/** Two colors mixed in sRGB: the first at 0, the second at 1. */
	static String mix(String from, String to, double fraction) {
		int a = Integer.parseInt(from.substring(1), 16);
		int b = Integer.parseInt(to.substring(1), 16);
		int mixed = 0;
		for (int shift = 16; shift >= 0; shift -= 8) {
			int x = a >> shift & 0xff;
			int y = b >> shift & 0xff;
			mixed |= (int) Math.round(x + (y - x) * fraction) << shift;
		}
		return String.format(Locale.ROOT, "#%06x", mixed);
	}

	// -- Border --

	/** Whether the point of a row has a border. */
	boolean border(int row) {
		if (borders == null) {
			return false;
		}
		String value = borders.get(row).trim().toLowerCase(Locale.ROOT);
		return value.equals("true") || value.equals("yes") || value.equals("1");
	}

	String borderColor() {
		Border border = scatter.getBorder();
		return border.getStroke() == null ? canvas.theme.ink() : border.getStroke();
	}

	// -- Words --

	String name(Encoding encoding) {
		return encoding.getName() == null ? canvas.values.label(encoding.getDataKey()) : encoding.getName();
	}

	private static String withUnit(Encoding encoding, String value) {
		return encoding.getUnit() == null ? value : value + encoding.getUnit();
	}

	private static String value(Encoding encoding, double value) {
		return Double.isNaN(value) ? "none" : withUnit(encoding, Canvas.format(value));
	}

	/** The lines of a tooltip for the columns of a row, after its position. */
	List<String[]> tooltip(int row) {
		List<String[]> lines = new ArrayList<>();
		if (sizes != null) {
			lines.add(new String[] { name(scatter.getZAxis()), value(scatter.getZAxis(), sizes[row]) });
		}
		if (shades != null) {
			lines.add(new String[] { name(scatter.getShade()), value(scatter.getShade(), shades[row]) });
		}
		if (borders != null) {
			lines.add(new String[] { name(scatter.getBorder()), border(row) ? "yes" : "no" });
		}
		return lines;
	}

	/**
	 * Legend entries: the least and the greatest point for a size, five steps of a shade with the
	 * values at the ends, and a point with the border.
	 */
	List<LegendEntry> legend() {
		List<LegendEntry> entries = new ArrayList<>();
		if (sizes != null) {
			double[] extent = extent(sizes);
			String ink = canvas.theme.secondaryInk();
			entries.add(LegendEntry.scale(
					name(scatter.getZAxis()),
					value(scatter.getZAxis(), extent[0]),
					List.of(new Marker(radiusOf(extent[0]), "none", ink, 1, null), new Marker(radiusOf(extent[1]), "none", ink, 1, null)),
					value(scatter.getZAxis(), extent[1])));
		}
		if (shades != null) {
			List<Marker> steps = new ArrayList<>();
			for (int step = 0; step < 5; ++step) {
				steps.add(new Marker(5, shadeOf(step / 4.0), null, 0, null));
			}
			entries.add(LegendEntry.scale(
					name(scatter.getShade()),
					value(scatter.getShade(), shadeFrom),
					steps,
					value(scatter.getShade(), shadeTo)));
		}
		if (borders != null) {
			Border border = scatter.getBorder();
			entries.add(LegendEntry.marker(
					name(border),
					new Marker(5, canvas.theme.grid(), borderColor(), border.getStrokeWidth(), border.getStrokeDasharray())));
		}
		return entries;
	}

}
