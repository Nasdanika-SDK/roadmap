package org.nasdanika.sdk.roadmap.models.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nasdanika.sdk.roadmap.models.core.change.ChangeFactory;
import org.nasdanika.sdk.roadmap.models.core.change.ChangeMode;
import org.nasdanika.sdk.roadmap.models.core.change.SetChange;
import org.nasdanika.sdk.roadmap.models.core.chart.Bar;
import org.nasdanika.sdk.roadmap.models.core.chart.BarChart;
import org.nasdanika.sdk.roadmap.models.core.chart.CartesianSeries;
import org.nasdanika.sdk.roadmap.models.core.chart.Cell;
import org.nasdanika.sdk.roadmap.models.core.chart.ChartFactory;
import org.nasdanika.sdk.roadmap.models.core.chart.ChartPackage;
import org.nasdanika.sdk.roadmap.models.core.chart.ComposedChart;
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
import org.nasdanika.sdk.roadmap.models.core.document.Document;
import org.nasdanika.sdk.roadmap.models.core.document.DocumentFactory;
import org.nasdanika.sdk.roadmap.models.core.document.DocumentPackage;
import org.nasdanika.sdk.roadmap.models.core.document.Frame;
import org.nasdanika.sdk.roadmap.models.core.document.FrameReference;
import org.nasdanika.sdk.roadmap.models.core.document.Section;
import org.nasdanika.sdk.roadmap.models.core.document.Table;
import org.nasdanika.sdk.roadmap.models.core.document.Text;
import org.nasdanika.sdk.roadmap.models.core.enterprise.Channel;
import org.nasdanika.sdk.roadmap.models.core.enterprise.Concern;
import org.nasdanika.sdk.roadmap.models.core.enterprise.EnterpriseCatalog;
import org.nasdanika.sdk.roadmap.models.core.enterprise.EnterpriseFactory;
import org.nasdanika.sdk.roadmap.models.core.enterprise.Format;
import org.nasdanika.sdk.roadmap.models.core.enterprise.Message;
import org.nasdanika.sdk.roadmap.models.core.enterprise.Stakeholder;
import org.nasdanika.sdk.roadmap.models.core.enterprise.Viewpoint;
import org.nasdanika.sdk.roadmap.models.core.example.Example;
import org.nasdanika.sdk.roadmap.models.core.example.ExampleFactory;
import org.nasdanika.sdk.roadmap.models.core.example.Payload;
import org.nasdanika.sdk.roadmap.models.core.kind.Classification;
import org.nasdanika.sdk.roadmap.models.core.kind.Kind;
import org.nasdanika.sdk.roadmap.models.core.kind.KindCatalog;
import org.nasdanika.sdk.roadmap.models.core.kind.KindFactory;
import org.nasdanika.sdk.roadmap.models.core.kind.KindReference;
import org.nasdanika.sdk.roadmap.models.core.occupancy.Coordinate;
import org.nasdanika.sdk.roadmap.models.core.occupancy.Dimension;
import org.nasdanika.sdk.roadmap.models.core.occupancy.Extent;
import org.nasdanika.sdk.roadmap.models.core.occupancy.Occupancy;
import org.nasdanika.sdk.roadmap.models.core.occupancy.OccupancyFactory;
import org.nasdanika.sdk.roadmap.models.core.occupancy.Transition;

/**
 * The nano-models instantiated: enough to show what each one says and that they fit together. The
 * roadmap book's chapter on the core nano-models includes its listings from here.
 */
class NanoModelTests {

	static Format format(String id) {
		Format format = EnterpriseFactory.eINSTANCE.createFormat();
		format.setId(id);
		format.setName(id);
		return format;
	}

	static Channel channel(String name, Format... accepts) {
		Channel channel = EnterpriseFactory.eINSTANCE.createChannel();
		channel.setName(name);
		channel.getAccepts().addAll(List.of(accepts));
		return channel;
	}

