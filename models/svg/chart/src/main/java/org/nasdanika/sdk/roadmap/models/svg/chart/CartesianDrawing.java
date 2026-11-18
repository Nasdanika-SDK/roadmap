package org.nasdanika.sdk.roadmap.models.svg.chart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.nasdanika.sdk.roadmap.models.core.chart.Area;
import org.nasdanika.sdk.roadmap.models.core.chart.Axis;
import org.nasdanika.sdk.roadmap.models.core.chart.AxisType;
import org.nasdanika.sdk.roadmap.models.core.chart.Bar;
import org.nasdanika.sdk.roadmap.models.core.chart.BarSpacing;
import org.nasdanika.sdk.roadmap.models.core.chart.Border;
import org.nasdanika.sdk.roadmap.models.core.chart.CartesianChart;
import org.nasdanika.sdk.roadmap.models.core.chart.CartesianGrid;
import org.nasdanika.sdk.roadmap.models.core.chart.CartesianSeries;
import org.nasdanika.sdk.roadmap.models.core.chart.ChartFactory;
import org.nasdanika.sdk.roadmap.models.core.chart.Curve;
import org.nasdanika.sdk.roadmap.models.core.chart.Layout;
import org.nasdanika.sdk.roadmap.models.core.chart.Line;
import org.nasdanika.sdk.roadmap.models.core.chart.ReferenceLine;
import org.nasdanika.sdk.roadmap.models.core.chart.Scatter;
import org.nasdanika.sdk.roadmap.models.core.chart.Shape;
import org.nasdanika.sdk.roadmap.models.core.chart.Side;
import org.nasdanika.sdk.roadmap.models.core.chart.XAxis;
import org.nasdanika.sdk.roadmap.models.core.chart.YAxis;
import org.nasdanika.sdk.runtime.models.svg.Circle;
import org.nasdanika.sdk.runtime.models.svg.Container;
import org.nasdanika.sdk.runtime.models.svg.G;
import org.nasdanika.sdk.runtime.models.svg.Path;

/**
 * A chart on x and y axes. Geometry is worked out in two coordinates, along the categories and
 * along the values, and turned into x and y last, so a vertical layout is a swap and not a second
 * drawing.
 *
 * Not drawn yet: logarithmic axes (drawn linear), monotone curves (drawn straight), steps of areas,
 * time axes (read as numbers), and the cross, star and wye shapes of a scatter (drawn as circles).
 */
final class CartesianDrawing {

	/** The thickest a bar is drawn: a bar is a mark, not a block. */
	private static final double MAX_BAR = 24;

	private final Canvas canvas;

	private final CartesianChart<?> chart;

	private final boolean vertical;

	private final XAxis xAxis;

	private final boolean numericX;

	private final List<String> categories;

	private final double[] xValues;

	private final List<YAxis> yAxes;

	private final List<? extends CartesianSeries> series;

	/** For each series and row: where the mark starts and where it ends, in values. */
	private final Map<CartesianSeries, double[][]> bounds = new HashMap<>();

	/** For each series and row: whether the mark is the outer end of its stack, which is rounded. */
	private final Map<CartesianSeries, boolean[]> outermost = new HashMap<>();

	private final List<Ticks> valueTicks = new ArrayList<>();

	private Ticks xTicks;

	private Box plot;

	private double categorySpace;

	private double nearSpace;

	private double farSpace;

	CartesianDrawing(Canvas canvas, CartesianChart<?> chart) {
		this.canvas = canvas;
		this.chart = chart;
		vertical = chart.getLayout() == Layout.VERTICAL;
		xAxis = chart.getXAxis() == null ? ChartFactory.eINSTANCE.createXAxis() : chart.getXAxis();
		numericX = xAxis.getType() != AxisType.CATEGORY;
		String categoryKey = xAxis.getDataKey() == null ? canvas.values.firstColumn() : xAxis.getDataKey();
		categories = canvas.values.labels(categoryKey);
		xValues = canvas.values.numbers(categoryKey);
		yAxes = chart.getYAxes().isEmpty() ? List.of(ChartFactory.eINSTANCE.createYAxis()) : chart.getYAxes();
		series = chart.getSeries();
		stack();
		domains();
	}

