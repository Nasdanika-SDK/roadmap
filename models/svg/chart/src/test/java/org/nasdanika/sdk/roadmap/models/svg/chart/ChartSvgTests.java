package org.nasdanika.sdk.roadmap.models.svg.chart;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.nasdanika.sdk.roadmap.models.core.chart.Bar;
import org.nasdanika.sdk.roadmap.models.core.chart.BarChart;
import org.nasdanika.sdk.roadmap.models.core.chart.CartesianSeries;
import org.nasdanika.sdk.roadmap.models.core.chart.Cell;
import org.nasdanika.sdk.roadmap.models.core.chart.ChartFactory;
import org.nasdanika.sdk.roadmap.models.core.chart.ComposedChart;
import org.nasdanika.sdk.roadmap.models.core.chart.Curve;
import org.nasdanika.sdk.roadmap.models.core.chart.Layout;
import org.nasdanika.sdk.roadmap.models.core.chart.Line;
import org.nasdanika.sdk.roadmap.models.core.chart.LineChart;
import org.nasdanika.sdk.roadmap.models.core.chart.Pie;
import org.nasdanika.sdk.roadmap.models.core.chart.PieChart;
import org.nasdanika.sdk.roadmap.models.core.chart.ReferenceLine;
import org.nasdanika.sdk.roadmap.models.core.chart.Side;
import org.nasdanika.sdk.roadmap.models.core.chart.YAxis;
import org.nasdanika.sdk.roadmap.models.core.document.Column;
import org.nasdanika.sdk.roadmap.models.core.document.DataType;
import org.nasdanika.sdk.roadmap.models.core.document.DocumentFactory;
import org.nasdanika.sdk.roadmap.models.core.document.Frame;
import org.nasdanika.sdk.roadmap.models.core.document.FrameReference;
import org.nasdanika.sdk.runtime.models.svg.Container;
import org.nasdanika.sdk.runtime.models.svg.Desc;
import org.nasdanika.sdk.runtime.models.svg.Element;
import org.nasdanika.sdk.runtime.models.svg.G;
import org.nasdanika.sdk.runtime.models.svg.Svg;
import org.nasdanika.sdk.runtime.models.svg.Title;
import org.nasdanika.sdk.runtime.models.svg.resource.SvgFormat;

/**
 * The charts of the roadmap book, written to SVG. Each test saves what it drew to
 * target/screenshots, from where the book includes it.
 */
class ChartSvgTests {

	static Column column(String name, DataType type, String... values) {
		Column column = DocumentFactory.eINSTANCE.createColumn();
		column.setName(name);
		column.setType(type);
		column.getValues().addAll(List.of(values));
		return column;
	}

	static Frame results() {
		Frame results = DocumentFactory.eINSTANCE.createFrame();
		results.getColumns().add(column("version", DataType.TEXT, "v1", "v2", "v3", "v4"));
		Column passRate = column("passRate", DataType.NUMBER, "72", "81", "86", "84");
		passRate.setLabel("Pass rate");
		results.getColumns().add(passRate);
		return results;
	}

	/** The bar chart of the chapter on the core nano-models. */
	static BarChart passRateByVersion() {
		Bar passRate = ChartFactory.eINSTANCE.createBar();
		passRate.setDataKey("passRate");
		passRate.setFill("#898781");
		Cell shipped = ChartFactory.eINSTANCE.createCell();
		shipped.setCategory("v3");
		shipped.setFill("#2a78d6");
		passRate.getCells().add(shipped);

		YAxis percent = ChartFactory.eINSTANCE.createYAxis();
		percent.setUnit("%");
		percent.setMax("100");
		ReferenceLine threshold = ChartFactory.eINSTANCE.createReferenceLine();
		threshold.setY("80");
		threshold.setLabel("Release threshold");
		threshold.setStrokeDasharray("3 3");

		BarChart chart = ChartFactory.eINSTANCE.createBarChart();
		chart.setTitle("Pass rate by prompt version");
		chart.setAlternativeText("v3 passes 86% of cases, the most of four versions."
				+ " v2, v3 and v4 are above the release threshold of 80%.");
		chart.setData(results());
		chart.getSeries().add(passRate);
		chart.getYAxes().add(percent);
		chart.getReferenceLines().add(threshold);
		return chart;
	}