	/** The formats a stakeholder can be sent over a channel: the ones both take, in the order of the stakeholder. */
	// tag::deliverable[]
	static List<Format> deliverable(Stakeholder stakeholder, Channel channel) {
		List<Format> formats = new ArrayList<>(stakeholder.getFormats());
		formats.retainAll(channel.getAccepts());
		return formats;
	}
	// end::deliverable[]

	@Test
	void theFirstModelOfAnEffort() {
		// tag::enterprise[]
		Format markdown = format("markdown");
		Format html = format("html");
		Format pdf = format("pdf");
		Channel email = channel("E-mail", html, pdf); // <1>
		Channel mcp = channel("MCP", markdown);

		Stakeholder customer = EnterpriseFactory.eINSTANCE.createStakeholder();
		customer.setName("Customer");
		customer.getFormats().addAll(List.of(pdf, html)); // <2>
		customer.getChannels().add(email);
		Concern finances = EnterpriseFactory.eINSTANCE.createConcern();
		finances.setName("What is the state of my finances?");
		customer.getConcerns().add(finances);

		Stakeholder agent = EnterpriseFactory.eINSTANCE.createStakeholder();
		agent.setName("Assistant");
		agent.getFormats().add(markdown);
		agent.getChannels().add(mcp);

		Viewpoint statement = EnterpriseFactory.eINSTANCE.createViewpoint();
		statement.setName("Monthly statement");
		statement.setDocumentation("Opening balance, transactions by date, closing balance."); // <3>
		statement.getFrames().add(finances);
		statement.getElementKinds().add(DocumentPackage.Literals.TABLE); // <4>

		assertThat(deliverable(customer, email)).containsExactly(pdf, html); // <5>
		assertThat(deliverable(agent, mcp)).containsExactly(markdown);
		assertThat(deliverable(agent, email)).isEmpty();
		assertThat(finances.getStakeholder()).isSameAs(customer);
		// end::enterprise[]
	}

	@Test
	void aDocumentIsAViewAndASectionIsARepresentation() {
		// tag::document[]
		Viewpoint statement = EnterpriseFactory.eINSTANCE.createViewpoint();
		statement.setName("Monthly statement");

		Document november = DocumentFactory.eINSTANCE.createDocument(); // <1>
		november.setName("Statement for November 2026");
		november.setViewpoint(statement);

		Section transactions = DocumentFactory.eINSTANCE.createSection(); // <2>
		transactions.setTitle("Transactions");
		Text note = DocumentFactory.eINSTANCE.createText();
		note.setText("No fees were charged in **November**.");
		note.setFormat("markdown"); // <3>
		transactions.getElements().add(note);
		november.getRepresentations().add(transactions);

		Message message = EnterpriseFactory.eINSTANCE.createMessage(); // <4>
		message.setView(november);
		message.setFormat(format("pdf"));

		assertThat(november.getViewpoint().getName()).isEqualTo("Monthly statement");
		assertThat(november.getRepresentations().get(0).getElements()).containsExactly(note);
		assertThat(message.getView()).isSameAs(november);
		// end::document[]
	}

	@Test
	void aKindLivesInACatalogAndCarriesChanges(@TempDir Path directory) throws IOException {
		// tag::kind[]
		SetChange memory = ChangeFactory.eINSTANCE.createSetChange(); // <1>
		memory.setFeature("memory");
		memory.setLiteral("16");
		memory.setMode(ChangeMode.CONSTRAINT);

		Kind large = KindFactory.eINSTANCE.createKind();
		large.setId("large");
		large.setName("Large");
		large.getChanges().add(memory);

		KindCatalog catalog = KindFactory.eINSTANCE.createKindCatalog();
		catalog.setName("Machine sizes");
		catalog.getKinds().add(large);

		KindReference reference = KindFactory.eINSTANCE.createKindReference(); // <2>
		reference.setTarget(large);
		Classification classification = KindFactory.eINSTANCE.createClassification();
		classification.setKind(reference);

		ResourceSet resourceSet = resourceSet();
		Resource catalogResource = resourceSet.createResource(URI.createFileURI(directory.resolve("sizes.xmi").toString()));
		catalogResource.getContents().add(catalog);
		Resource machineResource = resourceSet.createResource(URI.createFileURI(directory.resolve("machine.xmi").toString()));
		machineResource.getContents().add(classification);
		catalogResource.save(null);
		machineResource.save(null); // <3>

		Resource loaded = resourceSet().getResource(machineResource.getURI(), true); // <4>
		Classification loadedClassification = (Classification) loaded.getContents().get(0);
		Kind loadedKind = ((KindReference) loadedClassification.getKind()).getTarget();

		assertThat(loadedKind.getName()).isEqualTo("Large");
		assertThat(loadedKind.eResource().getURI().lastSegment()).isEqualTo("sizes.xmi");
		assertThat(((SetChange) loadedKind.getChanges().get(0)).getLiteral()).isEqualTo("16");
		// end::kind[]
	}

