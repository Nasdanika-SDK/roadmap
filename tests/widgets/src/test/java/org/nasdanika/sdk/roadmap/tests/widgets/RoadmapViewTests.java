package org.nasdanika.sdk.roadmap.tests.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.nasdanika.sdk.roadmap.runtime.tui.Screenshots;
import org.nasdanika.sdk.roadmap.runtime.tui.TamboUIContext;
import org.nasdanika.sdk.roadmap.runtime.widget.Badge;
import org.nasdanika.sdk.roadmap.runtime.widget.ItemList;
import org.nasdanika.sdk.roadmap.runtime.widget.Kinds;
import org.nasdanika.sdk.roadmap.runtime.widget.Link;
import org.nasdanika.sdk.roadmap.runtime.widget.Section;
import org.nasdanika.sdk.roadmap.runtime.widget.Table;
import org.nasdanika.sdk.roadmap.runtime.widget.Text;
import org.nasdanika.sdk.roadmap.runtime.widget.Variant;
import org.nasdanika.sdk.roadmap.runtime.widget.Widget;
import org.nasdanika.sdk.roadmap.runtime.widget.markdown.GfmContext;
import org.nasdanika.sdk.roadmap.runtime.widget.markdown.MarkdownContext;
import org.nasdanika.sdk.roadmap.runtime.widget.terminal.GridContext;
import org.nasdanika.sdk.roadmap.runtime.widget.terminal.TerminalContext;

import dev.tamboui.buffer.Buffer;

/**
 * One view of this roadmap's runtime items, written once and rendered four ways. Each test writes
 * its rendering to {@code target/showcase}, and the book includes them from there, so the book
 * shows what the code produced.
 */
class RoadmapViewTests {

	// tag::data[]
	enum Status {

		AVAILABLE(Variant.SUCCESS),
		SKETCH(Variant.WARNING),
		PLANNED(Variant.SECONDARY);

		final Variant variant;

		Status(Variant variant) {
			this.variant = variant;
		}

	}

	record Item(String name, String module, Status status) {

	}

	static final List<Item> ITEMS = List.of(
			new Item("Bindings", "runtime/common", Status.SKETCH),
			new Item("Waypoints", "runtime/common", Status.SKETCH),
			new Item("Widgets", "runtime/widget", Status.SKETCH),
			new Item("Terminal on TamboUI", "runtime/tui", Status.SKETCH),
			new Item("Nasdanika Commander", "runtime/commander", Status.SKETCH),
			new Item("Screens drawn in Draw.io", "none yet", Status.PLANNED));
	// end::data[]

	/**
	 * The view. It names no format: it asks for kinds, and it has a plan for the one it may not get.
	 */
	// tag::view[]
	static <C> Widget<?, C> roadmap(Kinds<C> kinds, Supplier<String> runtime, List<Item> items) {
		Text<C> text = kinds.require(Text.class);
		Link<C> link = kinds.require(Link.class);
		Badge<C> badge = kinds.require(Badge.class);
		Section<C> section = kinds.require(Section.class);
		ItemList<C> list = kinds.require(ItemList.class);
		Optional<Table<C>> table = kinds.find(Table.class);

		Widget<?, C> body = table
				.<Widget<?, C>>map(kind -> kind.table(
						List.of(
								new Table.Column<Item, C>(text.text("Item"), item -> text.text(item.name()), 4),
								new Table.Column<Item, C>(text.text("Module"), item -> text.text(item.module()), 3),
								new Table.Column<Item, C>(text.text("Status"), item -> badge.badge(item.status().name(), item.status().variant), 2)),
						Table.Rows.of(items)))
				.orElseGet(() -> list.list(false, items.stream().map(item -> text.text(item.name() + ", " + item.status())).toList()));

		return section.section(text.text("Runtime roadmap"), List.of(
				text.text(() -> "Built on runtime " + runtime.get() + "."),
				body,
				link.link(text.text("roadmap.sdk.nasdanika.org"), "https://roadmap.sdk.nasdanika.org")));
	}
	// end::view[]

	static final Supplier<String> RUNTIME = () -> "2026.11.0";

	static Path showcase(String file, String content) throws IOException {
		Path directory = Path.of(System.getProperty("roadmap.target", "target"), "showcase");
		Files.createDirectories(directory);
		return Files.writeString(directory.resolve(file), content, StandardCharsets.UTF_8);
	}

	@Test
	void inGitHubFlavoredMarkdown() throws IOException {
		// tag::gfm[]
		StringBuilder out = new StringBuilder();
		roadmap(Kinds.of(GfmContext.class), RUNTIME, ITEMS).render(new GfmContext(out));
		// end::gfm[]
		showcase("roadmap.md", out.toString());

		assertThat(out.toString()).isEqualTo("""
				# Runtime roadmap

				Built on runtime 2026.11.0.

				| Item | Module | Status |
				| --- | --- | --- |
				| Bindings | runtime/common | **SKETCH** |
				| Waypoints | runtime/common | **SKETCH** |
				| Widgets | runtime/widget | **SKETCH** |
				| Terminal on TamboUI | runtime/tui | **SKETCH** |
				| Nasdanika Commander | runtime/commander | **SKETCH** |
				| Screens drawn in Draw.io | none yet | **PLANNED** |

				[roadmap.sdk.nasdanika.org](<https://roadmap.sdk.nasdanika.org>)

				""");
	}

	@Test
	void inCommonMarkWhichHasNoTables() throws IOException {
		// tag::commonmark[]
		StringBuilder out = new StringBuilder();
		roadmap(Kinds.of(MarkdownContext.class), RUNTIME, ITEMS).render(new MarkdownContext(out));
		// end::commonmark[]
		showcase("roadmap-commonmark.md", out.toString());

		assertThat(out.toString()).isEqualTo("""
				# Runtime roadmap

				Built on runtime 2026.11.0.

				- Bindings, SKETCH
				- Waypoints, SKETCH
				- Widgets, SKETCH
				- Terminal on TamboUI, SKETCH
				- Nasdanika Commander, SKETCH
				- Screens drawn in Draw.io, PLANNED

				[roadmap.sdk.nasdanika.org](<https://roadmap.sdk.nasdanika.org>)

				""");
	}

	@Test
	void onATerminalAsText() throws IOException {
		// tag::grid[]
		GridContext screen = new GridContext(64, 12);
		roadmap(Kinds.of(TerminalContext.class), RUNTIME, ITEMS).render(screen);
		// end::grid[]
		showcase("roadmap.txt", screen.toString());

		assertThat(screen.toString()).contains("Runtime roadmap").contains("Nasdanika Commander").contains("PLANNED");
	}

	@Test
	void onATamboUIScreen() throws IOException {
		// tag::tamboui[]
		Widget<?, TamboUIContext> view = roadmap(Kinds.of(TamboUIContext.class), RUNTIME, ITEMS); // <1>
		Buffer buffer = Screenshots.draw(64, 12, view::render); // <2>

		Path directory = Path.of(System.getProperty("roadmap.target", "target"), "showcase");
		Screenshots.write(buffer, "Nasdanika SDK Roadmap", directory.resolve("roadmap.svg")); // <3>
		// end::tamboui[]
		showcase("roadmap-tamboui.txt", Screenshots.text(buffer));

		assertThat(Screenshots.text(buffer)).contains("Runtime roadmap").contains("Nasdanika Commander").contains("PLANNED");
		assertThat(Files.readString(directory.resolve("roadmap.svg"))).startsWith("<svg").doesNotContain("@font-face");
	}

}