	// -- Layout --

	private int axis(CartesianSeries series) {
		return axisIndex(series.getYAxis());
	}

	/** The index of an axis, the first when none is named. */
	private int axisIndex(YAxis axis) {
		return axis == null ? 0 : Math.max(0, yAxes.indexOf(axis));
	}

	private static String stackId(CartesianSeries series) {
		return switch (series) {
			case Bar bar -> bar.getStackId();
			case Area area -> area.getStackId();
			default -> null;
		};
	}

	/** Stacked marks start where the one before them in the stack ended: positive and negative values apart. */
	private void stack() {
		int rows = categories.size();
		Map<String, double[]> sums = new HashMap<>();
		Map<String, CartesianSeries[]> tops = new HashMap<>();
		for (CartesianSeries each : series) {
			double[] values = canvas.values.numbers(each.getDataKey());
			String stackId = stackId(each);
			double[][] extents = new double[rows][];
			for (int row = 0; row < rows; ++row) {
				double value = values[row];
				if (Double.isNaN(value)) {
					extents[row] = new double[] { Double.NaN, Double.NaN };
				} else if (stackId == null) {
					extents[row] = new double[] { 0, value };
				} else {
					String key = stackKey(each, value);
					double[] sum = sums.computeIfAbsent(key, k -> new double[rows]);
					extents[row] = new double[] { sum[row], sum[row] + value };
					sum[row] += value;
					tops.computeIfAbsent(key, k -> new CartesianSeries[rows])[row] = each;
				}
			}
			bounds.put(each, extents);
		}
		for (CartesianSeries each : series) {
			boolean[] outer = new boolean[rows];
			double[][] extents = bounds.get(each);
			for (int row = 0; row < rows; ++row) {
				double value = extents[row][1] - extents[row][0];
				outer[row] = stackId(each) == null || Double.isNaN(value) || tops.get(stackKey(each, value))[row] == each;
			}
			outermost.put(each, outer);
		}
	}

	private String stackKey(CartesianSeries series, double value) {
		return axis(series) + "/" + series.eClass().getName() + "/" + stackId(series) + (value < 0 ? "/-" : "/+");
	}

	private void domains() {
		for (int index = 0; index < yAxes.size(); ++index) {
			double low = Double.POSITIVE_INFINITY;
			double high = Double.NEGATIVE_INFINITY;
			for (CartesianSeries each : series) {
				if (axis(each) == index) {
					boolean fromBase = each instanceof Bar || each instanceof Area;
					for (double[] extent : bounds.get(each)) {
						for (double value : fromBase ? extent : new double[] { extent[1] }) {
							if (!Double.isNaN(value)) {
								low = Math.min(low, value);
								high = Math.max(high, value);
							}
						}
					}
				}
			}
			for (ReferenceLine line : chart.getReferenceLines()) {
				double value = Values.number(line.getY());
				if (!Double.isNaN(value) && axisIndex(line.getYAxis()) == index) {
					low = Math.min(low, value);
					high = Math.max(high, value);
				}
			}
			valueTicks.add(ticks(yAxes.get(index), low, high));
		}
		if (numericX) {
			double low = Double.POSITIVE_INFINITY;
			double high = Double.NEGATIVE_INFINITY;
			for (double value : xValues) {
				if (!Double.isNaN(value)) {
					low = Math.min(low, value);
					high = Math.max(high, value);
				}
			}
			xTicks = ticks(xAxis, low, high);
		}
	}

	/** The range of an axis, as Recharts has it: from zero when the minimum is not given, to a tick above the data. */
	private static Ticks ticks(Axis axis, double low, double high) {
		if (low > high) {
			low = 0;
			high = 1;
		}
		String min = axis.getMin();
		String max = axis.getMax();
		boolean roundLow = min == null || "auto".equals(min);
		double start = min == null ? Math.min(0, low) : "auto".equals(min) || "dataMin".equals(min) ? low : Double.parseDouble(min);
		boolean roundHigh = max == null || "auto".equals(max);
		double end = max == null || "auto".equals(max) || "dataMax".equals(max) ? high : Double.parseDouble(max);
		return Ticks.of(start, end, roundLow, roundHigh, 5);
	}