	private static ResourceSet resourceSet() {
		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
		return resourceSet;
	}

	@Test
	void anOccupancyIsABoxAndATransitionSaysWhatChanges() {
		// tag::occupancy[]
		Dimension time = OccupancyFactory.eINSTANCE.createDimension();
		time.setName("Calendar time");
		time.setComparator("iso8601");

		Occupancy draft = occupancy(time, "2026-11-01", "2026-11-11"); // <1>
		Occupancy published = occupancy(time, "2026-11-11", null); // <2>
		published.getPredecessors().add(draft);

		Transition publish = OccupancyFactory.eINSTANCE.createTransition(); // <3>
		publish.setName("publish");
		publish.setTarget(published);
		SetChange status = ChangeFactory.eINSTANCE.createSetChange();
		status.setFeature("status");
		status.setLiteral("published");
		publish.getChanges().add(status);
		draft.getRelationships().add(publish);

		assertThat(publish.getSource()).isSameAs(draft);
		assertThat(published.getExtents().get(0).getEnd()).isNull();
		assertThat(draft.getExtents().get(0).isEndExclusive()).isTrue();
		// end::occupancy[]
	}

	private static Occupancy occupancy(Dimension dimension, String start, String end) {
		Extent extent = OccupancyFactory.eINSTANCE.createExtent();
		extent.setDimension(dimension);
		extent.setStart(coordinate(dimension, start));
		if (end != null) {
			extent.setEnd(coordinate(dimension, end));
			extent.setEndExclusive(true);
		}
		Occupancy occupancy = OccupancyFactory.eINSTANCE.createOccupancy();
		occupancy.getExtents().add(extent);
		return occupancy;
	}

	private static Coordinate coordinate(Dimension dimension, String value) {
		Coordinate coordinate = OccupancyFactory.eINSTANCE.createCoordinate();
		coordinate.setDimension(dimension);
		coordinate.setValue(value);
		return coordinate;
	}

	@Test
	void anExampleIsAnInputItsOutputAndWhy() {
		// tag::example[]
		Example example = ExampleFactory.eINSTANCE.createExample();
		example.setName("A transfer between own accounts");
		example.setInput(payload("{ \"from\": \"checking\", \"to\": \"savings\", \"amount\": 100 }"));
		example.setOutput(payload("{ \"status\": \"completed\", \"fee\": 0 }"));
		example.setRationale("Transfers between accounts of one customer have no fee.");

		assertThat(Map.of(example.getInput().getFormat(), example.getOutput().getText()))
				.containsEntry("json", "{ \"status\": \"completed\", \"fee\": 0 }");
		// end::example[]
	}

	private static Payload payload(String text) {
		Payload payload = ExampleFactory.eINSTANCE.createPayload();
		payload.setText(text);
		payload.setFormat("json");
		return payload;
	}

	static Column column(String name, DataType type, String... values) {
		Column column = DocumentFactory.eINSTANCE.createColumn();
		column.setName(name);
		column.setType(type);
		column.getValues().addAll(List.of(values));
		return column;
	}

