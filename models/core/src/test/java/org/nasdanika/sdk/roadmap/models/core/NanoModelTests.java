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
import org.nasdanika.sdk.roadmap.models.core.document.Document;
import org.nasdanika.sdk.roadmap.models.core.document.DocumentFactory;
import org.nasdanika.sdk.roadmap.models.core.document.DocumentPackage;
import org.nasdanika.sdk.roadmap.models.core.document.Section;
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

	@Test
	void everyNanoModelIsRegisteredAsAService() {
		// The module descriptor says which providers it offers; a resource set that uses EPackage services finds them
		List<String> providers = NanoModelTests.class.getModule().getDescriptor().provides().stream()
				.filter(provides -> provides.service().equals(org.eclipse.emf.ecore.EPackage.class.getName()))
				.flatMap(provides -> provides.providers().stream())
				.toList();
		assertThat(providers).hasSize(7);

		List<String> namespaces = List.of(
				org.nasdanika.sdk.roadmap.models.core.util.ChangeEPackageProvider.provider(),
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
				"https://roadmap.sdk.nasdanika.org/models/core/document",
				"https://roadmap.sdk.nasdanika.org/models/core/enterprise",
				"https://roadmap.sdk.nasdanika.org/models/core/example",
				"https://roadmap.sdk.nasdanika.org/models/core/kind",
				"https://roadmap.sdk.nasdanika.org/models/core/occupancy",
				"https://roadmap.sdk.nasdanika.org/models/core/related");
	}

}