	private static boolean far(YAxis axis) {
		return axis.getOrientation() == Side.RIGHT || axis.getOrientation() == Side.TOP;
	}

	private String unit(Axis axis) {
		return axis.getUnit() == null ? "" : axis.getUnit();
	}

	private double tickWidth(int index) {
		YAxis axis = yAxes.get(index);
		return valueTicks.get(index).values().stream()
				.mapToDouble(tick -> canvas.textWidth(Canvas.format(tick) + unit(axis)))
				.max()
				.orElse(0);
	}

	private Box plot(Box box) {
		double fontSize = canvas.fontSize();
		for (int index = 0; index < yAxes.size(); ++index) {
			YAxis axis = yAxes.get(index);
			if (!axis.isHidden()) {
				double space = axis.getWidth() > 0
						? axis.getWidth()
						: (vertical ? fontSize : tickWidth(index)) + 8 + (axis.getLabel() == null ? 0 : fontSize + 8);
				if (far(axis)) {
					farSpace = Math.max(farSpace, space);
				} else {
					nearSpace = Math.max(nearSpace, space);
				}
			}
		}
		if (!xAxis.isHidden()) {
			double labels = categories.stream().mapToDouble(canvas::textWidth).max().orElse(0);
			categorySpace = (vertical ? labels : fontSize) + 8 + (xAxis.getLabel() == null ? 0 : fontSize + 8);
		}
		if (vertical) {
			return new Box(
					box.left() + categorySpace,
					box.top() + Math.max(farSpace, fontSize / 2),
					box.right() - 16,
					box.bottom() - nearSpace);
		}
		return new Box(
				box.left() + nearSpace,
				box.top() + fontSize / 2 + 4,
				box.right() - Math.max(farSpace, 16),
				box.bottom() - categorySpace);
	}

	// -- Coordinates --

	private double categoryStart() {
		return vertical ? plot.top() : plot.left();
	}

	private double categoryEnd() {
		return vertical ? plot.bottom() : plot.right();
	}

	private double band() {
		return (categoryEnd() - categoryStart()) / Math.max(1, categories.size());
	}

	/** The middle of the band of a category, or the position of the x value of a row on a numeric axis. */
	private double category(int row) {
		if (numericX) {
			return xTicks.position(xValues[row], categoryStart(), categoryEnd());
		}
		return categoryStart() + band() * (row + 0.5);
	}

	private String categoryLabel(int row) {
		return numericX ? Canvas.format(xValues[row]) : categories.get(row);
	}

	private double value(int axis, double value) {
		Ticks ticks = valueTicks.get(axis);
		return vertical
				? ticks.position(ticks.clamp(value), plot.left(), plot.right())
				: ticks.position(ticks.clamp(value), plot.bottom(), plot.top());
	}

	private double x(double category, double value) {
		return vertical ? value : category;
	}

	private double y(double category, double value) {
		return vertical ? category : value;
	}

	private String point(double category, double value) {
		return Canvas.number(x(category, value)) + " " + Canvas.number(y(category, value));
	}

	private void rule(Container parent, double c1, double v1, double c2, double v2, String stroke, String dasharray) {
		canvas.rule(parent, x(c1, v1), y(c1, v1), x(c2, v2), y(c2, v2), stroke, dasharray);
	}

	// -- Drawing --

	void draw(Box box) {
		plot = plot(box);
		grid();
		axes();
		areas();
		bars();
		lines();
		scatters();
		referenceLines();
	}