	/** Results of an evaluation suite, one row per version of a prompt. */
	// tag::frame[]
	static Frame results() {
		Frame results = DocumentFactory.eINSTANCE.createFrame();
		results.setDocumentation("Eval suite of 120 cases, one run per prompt version."); // <1>
		results.getColumns().add(column("version", DataType.TEXT, "v1", "v2", "v3", "v4"));
		Column passRate = column("passRate", DataType.NUMBER, "72", "81", "86", "84");
		passRate.setLabel("Pass rate"); // <2>
		results.getColumns().add(passRate);
		return results;
	}
	// end::frame[]

	@Test
	void anEvaluationIsABarChartInADocument() {
		// tag::chart[]
		Bar passRate = ChartFactory.eINSTANCE.createBar(); // <1>
		passRate.setDataKey("passRate");
		passRate.setFill("#898781");
		Cell shipped = ChartFactory.eINSTANCE.createCell(); // <2>
		shipped.setCategory("v3");
		shipped.setFill("#2a78d6");
		passRate.getCells().add(shipped);

		YAxis percent = ChartFactory.eINSTANCE.createYAxis();
		percent.setUnit("%");
		percent.setMax("100");
		ReferenceLine threshold = ChartFactory.eINSTANCE.createReferenceLine(); // <3>
		threshold.setY("80");
		threshold.setLabel("Release threshold");
		threshold.setStrokeDasharray("3 3");

		BarChart chart = ChartFactory.eINSTANCE.createBarChart();
		chart.setTitle("Pass rate by prompt version");
		chart.setAlternativeText("v3 passes 86% of cases, the most of four versions."
				+ " v2, v3 and v4 are above the release threshold of 80%."); // <4>
		chart.setData(results());
		chart.getSeries().add(passRate);
		chart.getYAxes().add(percent);
		chart.getReferenceLines().add(threshold);

		Section evaluation = DocumentFactory.eINSTANCE.createSection(); // <5>
		evaluation.setTitle("Evaluation");
		evaluation.getElements().add(chart);

		assertThat(evaluation.getElements()).containsExactly(chart);
		assertThat(chart.getXAxis()).isNull(); // <6>
		assertThat(chart.getLayout()).isEqualTo(Layout.HORIZONTAL);
		assertThat(chart.getBarCategoryGap()).isEqualTo("10%");
		assertThat(chart.getWidth()).isEqualTo(640);
		// end::chart[]
	}

	@Test
	void costAndPassRateOnTwoAxes() {
		// tag::composed[]
		Frame results = results();
		results.getColumns().add(column("cost", DataType.NUMBER, "1.2", "1.4", "2.1", "1.4"));

		YAxis cents = ChartFactory.eINSTANCE.createYAxis();
		cents.setLabel("Cost per case, cents");
		YAxis percent = ChartFactory.eINSTANCE.createYAxis();
		percent.setLabel("Pass rate");
		percent.setUnit("%");
		percent.setOrientation(Side.RIGHT); // <1>

		Bar cost = ChartFactory.eINSTANCE.createBar();
		cost.setDataKey("cost");
		cost.setYAxis(cents);
		Line passRate = ChartFactory.eINSTANCE.createLine();
		passRate.setDataKey("passRate");
		passRate.setYAxis(percent); // <2>

		ComposedChart chart = ChartFactory.eINSTANCE.createComposedChart();
		chart.setTitle("What a point of pass rate costs");
		chart.setData(results);
		chart.getYAxes().addAll(List.of(cents, percent));
		chart.getSeries().addAll(List.<CartesianSeries>of(cost, passRate)); // <3>
		chart.setLegend(ChartFactory.eINSTANCE.createLegend());

		Column costs = results.getColumns().get(2);
		assertThat(costs.getValues()).containsExactly("1.2", "1.4", "2.1", "1.4"); // <4>
		assertThat(passRate.getStrokeWidth()).isEqualTo(2);
		assertThat(chart.getLegend().getPosition()).isEqualTo(Side.BOTTOM);
		// end::composed[]
	}

