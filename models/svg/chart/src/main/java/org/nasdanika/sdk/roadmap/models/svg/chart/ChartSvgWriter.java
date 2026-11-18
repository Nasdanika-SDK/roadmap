package org.nasdanika.sdk.roadmap.models.svg.chart;

import java.util.ArrayList;
import java.util.List;

import org.nasdanika.sdk.roadmap.models.core.chart.Area;
import org.nasdanika.sdk.roadmap.models.core.chart.Bar;
import org.nasdanika.sdk.roadmap.models.core.chart.CartesianChart;
import org.nasdanika.sdk.roadmap.models.core.chart.Chart;
import org.nasdanika.sdk.roadmap.models.core.chart.Line;
import org.nasdanika.sdk.roadmap.models.core.chart.Pie;
import org.nasdanika.sdk.roadmap.models.core.chart.PieChart;
import org.nasdanika.sdk.roadmap.models.core.chart.Scatter;
import org.nasdanika.sdk.roadmap.models.core.chart.Series;
import org.nasdanika.sdk.roadmap.models.svg.chart.Canvas.LegendEntry;
import org.nasdanika.sdk.roadmap.models.svg.chart.Canvas.Mark;
import org.nasdanika.sdk.runtime.models.svg.G;
import org.nasdanika.sdk.runtime.models.svg.Svg;

/**
 * Writes a chart of the chart nano-model as an element of the runtime's SVG model, which
 * {@code SvgFormat} saves as an SVG document.
 *
 * The chart's title is the accessible name of the SVG and its alternative text the description;
 * neither is drawn, since a document writer makes the title a caption. With a tooltip, each mark
 * has a title, which a browser shows on hover. Marks follow one set of rules: lines 2 pixels wide,
 * bars at most 24 pixels thick with a rounded end, dots ringed with the surface, a hairline grid,
 * and text in ink and not in the colors of series.
 */
public class ChartSvgWriter {

	private final ChartTheme theme;

	public ChartSvgWriter() {
		this(ChartTheme.light());
	}

	public ChartSvgWriter(ChartTheme theme) {
		this.theme = theme;
	}

	public Svg write(Chart<?> chart) {
		Canvas canvas = new Canvas(theme, chart);
		Box box = new Box(8, 8, chart.getWidth() - 8, chart.getHeight() - 8);
		switch (chart) {
			case PieChart pieChart -> {
				PieDrawing drawing = new PieDrawing(canvas, pieChart);
				drawing.draw(canvas.legend(entries(canvas, drawing, pieChart), box));
			}
			case CartesianChart<?> cartesian -> {
				CartesianDrawing drawing = new CartesianDrawing(canvas, cartesian);
				drawing.draw(canvas.legend(entries(canvas, cartesian), box));
			}
			default -> throw new IllegalArgumentException("Not a chart that can be drawn yet: " + chart.eClass().getName());
		}
		canvas.svg.getContent().removeIf(entry -> entry.getValue() instanceof G group && group.getContent().isEmpty());
		return canvas.svg;
	}

	private static List<LegendEntry> entries(Canvas canvas, CartesianChart<?> chart) {
		List<LegendEntry> entries = new ArrayList<>();
		for (int index = 0; index < chart.getSeries().size(); ++index) {
			Series series = chart.getSeries().get(index);
			String color = canvas.color(series, index);
			if (series instanceof Scatter scatter) {
				// A shaded scatter has no color of its own to show: its shade has the entry
				if (scatter.getShade() == null) {
					entries.add(new LegendEntry(canvas.seriesName(series), color, Mark.DOT));
				}
				entries.addAll(new Encodings(canvas, scatter, color).legend());
			} else {
				Mark mark = series instanceof Line ? Mark.LINE : series instanceof Bar || series instanceof Area ? Mark.BOX : Mark.DOT;
				entries.add(new LegendEntry(canvas.seriesName(series), color, mark));
			}
		}
		return entries;
	}

	/** A pie's legend names its slices: those of the first pie. */
	private static List<LegendEntry> entries(Canvas canvas, PieDrawing drawing, PieChart chart) {
		List<LegendEntry> entries = new ArrayList<>();
		if (!chart.getSeries().isEmpty()) {
			Pie pie = chart.getSeries().getFirst();
			List<String> names = drawing.names(pie);
			for (int row = 0; row < names.size(); ++row) {
				String color = Canvas.cellFill(pie.getCells(), names.get(row), canvas.color(row, null));
				entries.add(new LegendEntry(names.get(row), color, Mark.BOX));
			}
		}
		return entries;
	}

}
