package org.nasdanika.sdk.roadmap.models.svg.chart;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

import org.nasdanika.sdk.roadmap.models.core.chart.Area;
import org.nasdanika.sdk.roadmap.models.core.chart.Bar;
import org.nasdanika.sdk.roadmap.models.core.chart.Cell;
import org.nasdanika.sdk.roadmap.models.core.chart.Chart;
import org.nasdanika.sdk.roadmap.models.core.chart.Line;
import org.nasdanika.sdk.roadmap.models.core.chart.Scatter;
import org.nasdanika.sdk.roadmap.models.core.chart.Series;
import org.nasdanika.sdk.roadmap.models.core.chart.Side;
import org.nasdanika.sdk.runtime.models.svg.Circle;
import org.nasdanika.sdk.runtime.models.svg.Container;
import org.nasdanika.sdk.runtime.models.svg.Desc;
import org.nasdanika.sdk.runtime.models.svg.G;
import org.nasdanika.sdk.runtime.models.svg.Path;
import org.nasdanika.sdk.runtime.models.svg.Rect;
import org.nasdanika.sdk.runtime.models.svg.Svg;
import org.nasdanika.sdk.runtime.models.svg.SvgFactory;
import org.nasdanika.sdk.runtime.models.svg.Text;
import org.nasdanika.sdk.runtime.models.svg.Title;

/**
 * What the drawings of all kinds of charts share: the SVG element being written, the theme, the
 * data, colors, text, tooltips and the legend.
 */
final class Canvas {

	static final SvgFactory SVG = SvgFactory.eINSTANCE;

	/** Text is measured by an average width of a character, as a fraction of the font size. */
	private static final double CHARACTER_WIDTH = 0.56;

	enum Mark {
		BOX, LINE, DOT,
		/** A marker, then the name: a border. */
		MARKER,
		/** The name, then markers from the least value to the greatest, with the values at the ends: a size or a shade. */
		SCALE
	}

	/** A circle in a legend. A stroke width of zero is no stroke. */
	record Marker(double radius, String fill, String stroke, int strokeWidth, String dasharray) {
	}

	record LegendEntry(String name, String color, Mark mark, List<Marker> markers, String low, String high) {

		LegendEntry(String name, String color, Mark mark) {
			this(name, color, mark, List.of(), null, null);
		}

		static LegendEntry marker(String name, Marker marker) {
			return new LegendEntry(name, null, Mark.MARKER, List.of(marker), null, null);
		}

		static LegendEntry scale(String name, String low, List<Marker> markers, String high) {
			return new LegendEntry(name, null, Mark.SCALE, markers, low, high);
		}

	}

	final ChartTheme theme;

	final Chart<?> chart;

	final Values values;

	final Svg svg;

	Canvas(ChartTheme theme, Chart<?> chart) {
		this.theme = theme;
		this.chart = chart;
		values = new Values(chart.getData());
		svg = SVG.createSvg();
		svg.setWidth(number(chart.getWidth()));
		svg.setHeight(number(chart.getHeight()));
		svg.setViewBox("0 0 " + chart.getWidth() + " " + chart.getHeight());
		svg.setFontFamily(theme.fontFamily());
		svg.setFontSize(number(theme.fontSize()));
		if (chart.getTitle() != null) {
			Title title = SVG.createTitle();
			title.setValue(chart.getTitle());
			svg.add(title);
		}
		if (chart.getAlternativeText() != null) {
			Desc desc = SVG.createDesc();
			desc.setValue(chart.getAlternativeText());
			svg.add(desc);
		}
	}

	double fontSize() {
		return theme.fontSize();
	}

	// -- Colors --

	/** A color of its own, or the chart's color for the index, or the theme's. */
	String color(int index, String own) {
		if (own != null) {
			return own;
		}
		List<String> colors = chart.getColors().isEmpty() ? theme.palette() : chart.getColors();
		return colors.get(index % colors.size());
	}

	String color(Series series, int index) {
		return switch (series) {
			case Line line -> color(index, line.getStroke());
			case Area area -> color(index, area.getStroke() == null ? area.getFill() : area.getStroke());
			case Bar bar -> color(index, bar.getFill());
			case Scatter scatter -> color(index, scatter.getFill());
			default -> color(index, null);
		};
	}

	/** The fill of the cell for a category, if there is one. */
	static String cellFill(List<Cell> cells, String category, String otherwise) {
		return cells.stream()
				.filter(cell -> category.equals(cell.getCategory()) && cell.getFill() != null)
				.map(Cell::getFill)
				.findFirst()
				.orElse(otherwise);
	}

