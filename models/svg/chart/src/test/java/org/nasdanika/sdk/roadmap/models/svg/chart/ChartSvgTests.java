package org.nasdanika.sdk.roadmap.models.svg.chart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;
import org.nasdanika.sdk.roadmap.models.core.chart.AxisType;
import org.nasdanika.sdk.roadmap.models.core.chart.Bar;
import org.nasdanika.sdk.roadmap.models.core.chart.BarChart;
import org.nasdanika.sdk.roadmap.models.core.chart.Border;
import org.nasdanika.sdk.roadmap.models.core.chart.Cell;
import org.nasdanika.sdk.roadmap.models.core.chart.ChartFactory;
import org.nasdanika.sdk.roadmap.models.core.chart.Curve;
import org.nasdanika.sdk.roadmap.models.core.chart.Layout;
import org.nasdanika.sdk.roadmap.models.core.chart.Line;
import org.nasdanika.sdk.roadmap.models.core.chart.LineChart;
import org.nasdanika.sdk.roadmap.models.core.chart.Pie;
import org.nasdanika.sdk.roadmap.models.core.chart.PieChart;
import org.nasdanika.sdk.roadmap.models.core.chart.ReferenceLine;
import org.nasdanika.sdk.roadmap.models.core.chart.Scatter;
import org.nasdanika.sdk.roadmap.models.core.chart.ScatterChart;
import org.nasdanika.sdk.roadmap.models.core.chart.Shade;
import org.nasdanika.sdk.roadmap.models.core.chart.XAxis;
import org.nasdanika.sdk.roadmap.models.core.chart.YAxis;
import org.nasdanika.sdk.roadmap.models.core.chart.ZAxis;
import org.nasdanika.sdk.roadmap.models.core.document.Column;
import org.nasdanika.sdk.roadmap.models.core.document.DataType;
import org.nasdanika.sdk.roadmap.models.core.document.DocumentFactory;
import org.nasdanika.sdk.roadmap.models.core.document.Frame;
import org.nasdanika.sdk.roadmap.models.core.document.FrameReference;
import org.nasdanika.sdk.runtime.models.svg.Circle;
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
	void costAndPassRateAsTwoSmallCharts() throws IOException {
		Frame results = results();
		results.getColumns().add(column("cost", DataType.NUMBER, "1.2", "1.4", "2.1", "1.4"));
		FrameReference shared = DocumentFactory.eINSTANCE.createFrameReference();
		shared.setTarget(results);

		Bar cost = ChartFactory.eINSTANCE.createBar();
		cost.setDataKey("cost");
		YAxis cents = ChartFactory.eINSTANCE.createYAxis();
		cents.setLabel("Cents per case");
		cents.setWidth(64);
		BarChart costs = ChartFactory.eINSTANCE.createBarChart();
		costs.setTitle("Cost per case by prompt version");
		costs.setData(EcoreUtil.copy(shared));
		costs.setHeight(200);
		costs.getSeries().add(cost);
		costs.getYAxes().add(cents);

		Line passRate = ChartFactory.eINSTANCE.createLine();
		passRate.setDataKey("passRate");
		YAxis percent = ChartFactory.eINSTANCE.createYAxis();
		percent.setLabel("Pass rate");
		percent.setUnit("%");
		percent.setMax("100");
		percent.setWidth(64);
		LineChart passRates = ChartFactory.eINSTANCE.createLineChart();
		passRates.setTitle("Pass rate by prompt version");
		passRates.setData(EcoreUtil.copy(shared));
		passRates.setHeight(200);
		passRates.getSeries().add(passRate);
		passRates.getYAxes().add(percent);

		Svg costSvg = new ChartSvgWriter().write(costs);
		save(costSvg, "chart-cost-per-case");
		Svg passRateSvg = new ChartSvgWriter().write(passRates);
		save(passRateSvg, "chart-pass-rate-line");

		// One width of axis: the middle of the first bar is above the first dot
		org.nasdanika.sdk.runtime.models.svg.Path firstBar = ofType(group(costSvg, "bars"),
				org.nasdanika.sdk.runtime.models.svg.Path.class).getFirst();
		Circle firstDot = ofType(group(passRateSvg, "lines"), Circle.class).getFirst();
		assertThat(Double.parseDouble(firstBar.getD().split(" ")[1]) + 12).isCloseTo(firstDot.getCx(), within(0.11));
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
	void aCostBenefitChart() throws IOException {
		Frame runs = DocumentFactory.eINSTANCE.createFrame();
		runs.getColumns().addAll(List.of(
				column("configuration", DataType.TEXT, "small, zero-shot", "small, few-shot",
						"small + RAG", "large, few-shot", "large + RAG", "large + tools"),
				column("cost", DataType.NUMBER, "0.3", "0.5", "0.9", "1.8", "2.4", "3.1"),
				column("value", DataType.NUMBER, "61", "76", "79", "84", "88", "83"),
				column("coverage", DataType.NUMBER, "25", "40", "70", "50", "95", "80"),
				column("risk", DataType.NUMBER, "0.6", "0.45", "0.3", "0.25", "0.15", "0.5"),
				column("fewRuns", DataType.TEXT, "no", "no", "no", "no", "yes", "no")));

		// tag::cost-benefit[]
		Scatter configurations = ChartFactory.eINSTANCE.createScatter();
		configurations.setName("Value");
		configurations.setDataKey("value"); // <1>
		configurations.setNameKey("configuration");
		configurations.setLabel(true);

		ZAxis coverage = ChartFactory.eINSTANCE.createZAxis();
		coverage.setDataKey("coverage"); // <2>
		coverage.setName("Coverage");
		coverage.setUnit("%");
		configurations.setZAxis(coverage);

		Shade risk = ChartFactory.eINSTANCE.createShade();
		risk.setDataKey("risk"); // <3>
		risk.setName("Risk");
		configurations.setShade(risk);

		Border fewRuns = ChartFactory.eINSTANCE.createBorder();
		fewRuns.setDataKey("fewRuns"); // <4>
		fewRuns.setName("Too few runs");
		fewRuns.setStrokeDasharray("3 2");
		configurations.setBorder(fewRuns);

		XAxis cost = ChartFactory.eINSTANCE.createXAxis();
		cost.setDataKey("cost"); // <5>
		cost.setType(AxisType.NUMBER);
		cost.setLabel("Cost per case, cents");
		YAxis value = ChartFactory.eINSTANCE.createYAxis();
		value.setLabel("Business value");
		value.setMin("auto");
		value.setMax("100");
		ReferenceLine floor = ChartFactory.eINSTANCE.createReferenceLine();
		floor.setY("70"); // <6>
		floor.setLabel("Quality floor");
		floor.setStrokeDasharray("3 3");

		ScatterChart chart = ChartFactory.eINSTANCE.createScatterChart();
		chart.setTitle("Cost and value of six configurations");
		chart.setAlternativeText("Value rises with cost up to large + RAG, 88 at 2.4 cents,"
				+ " with the widest coverage and the least risk, from too few runs."
				+ " Large + tools costs more and is worth less."
				+ " Small, zero-shot is under the quality floor of 70.");
		chart.setData(runs);
		chart.getSeries().add(configurations);
		chart.setXAxis(cost);
		chart.getYAxes().add(value);
		chart.getReferenceLines().add(floor);
		chart.setLegend(ChartFactory.eINSTANCE.createLegend());
		chart.setTooltip(ChartFactory.eINSTANCE.createTooltip());
		// end::cost-benefit[]

		Svg svg = new ChartSvgWriter().write(chart);
		save(svg, "chart-cost-benefit");

		List<Circle> points = ofType(group(svg, "scatters"), Circle.class);
		assertThat(points).hasSize(6);
		// The largest point is drawn first, so the smaller ones stay on top
		Circle largest = points.getFirst();
		assertThat(ofType(largest.children(), Title.class).getFirst().getValue())
				.startsWith("large + RAG\n")
				.contains("Coverage : 95%", "Risk : 0.15", "Too few runs : yes");
		assertThat(largest.getStrokeDasharray()).isEqualTo("3 2");
		// The least risk is the lightest shade, the greatest the darkest
		assertThat(largest.getFill()).isEqualTo("#86b6ef");
		assertThat(points).extracting(Circle::getFill).contains("#0d366b");
		assertThat(points).extracting(Circle::getR).isSortedAccordingTo((a, b) -> Double.compare(b, a));
		assertThat(ofType(group(svg, "labels"), org.nasdanika.sdk.runtime.models.svg.Text.class)).hasSize(6);
	}

	@Test
	void shadesMixInSrgb() {
		assertThat(Encodings.mix("#000000", "#ffffff", 0.5)).isEqualTo("#808080");
		assertThat(Encodings.mix("#86b6ef", "#0d366b", 0)).isEqualTo("#86b6ef");
		assertThat(Encodings.mix("#86b6ef", "#0d366b", 1)).isEqualTo("#0d366b");
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