	@Test
	void outcomesAsARing() {
		// tag::pie[]
		Frame outcomes = DocumentFactory.eINSTANCE.createFrame();
		outcomes.getColumns().add(column("outcome", DataType.TEXT, "passed", "failed", "errored"));
		outcomes.getColumns().add(column("cases", DataType.NUMBER, "103", "13", "4"));

		Pie pie = ChartFactory.eINSTANCE.createPie();
		pie.setDataKey("cases"); // <1>
		pie.setInnerRadius("60%"); // <2>
		pie.setLabel(true);

		PieChart chart = ChartFactory.eINSTANCE.createPieChart();
		chart.setTitle("Outcomes of v3");
		chart.setData(outcomes);
		chart.getSeries().add(pie);

		assertThat(pie.getNameKey()).isNull();
		assertThat(pie.getOuterRadius()).isEqualTo("80%");
		assertThat(List.of(pie.getStartAngle(), pie.getEndAngle())).containsExactly(0, 360);
		// end::pie[]
	}

	@Test
	void aChartIsSavedAndLoadedWithItsDocument(@TempDir Path directory) throws IOException {
		// A viewpoint can name the kind of chart it is made of, since each kind is a class
		Viewpoint evaluations = EnterpriseFactory.eINSTANCE.createViewpoint();
		evaluations.setName("Evaluation report");
		evaluations.getElementKinds().add(ChartPackage.Literals.COMPOSED_CHART);

		YAxis left = ChartFactory.eINSTANCE.createYAxis();
		YAxis right = ChartFactory.eINSTANCE.createYAxis();
		right.setOrientation(Side.RIGHT);
		Line line = ChartFactory.eINSTANCE.createLine();
		line.setDataKey("passRate");
		line.setYAxis(right);
		Bar bar = ChartFactory.eINSTANCE.createBar();
		bar.setDataKey("passRate");
		ComposedChart chart = ChartFactory.eINSTANCE.createComposedChart();
		chart.setData(results());
		chart.getYAxes().addAll(List.of(left, right));
		chart.getSeries().addAll(List.<CartesianSeries>of(bar, line));

		Section section = DocumentFactory.eINSTANCE.createSection();
		section.getElements().add(chart);
		Document document = DocumentFactory.eINSTANCE.createDocument();
		document.setViewpoint(evaluations);
		document.getRepresentations().add(section);

		ResourceSet resourceSet = resourceSet();
		Resource resource = resourceSet.createResource(URI.createFileURI(directory.resolve("report.xmi").toString()));
		resource.getContents().addAll(List.of(evaluations, document));
		resource.save(null);

		Resource loaded = resourceSet().getResource(resource.getURI(), true);
		Document loadedDocument = (Document) loaded.getContents().get(1);
		ComposedChart loadedChart = (ComposedChart) loadedDocument.getRepresentations().get(0).getElements().get(0);
		assertThat(loadedChart.getSeries()).hasSize(2);
		assertThat(loadedChart.getSeries().get(0)).isInstanceOf(Bar.class);
		assertThat(loadedChart.getSeries().get(1)).isInstanceOf(Line.class);
		assertThat(loadedChart.getSeries().get(1).getYAxis()).isSameAs(loadedChart.getYAxes().get(1));
		assertThat(loadedChart.getYAxes().get(1).getOrientation()).isEqualTo(Side.RIGHT);
		assertThat(((Frame) loadedChart.getData()).getColumns().get(1).getValues()).containsExactly("72", "81", "86", "84");
		assertThat(loadedDocument.getViewpoint().getElementKinds()).containsExactly(ChartPackage.Literals.COMPOSED_CHART);
	}

	static FrameReference reference(Frame frame) {
		FrameReference reference = DocumentFactory.eINSTANCE.createFrameReference();
		reference.setTarget(frame);
		return reference;
	}