	String seriesName(Series series) {
		return series.getName() == null ? values.label(series.getDataKey()) : series.getName();
	}

	// -- Elements --

	G group(String styleClass) {
		G group = SVG.createG();
		group.setStyleClass(styleClass);
		return svg.add(group);
	}

	Path path(Container parent, String d) {
		Path path = SVG.createPath();
		path.setD(d);
		return parent.add(path);
	}

	/** A line of one pixel, as a path. */
	Path rule(Container parent, double x1, double y1, double x2, double y2, String stroke, String dasharray) {
		Path path = path(parent, "M " + number(x1) + " " + number(y1) + " L " + number(x2) + " " + number(y2));
		path.setStroke(stroke);
		path.setStrokeWidth("1");
		path.setStrokeDasharray(dasharray);
		path.setFill("none");
		return path;
	}

	/** A marker filled with a color and ringed with the surface, so it stays legible where it crosses a line. */
	Circle dot(Container parent, double cx, double cy, double r, String fill) {
		Circle circle = SVG.createCircle();
		circle.setCx(round(cx));
		circle.setCy(round(cy));
		circle.setR(r);
		circle.setFill(fill);
		circle.setStroke(theme.surface());
		circle.setStrokeWidth("2");
		return parent.add(circle);
	}

	Text text(Container parent, double x, double y, String value, String anchor, String fill) {
		Text text = SVG.createText();
		text.setX(number(x));
		text.setY(number(y));
		text.setTextAnchor(anchor);
		text.setFill(fill);
		text.addText(value);
		return parent.add(text);
	}

	/** Text centered on a point vertically: SVG places text by its baseline. */
	Text centeredText(Container parent, double x, double y, String value, String anchor, String fill) {
		return text(parent, x, y + fontSize() * 0.35, value, anchor, fill);
	}

	Text rotatedText(Container parent, double x, double y, String value, int degrees) {
		Text text = centeredText(parent, x, y, value, "middle", theme.secondaryInk());
		text.setTransform("rotate(" + degrees + " " + number(x) + " " + number(y) + ")");
		return text;
	}

	/** A title on a mark is its tooltip in a browser, when the chart asks for tooltips. */
	void tooltip(Container mark, String category, String name, String value) {
		if (chart.getTooltip() != null) {
			Title title = SVG.createTitle();
			String separator = chart.getTooltip().getSeparator() == null ? " : " : chart.getTooltip().getSeparator();
			title.setValue((category == null ? "" : category + ", ") + name + separator + value);
			mark.add(title);
		}
	}

	/** A tooltip of several lines: a heading, then a name and a value on each. */
	void tooltip(Container mark, String heading, List<String[]> lines) {
		if (chart.getTooltip() != null) {
			String separator = chart.getTooltip().getSeparator() == null ? " : " : chart.getTooltip().getSeparator();
			StringBuilder value = new StringBuilder(heading);
			for (String[] line : lines) {
				value.append('\n').append(line[0]).append(separator).append(line[1]);
			}
			Title title = SVG.createTitle();
			title.setValue(value.toString());
			mark.add(title);
		}
	}

	double textWidth(String text) {
		return text.length() * fontSize() * CHARACTER_WIDTH;
	}

	// -- Legend --

	/**
	 * Draws the legend, if the chart has one, at its side of the box.
	 *
	 * @return What is left of the box
	 */
	Box legend(List<LegendEntry> entries, Box box) {
		if (chart.getLegend() == null || entries.isEmpty()) {
			return box;
		}
		G legend = group("legend");
		Side position = chart.getLegend().getPosition();
		double rowHeight = entries.stream()
				.flatMap(entry -> entry.markers().stream())
				.mapToDouble(marker -> 2 * marker.radius() + 4)
				.reduce(fontSize() + 8, Math::max);
		double gap = 16;
		if (position == Side.TOP || position == Side.BOTTOM) {
			double total = entries.stream().mapToDouble(entry -> entryWidth(entry) + gap).sum() - gap;
			double x = box.centerX() - total / 2;
			double y = position == Side.TOP ? box.top() + rowHeight / 2 : box.bottom() - rowHeight / 2;
			for (LegendEntry entry : entries) {
				entry(legend, entry, x, y);
				x += entryWidth(entry) + gap;
			}
			return position == Side.TOP
					? new Box(box.left(), box.top() + rowHeight + 8, box.right(), box.bottom())
					: new Box(box.left(), box.top(), box.right(), box.bottom() - rowHeight - 8);
		}
		double width = entries.stream().mapToDouble(this::entryWidth).max().orElse(0);
		double x = position == Side.LEFT ? box.left() : box.right() - width;
		double y = box.centerY() - rowHeight * (entries.size() - 1) / 2;
		for (LegendEntry entry : entries) {
			entry(legend, entry, x, y);
			y += rowHeight;
		}
		return position == Side.LEFT
				? new Box(box.left() + width + gap, box.top(), box.right(), box.bottom())
				: new Box(box.left(), box.top(), box.right() - width - gap, box.bottom());
	}