	private void grid() {
		CartesianGrid grid = chart.getGrid();
		if (grid == null) {
			return;
		}
		G group = canvas.group("grid");
		String color = canvas.theme.grid();
		if (vertical ? grid.isVertical() : grid.isHorizontal()) {
			for (double tick : valueTicks.getFirst().values()) {
				double value = value(0, tick);
				rule(group, categoryStart(), value, categoryEnd(), value, color, grid.getStrokeDasharray());
			}
		}
		if (vertical ? grid.isHorizontal() : grid.isVertical()) {
			Ticks ticks = valueTicks.getFirst();
			List<Double> positions = new ArrayList<>();
			if (numericX) {
				xTicks.values().forEach(tick -> positions.add(xTicks.position(tick, categoryStart(), categoryEnd())));
			} else {
				for (int row = 0; row < categories.size(); ++row) {
					positions.add(category(row));
				}
			}
			for (double position : positions) {
				rule(group, position, value(0, ticks.min()), position, value(0, ticks.max()), color, grid.getStrokeDasharray());
			}
		}
	}

	private void axes() {
		G group = canvas.group("axes");
		double fontSize = canvas.fontSize();
		String ink = canvas.theme.secondaryInk();
		if (!xAxis.isHidden()) {
			double edge = vertical ? plot.left() : plot.bottom();
			rule(group, categoryStart(), edge, categoryEnd(), edge, canvas.theme.axis(), null);
			Map<Double, String> labels = new LinkedHashMap<>();
			if (numericX) {
				for (double tick : xTicks.values()) {
					labels.put(xTicks.position(tick, categoryStart(), categoryEnd()), Canvas.format(tick) + unit(xAxis));
				}
			} else {
				for (int row = 0; row < categories.size(); ++row) {
					labels.put(category(row), categories.get(row));
				}
			}
			labels.forEach((position, label) -> {
				if (vertical) {
					canvas.centeredText(group, plot.left() - 8, position, label, "end", ink);
				} else {
					canvas.text(group, position, plot.bottom() + fontSize + 4, label, "middle", ink);
				}
			});
			if (xAxis.getLabel() != null) {
				if (vertical) {
					canvas.rotatedText(group, plot.left() - categorySpace + fontSize * 0.6, plot.centerY(), xAxis.getLabel(), -90);
				} else {
					canvas.text(group, plot.centerX(), plot.bottom() + 2 * fontSize + 12, xAxis.getLabel(), "middle", ink);
				}
			}
		}
		for (int index = 0; index < yAxes.size(); ++index) {
			YAxis axis = yAxes.get(index);
			if (axis.isHidden()) {
				continue;
			}
			boolean far = far(axis);
			for (double tick : valueTicks.get(index).values()) {
				double position = value(index, tick);
				String label = Canvas.format(tick) + unit(axis);
				if (vertical) {
					canvas.text(group, position, far ? plot.top() - 8 : plot.bottom() + fontSize + 4, label, "middle", ink);
				} else if (far) {
					canvas.centeredText(group, plot.right() + 8, position, label, "start", ink);
				} else {
					canvas.centeredText(group, plot.left() - 8, position, label, "end", ink);
				}
			}
			if (axis.getLabel() != null) {
				if (vertical) {
					double y = far ? plot.top() - fontSize - 16 : plot.bottom() + 2 * fontSize + 12;
					canvas.text(group, plot.centerX(), y, axis.getLabel(), "middle", ink);
				} else {
					double x = far ? plot.right() + farSpace - fontSize * 0.6 : plot.left() - nearSpace + fontSize * 0.6;
					canvas.rotatedText(group, x, plot.centerY(), axis.getLabel(), far ? 90 : -90);
				}
			}
		}
	}