	static void save(Svg svg, String name) throws IOException {
		Path file = Path.of(System.getProperty("roadmap.target", "target"), "screenshots", name + ".svg");
		Files.createDirectories(file.getParent());
		try (OutputStream out = Files.newOutputStream(file)) {
			SvgFormat.save(svg, out);
		}
	}

	/** The children of the group of a class: bars, lines, axes. */
	static List<Element> group(Svg svg, String styleClass) {
		return svg.children().stream()
				.filter(G.class::isInstance)
				.map(G.class::cast)
				.filter(group -> styleClass.equals(group.getStyleClass()))
				.findFirst()
				.<List<Element>>map(Container::children)
				.orElse(List.of());
	}

	static <T> List<T> ofType(List<Element> elements, Class<T> type) {
		return elements.stream().filter(type::isInstance).map(type::cast).toList();
	}

	@Test
	void aBarChartIsWrittenAsSvg() throws IOException {
		BarChart chart = passRateByVersion();
		chart.setTooltip(ChartFactory.eINSTANCE.createTooltip());

		// tag::write[]
		Svg svg = new ChartSvgWriter().write(chart); // <1>
		save(svg, "chart-pass-rate"); // <2>

		List<org.nasdanika.sdk.runtime.models.svg.Path> bars = ofType(group(svg, "bars"),
				org.nasdanika.sdk.runtime.models.svg.Path.class);
		assertThat(bars).extracting(bar -> bar.getFill())
				.containsExactly("#898781", "#898781", "#2a78d6", "#898781"); // <3>
		assertThat(ofType(bars.get(2).children(), Title.class).getFirst().getValue())
				.isEqualTo("v3, Pass rate : 86%"); // <4>
		assertThat(ofType(svg.children(), Desc.class).getFirst().getValue())
				.startsWith("v3 passes 86% of cases"); // <5>
		// end::write[]
	}

	@Test
	void aVerticalLayoutTurnsTheChart() throws IOException {
		BarChart chart = passRateByVersion();
		chart.setLayout(Layout.VERTICAL);
		chart.setHeight(240);
		chart.setGrid(ChartFactory.eINSTANCE.createCartesianGrid());
		Svg svg = new ChartSvgWriter().write(chart);
		save(svg, "chart-pass-rate-vertical");

		List<org.nasdanika.sdk.runtime.models.svg.Path> bars = ofType(group(svg, "bars"),
				org.nasdanika.sdk.runtime.models.svg.Path.class);
		// Horizontal bars start at the left edge: every path starts at the same x
		assertThat(bars).extracting(bar -> bar.getD().split(" ")[1]).containsOnly(bars.getFirst().getD().split(" ")[1]);
	}

	@Test
	void costAndPassRateOnTwoAxes() throws IOException {
		Frame results = results();
		results.getColumns().add(column("cost", DataType.NUMBER, "1.2", "1.4", "2.1", "1.4"));
		YAxis cents = ChartFactory.eINSTANCE.createYAxis();
		cents.setLabel("Cost per case, cents");
		YAxis percent = ChartFactory.eINSTANCE.createYAxis();
		percent.setLabel("Pass rate");
		percent.setUnit("%");
		percent.setOrientation(Side.RIGHT);
		Bar cost = ChartFactory.eINSTANCE.createBar();
		cost.setDataKey("cost");
		cost.setYAxis(cents);
		Line passRate = ChartFactory.eINSTANCE.createLine();
		passRate.setDataKey("passRate");
		passRate.setYAxis(percent);
		ComposedChart chart = ChartFactory.eINSTANCE.createComposedChart();
		chart.setTitle("What a point of pass rate costs");
		chart.setData(results);
		chart.getYAxes().addAll(List.of(cents, percent));
		chart.getSeries().addAll(List.<CartesianSeries>of(cost, passRate));
		chart.setLegend(ChartFactory.eINSTANCE.createLegend());

		Svg svg = new ChartSvgWriter().write(chart);
		save(svg, "chart-cost-and-pass-rate");

		assertThat(group(svg, "bars")).hasSize(4);
		assertThat(group(svg, "lines")).hasSize(1 + 4); // the line and its dots
		assertThat(group(svg, "legend")).isNotEmpty();
	}