	@Test
	void aTableAndAChartShowOneFrame(@TempDir Path directory) throws IOException {
		// tag::shared-frame[]
		Frame results = results();
		Document report = DocumentFactory.eINSTANCE.createDocument();
		report.setName("Evaluation of the prompt");
		report.getFrames().add(results); // <1>

		Table table = DocumentFactory.eINSTANCE.createTable(); // <2>
		table.setData(reference(results));
		Line passRate = ChartFactory.eINSTANCE.createLine();
		passRate.setDataKey("passRate");
		LineChart chart = ChartFactory.eINSTANCE.createLineChart();
		chart.setData(reference(results)); // <3>
		chart.getSeries().add(passRate);

		Section evaluation = DocumentFactory.eINSTANCE.createSection();
		evaluation.getElements().addAll(List.of(table, chart));
		report.getRepresentations().add(evaluation);

		ResourceSet resourceSet = resourceSet();
		Resource reportResource = resourceSet.createResource(uri(directory, "report.xmi"));
		reportResource.getContents().add(report);
		Resource resultsResource = resourceSet.createResource(uri(directory, "results.xmi"));
		resultsResource.getContents().add(results); // <4>
		reportResource.save(null);
		resultsResource.save(null);

		Resource loaded = resourceSet().getResource(reportResource.getURI(), true);
		Document loadedReport = (Document) loaded.getContents().get(0);
		Frame loadedResults = loadedReport.getFrames().get(0); // <5>
		List<?> blocks = loadedReport.getRepresentations().get(0).getElements();
		Frame tableData = ((FrameReference) ((Table) blocks.get(0)).getData()).getTarget();
		Frame chartData = ((FrameReference) ((LineChart) blocks.get(1)).getData()).getTarget();

		assertThat(results.eContainer()).isSameAs(report);
		assertThat(loadedResults.eResource().getURI().lastSegment()).isEqualTo("results.xmi");
		assertThat(tableData).isSameAs(loadedResults).isSameAs(chartData); // <6>
		// end::shared-frame[]
	}

	private static URI uri(Path directory, String name) {
		return URI.createFileURI(directory.resolve(name).toString());
	}

	@Test
	void everyNanoModelIsRegisteredAsAService() {
		// The module descriptor says which providers it offers; a resource set that uses EPackage services finds them
		List<String> providers = NanoModelTests.class.getModule().getDescriptor().provides().stream()
				.filter(provides -> provides.service().equals(org.eclipse.emf.ecore.EPackage.class.getName()))
				.flatMap(provides -> provides.providers().stream())
				.toList();
		assertThat(providers).hasSize(8);

		List<String> namespaces = List.of(
				org.nasdanika.sdk.roadmap.models.core.util.ChangeEPackageProvider.provider(),
				org.nasdanika.sdk.roadmap.models.core.util.ChartEPackageProvider.provider(),
				org.nasdanika.sdk.roadmap.models.core.util.DocumentEPackageProvider.provider(),
				org.nasdanika.sdk.roadmap.models.core.util.EnterpriseEPackageProvider.provider(),
				org.nasdanika.sdk.roadmap.models.core.util.ExampleEPackageProvider.provider(),
				org.nasdanika.sdk.roadmap.models.core.util.KindEPackageProvider.provider(),
				org.nasdanika.sdk.roadmap.models.core.util.OccupancyEPackageProvider.provider(),
				org.nasdanika.sdk.roadmap.models.core.util.RelatedEPackageProvider.provider())
				.stream()
				.map(org.eclipse.emf.ecore.EPackage::getNsURI)
				.toList();

		assertThat(namespaces).containsExactly(
				"https://roadmap.sdk.nasdanika.org/models/core/change",
				"https://roadmap.sdk.nasdanika.org/models/core/chart",
				"https://roadmap.sdk.nasdanika.org/models/core/document",
				"https://roadmap.sdk.nasdanika.org/models/core/enterprise",
				"https://roadmap.sdk.nasdanika.org/models/core/example",
				"https://roadmap.sdk.nasdanika.org/models/core/kind",
				"https://roadmap.sdk.nasdanika.org/models/core/occupancy",
				"https://roadmap.sdk.nasdanika.org/models/core/related");
	}

}