	private void bars() {
		List<Bar> bars = series.stream().filter(Bar.class::isInstance).map(Bar.class::cast).toList();
		if (bars.isEmpty()) {
			return;
		}
		Map<String, Integer> groups = new LinkedHashMap<>();
		for (Bar bar : bars) {
			groups.putIfAbsent(bar.getStackId() == null ? "bar " + series.indexOf(bar) : stackKey(bar, 1), groups.size());
		}
		double band = numericX ? 2 * MAX_BAR : band();
		BarSpacing spacing = chart instanceof BarSpacing barSpacing ? barSpacing : null;
		double categoryGap = length(spacing == null ? "10%" : spacing.getBarCategoryGap(), band);
		double barGap = length(spacing == null ? "4" : spacing.getBarGap(), band);
		int count = groups.size();
		double inner = band - categoryGap;
		double slot = (inner - barGap * (count - 1)) / count;
		double thickness = Math.max(1, Math.min(MAX_BAR, slot));
		G group = canvas.group("bars");
		for (Bar bar : bars) {
			int index = series.indexOf(bar);
			int axis = axis(bar);
			String color = canvas.color(bar, index);
			String name = canvas.seriesName(bar);
			int position = groups.get(bar.getStackId() == null ? "bar " + index : stackKey(bar, 1));
			double offset = -inner / 2 + position * (slot + barGap) + (slot - thickness) / 2;
			double[][] extents = bounds.get(bar);
			boolean[] outer = outermost.get(bar);
			for (int row = 0; row < extents.length; ++row) {
				if (Double.isNaN(extents[row][1])) {
					continue;
				}
				double start = category(row) + offset;
				double base = value(axis, extents[row][0]);
				double end = value(axis, extents[row][1]);
				double direction = Math.signum(end - base);
				double length = Math.abs(end - base);
				if (length == 0) {
					continue;
				}
				if (extents[row][0] != 0 && length > 2) {
					// A gap in the surface color between stacked segments
					base += 2 * direction;
					length -= 2;
				}
				double radius = outer[row] ? Math.min(4, Math.min(thickness / 2, length)) : 0;
				Path path = canvas.path(group, barPath(start, start + thickness, base, end, radius, direction));
				path.setFill(Canvas.cellFill(bar.getCells(), categoryLabel(row), color));
				double value = extents[row][1] - extents[row][0];
				canvas.tooltip(path, categoryLabel(row), name, Canvas.format(value) + unit(yAxes.get(axis)));
			}
		}
	}

	/** A bar, square at its base and rounded at its end. */
	private String barPath(double start, double end, double base, double top, double radius, double direction) {
		return "M " + point(start, base)
				+ " L " + point(start, top - direction * radius)
				+ " Q " + point(start, top) + " " + point(start + radius, top)
				+ " L " + point(end - radius, top)
				+ " Q " + point(end, top) + " " + point(end, top - direction * radius)
				+ " L " + point(end, base) + " Z";
	}

	/** A length in pixels, or a percentage of a whole. */
	private static double length(String spec, double whole) {
		if (spec == null || spec.isBlank()) {
			return 0;
		}
		String trimmed = spec.trim();
		return trimmed.endsWith("%")
				? whole * Double.parseDouble(trimmed.substring(0, trimmed.length() - 1)) / 100
				: Double.parseDouble(trimmed);
	}

	/** The rows with values, split where a value is missing: a line has a gap there. */
	private List<List<Integer>> runs(double[][] extents) {
		List<List<Integer>> runs = new ArrayList<>();
		List<Integer> run = new ArrayList<>();
		for (int row = 0; row < extents.length; ++row) {
			if (Double.isNaN(extents[row][1])) {
				if (!run.isEmpty()) {
					runs.add(run);
					run = new ArrayList<>();
				}
			} else {
				run.add(row);
			}
		}
		if (!run.isEmpty()) {
			runs.add(run);
		}
		return runs;
	}

	private String trace(List<Integer> run, double[][] extents, int axis, Curve curve) {
		StringBuilder d = new StringBuilder();
		double previousCategory = 0;
		double previousValue = 0;
		for (int row : run) {
			double category = category(row);
			double value = value(axis, extents[row][1]);
			if (d.isEmpty()) {
				d.append("M ").append(point(category, value));
			} else if (curve == Curve.STEP) {
				double middle = (previousCategory + category) / 2;
				d.append(" L ").append(point(middle, previousValue));
				d.append(" L ").append(point(middle, value));
				d.append(" L ").append(point(category, value));
			} else {
				d.append(" L ").append(point(category, value));
			}
			previousCategory = category;
			previousValue = value;
		}
		return d.toString();
	}

	private void stroke(Path path, String color, int width) {
		path.setFill("none");
		path.setStroke(color);
		path.setStrokeWidth(Integer.toString(width));
		path.setStrokeLinejoin("round");
		path.setStrokeLinecap("round");
	}