	@Test
	void outcomesAsARing() throws IOException {
		Frame outcomes = DocumentFactory.eINSTANCE.createFrame();
		outcomes.getColumns().add(column("outcome", DataType.TEXT, "passed", "failed", "errored"));
		outcomes.getColumns().add(column("cases", DataType.NUMBER, "103", "13", "4"));
		Pie pie = ChartFactory.eINSTANCE.createPie();
		pie.setDataKey("cases");
		pie.setInnerRadius("60%");
		pie.setLabel(true);
		PieChart chart = ChartFactory.eINSTANCE.createPieChart();
		chart.setTitle("Outcomes of v3");
		chart.setData(outcomes);
		chart.getSeries().add(pie);
		chart.setLegend(ChartFactory.eINSTANCE.createLegend());

		Svg svg = new ChartSvgWriter().write(chart);
		save(svg, "chart-outcomes");

		List<org.nasdanika.sdk.runtime.models.svg.Path> slices = ofType(group(svg, "pies"),
				org.nasdanika.sdk.runtime.models.svg.Path.class);
		assertThat(slices).hasSize(3);
		// A ring: each slice has an outer and an inner arc
		assertThat(slices).allSatisfy(slice -> assertThat(slice.getD().split(" A ")).hasSize(3));
	}

	@Test
	void aChartDrawsAFrameItRefersTo() throws IOException {
		Frame trend = DocumentFactory.eINSTANCE.createFrame();
		trend.getColumns().add(column("week", DataType.TEXT, "W1", "W2", "W3", "W4", "W5", "W6"));
		trend.getColumns().add(column("passRate", DataType.NUMBER, "72", "75", "", "81", "84", "86"));
		FrameReference reference = DocumentFactory.eINSTANCE.createFrameReference();
		reference.setTarget(trend);

		Line passRate = ChartFactory.eINSTANCE.createLine();
		passRate.setDataKey("passRate");
		passRate.setName("Pass rate");
		passRate.setCurve(Curve.STEP);
		LineChart chart = ChartFactory.eINSTANCE.createLineChart();
		chart.setData(reference);
		chart.getSeries().add(passRate);

		Svg svg = new ChartSvgWriter().write(chart);
		save(svg, "chart-weekly-trend");

		List<org.nasdanika.sdk.runtime.models.svg.Path> lines = ofType(group(svg, "lines"),
				org.nasdanika.sdk.runtime.models.svg.Path.class);
		assertThat(lines).hasSize(2); // a missing value is a gap
	}

	@Test
	void ticksAreRoundNumbers() {
		assertThat(Ticks.of(0, 86, true, true, 5).values()).containsExactly(0.0, 20.0, 40.0, 60.0, 80.0, 100.0);
		assertThat(Ticks.of(0, 2.1, true, true, 5).values()).containsExactly(0.0, 0.5, 1.0, 1.5, 2.0, 2.5);
		assertThat(Ticks.of(0, 100, false, false, 5).values()).containsExactly(0.0, 20.0, 40.0, 60.0, 80.0, 100.0);
		assertThat(Ticks.of(-3, 7, true, true, 5).values()).containsExactly(-4.0, -2.0, 0.0, 2.0, 4.0, 6.0, 8.0);
		assertThat(Canvas.format(1234.5)).isEqualTo("1,234.5");
	}

}