	private double swatchWidth(LegendEntry entry) {
		return entry.mark() == Mark.LINE ? 20 : 16;
	}

	private static double markersWidth(LegendEntry entry) {
		return entry.markers().stream().mapToDouble(marker -> 2 * marker.radius() + 4).sum();
	}

	private double entryWidth(LegendEntry entry) {
		return switch (entry.mark()) {
			case MARKER -> markersWidth(entry) + 2 + textWidth(entry.name());
			case SCALE -> textWidth(entry.name()) + 8 + textWidth(entry.low()) + 4 + markersWidth(entry) + 2 + textWidth(entry.high());
			default -> swatchWidth(entry) + textWidth(entry.name());
		};
	}

	/** Markers side by side, centered on a line, from x. */
	private double markers(Container legend, List<Marker> markers, double x, double y) {
		for (Marker marker : markers) {
			Circle circle = SVG.createCircle();
			circle.setCx(round(x + 2 + marker.radius()));
			circle.setCy(round(y));
			circle.setR(round(marker.radius()));
			circle.setFill(marker.fill());
			if (marker.strokeWidth() > 0) {
				circle.setStroke(marker.stroke());
				circle.setStrokeWidth(Integer.toString(marker.strokeWidth()));
				circle.setStrokeDasharray(marker.dasharray());
			}
			legend.add(circle);
			x += 2 * marker.radius() + 4;
		}
		return x;
	}

	private void entry(Container legend, LegendEntry entry, double x, double y) {
		String ink = theme.secondaryInk();
		switch (entry.mark()) {
			case MARKER -> {
				double end = markers(legend, entry.markers(), x, y);
				centeredText(legend, end + 2, y, entry.name(), "start", ink);
				return;
			}
			case SCALE -> {
				centeredText(legend, x, y, entry.name(), "start", ink);
				x += textWidth(entry.name()) + 8;
				centeredText(legend, x, y, entry.low(), "start", ink);
				x = markers(legend, entry.markers(), x + textWidth(entry.low()) + 4, y);
				centeredText(legend, x + 2, y, entry.high(), "start", ink);
				return;
			}
			default -> {
				// A swatch, below
			}
		}
		switch (entry.mark()) {
			case BOX -> {
				Rect swatch = SVG.createRect();
				swatch.setX(round(x));
				swatch.setY(round(y - 5));
				swatch.setWidth(10);
				swatch.setHeight(10);
				swatch.setRx(2);
				swatch.setFill(entry.color());
				legend.add(swatch);
			}
			case LINE -> {
				Path line = path(legend, "M " + number(x) + " " + number(y) + " L " + number(x + 14) + " " + number(y));
				line.setStroke(entry.color());
				line.setStrokeWidth("2");
				line.setStrokeLinecap("round");
				dot(legend, x + 7, y, 3, entry.color());
			}
			case DOT -> dot(legend, x + 5, y, 4.5, entry.color());
			default -> throw new IllegalStateException(entry.mark().name());
		}
		centeredText(legend, x + swatchWidth(entry), y, entry.name(), "start", ink);
	}

	// -- Numbers --

	/** A value as a reader sees it: grouped thousands, no trailing zeros. */
	static String format(double value) {
		DecimalFormat format = new DecimalFormat("#,##0.######", DecimalFormatSymbols.getInstance(Locale.ROOT));
		return format.format(value == 0 ? 0 : value);
	}

	/** A coordinate as SVG gets it: a tenth of a pixel is enough. */
	static String number(double value) {
		double rounded = round(value);
		return rounded == Math.rint(rounded) ? Long.toString((long) rounded) : Double.toString(rounded);
	}

	static double round(double value) {
		return Math.round(value * 10) / 10.0;
	}

}