	private void dots(Container group, CartesianSeries each, String color, double radius) {
		double[][] extents = bounds.get(each);
		int axis = axis(each);
		for (int row = 0; row < extents.length; ++row) {
			if (!Double.isNaN(extents[row][1])) {
				double category = category(row);
				double value = value(axis, extents[row][1]);
				Container dot = canvas.dot(group, x(category, value), y(category, value), radius, color);
				double shown = extents[row][1] - extents[row][0];
				canvas.tooltip(dot, categoryLabel(row), canvas.seriesName(each), Canvas.format(shown) + unit(yAxes.get(axis)));
			}
		}
	}

	private void lines() {
		G group = canvas.group("lines");
		for (CartesianSeries each : series) {
			if (each instanceof Line line) {
				String color = canvas.color(line, series.indexOf(line));
				for (List<Integer> run : runs(bounds.get(line))) {
					stroke(canvas.path(group, trace(run, bounds.get(line), axis(line), line.getCurve())), color, line.getStrokeWidth());
				}
				if (line.isDot()) {
					dots(group, line, color, 4);
				}
			}
		}
	}

	private void areas() {
		G group = canvas.group("areas");
		for (CartesianSeries each : series) {
			if (each instanceof Area area) {
				String color = canvas.color(area, series.indexOf(area));
				int axis = axis(area);
				double[][] extents = bounds.get(area);
				for (List<Integer> run : runs(extents)) {
					StringBuilder d = new StringBuilder(trace(run, extents, axis, Curve.LINEAR));
					for (int index = run.size() - 1; index >= 0; --index) {
						int row = run.get(index);
						d.append(" L ").append(point(category(row), value(axis, extents[row][0])));
					}
					Path fill = canvas.path(group, d.append(" Z").toString());
					fill.setFill(area.getFill() == null ? color : area.getFill());
					fill.setFillOpacity(Canvas.number(area.getFillOpacity()));
					stroke(canvas.path(group, trace(run, extents, axis, Curve.LINEAR)), color, 2);
				}
				if (area.isDot()) {
					dots(group, area, color, 4);
				}
			}
		}
	}

	/**
	 * Points, the largest first so that a small one is not hidden under a large one. Each takes its
	 * size, fill and border from columns when the scatter says so, and its name from a column.
	 */
	private void scatters() {
		G group = canvas.group("scatters");
		G labels = canvas.group("labels");
		double fontSize = canvas.fontSize();
		String xName = xAxis.getLabel() != null ? xAxis.getLabel()
				: xAxis.getDataKey() != null ? canvas.values.label(xAxis.getDataKey()) : canvas.values.label(canvas.values.firstColumn());
		for (CartesianSeries each : series) {
			if (each instanceof Scatter scatter) {
				Encodings encodings = new Encodings(canvas, scatter, canvas.color(scatter, series.indexOf(scatter)));
				List<String> names = scatter.getNameKey() == null ? null : canvas.values.labels(scatter.getNameKey());
				double[][] extents = bounds.get(scatter);
				int axis = axis(scatter);
				List<Integer> rows = new ArrayList<>();
				for (int row = 0; row < extents.length; ++row) {
					if (!Double.isNaN(extents[row][1]) && (!numericX || !Double.isNaN(xValues[row]))) {
						rows.add(row);
					}
				}
				rows.sort((a, b) -> Double.compare(encodings.radius(b), encodings.radius(a)));
				for (int row : rows) {
					double category = category(row);
					double value = value(axis, extents[row][1]);
					double x = x(category, value);
					double y = y(category, value);
					double radius = encodings.radius(row);
					Shape shape = scatter.getShape();
					Container mark = shape == null || shape == Shape.CIRCLE
							? canvas.dot(group, x, y, radius, encodings.fill(row))
							: marker(group, shape, x, y, radius, encodings.fill(row));
					if (encodings.border(row)) {
						Border border = scatter.getBorder();
						if (mark instanceof Circle circle) {
							circle.setStroke(encodings.borderColor());
							circle.setStrokeWidth(Integer.toString(border.getStrokeWidth()));
							circle.setStrokeDasharray(border.getStrokeDasharray());
						} else if (mark instanceof Path path) {
							path.setStroke(encodings.borderColor());
							path.setStrokeWidth(Integer.toString(border.getStrokeWidth()));
							path.setStrokeDasharray(border.getStrokeDasharray());
						}
					}
					String name = names == null ? categoryLabel(row) : names.get(row);
					List<String[]> lines = new ArrayList<>();
					lines.add(new String[] { xName, categoryLabel(row) + unit(xAxis) });
					lines.add(new String[] { canvas.seriesName(scatter), Canvas.format(extents[row][1]) + unit(yAxes.get(axis)) });
					lines.addAll(encodings.tooltip(row));
					canvas.tooltip(mark, name, lines);
					if (scatter.isLabel() && names != null) {
						// To the right of the point, or to the left where the right has no room
						double width = canvas.textWidth(name);
						boolean left = x + radius + 4 + width > plot.right();
						canvas.centeredText(labels, left ? x - radius - 4 : x + radius + 4, y, name, left ? "end" : "start", canvas.theme.ink());
					}
				}
			}
		}
	}

	/** A marker of a shape, scaled from 8 pixels across to a radius, and ringed with the surface. */
	private Container marker(Container group, Shape shape, double x, double y, double radius, String color) {
		double s = radius / 4.5;
		String d = switch (shape) {
			case SQUARE -> "M " + p(x - 4 * s, y - 4 * s) + " L " + p(x + 4 * s, y - 4 * s) + " L " + p(x + 4 * s, y + 4 * s) + " L " + p(x - 4 * s, y + 4 * s) + " Z";
			case DIAMOND -> "M " + p(x, y - 5.5 * s) + " L " + p(x + 5.5 * s, y) + " L " + p(x, y + 5.5 * s) + " L " + p(x - 5.5 * s, y) + " Z";
			case TRIANGLE -> "M " + p(x, y - 5.5 * s) + " L " + p(x + 5 * s, y + 4 * s) + " L " + p(x - 5 * s, y + 4 * s) + " Z";
			default -> null;
		};
		if (d == null) {
			return canvas.dot(group, x, y, radius, color);
		}
		Path path = canvas.path(group, d);
		path.setFill(color);
		path.setStroke(canvas.theme.surface());
		path.setStrokeWidth("2");
		return path;
	}

	private static String p(double x, double y) {
		return Canvas.number(x) + " " + Canvas.number(y);
	}

	private void referenceLines() {
		G group = canvas.group("reference-lines");
		double fontSize = canvas.fontSize();
		String ink = canvas.theme.ink();
		for (ReferenceLine line : chart.getReferenceLines()) {
			String stroke = line.getStroke() == null ? canvas.theme.secondaryInk() : line.getStroke();
			double y = Values.number(line.getY());
			if (!Double.isNaN(y)) {
				double value = value(axisIndex(line.getYAxis()), y);
				rule(group, categoryStart(), value, categoryEnd(), value, stroke, line.getStrokeDasharray());
				if (line.getLabel() != null) {
					if (vertical) {
						canvas.text(group, value + 4, plot.top() + fontSize, line.getLabel(), "start", ink);
					} else {
						// At the start of the line, where the end of a series is less likely to be
						canvas.text(group, plot.left() + 4, value - 4, line.getLabel(), "start", ink);
					}
				}
			}
			if (line.getX() != null) {
				double category;
				if (numericX) {
					category = xTicks.position(Values.number(line.getX()), categoryStart(), categoryEnd());
				} else if (categories.contains(line.getX())) {
					category = category(categories.indexOf(line.getX()));
				} else {
					continue;
				}
				Ticks ticks = valueTicks.getFirst();
				rule(group, category, value(0, ticks.min()), category, value(0, ticks.max()), stroke, line.getStrokeDasharray());
				if (line.getLabel() != null) {
					if (vertical) {
						canvas.text(group, plot.right() - 4, category - 4, line.getLabel(), "end", ink);
					} else {
						canvas.text(group, category + 4, plot.top() + fontSize, line.getLabel(), "start", ink);
					}
				}
			}
		}
	}

}
