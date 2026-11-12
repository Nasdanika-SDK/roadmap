package org.nasdanika.sdk.roadmap.runtime.explorer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nasdanika.sdk.roadmap.runtime.explorer.Explorer.Focus;
import org.nasdanika.sdk.roadmap.runtime.explorer.jvm.ModuleNode;
import org.nasdanika.sdk.roadmap.runtime.explorer.jvm.ModuleViews;
import org.nasdanika.sdk.roadmap.runtime.explorer.jvm.ModulesVolume;
import org.nasdanika.sdk.roadmap.runtime.tui.Screenshots;
import org.nasdanika.sdk.roadmap.runtime.widget.Kinds;
import org.nasdanika.sdk.roadmap.runtime.widget.terminal.TerminalContext;
import org.nasdanika.sdk.runtime.volume.GenericVolume.Entry;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;

/**
 * Nasdanika Explorer, driven by keys and read from a buffer: no terminal is involved. The roadmap
 * book's chapter on the explorer includes its listings and its screenshots from here.
 */
class ExplorerTests {

	ModulesVolume modules;
	Explorer<ModuleNode> explorer;

	// tag::setup[]
	@BeforeEach
	void setUp() {
		modules = new ModulesVolume(Modules.SAMPLE); // <1>
		ModuleViews<TerminalContext> views = new ModuleViews<>(Kinds.of(TerminalContext.class)); // <2>
		explorer = new Explorer<>("jvm", modules, views::view); // <3>
	}
	// end::setup[]

	void press(KeyCode... keys) {
		for (KeyCode key: keys) {
			explorer.handle(KeyEvent.ofKey(key));
		}
	}

	void type(String text) {
		text.chars().forEach(ch -> explorer.handle(KeyEvent.ofChar(ch)));
	}

	/** What is on the screen, as text. */
	String screen(int columns, int rows) {
		Buffer buffer = Screenshots.buffer(columns, rows);
		explorer.render(Frame.forTesting(buffer));
		return Screenshots.text(buffer);
	}

	/** A screenshot for the book, in target/screenshots. */
	void screenshot(String name) {
		Buffer buffer = Screenshots.buffer(100, 24);
		explorer.render(Frame.forTesting(buffer));
		Path file = Path.of(System.getProperty("roadmap.target", "target"), "screenshots", name + ".svg");
		Screenshots.write(buffer, "Nasdanika Explorer", file);
		try {
			Files.writeString(file.resolveSibling(name + ".txt"), Screenshots.text(buffer));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Test
	void aTreeOnTheLeftAndAViewOfTheSelectionOnTheRight() {
		// tag::start[]
		String screen = screen(100, 24);
		screenshot("explorer");

		assertThat(explorer.getSelected().path()).isEqualTo("."); // <1>
		assertThat(screen).contains("- jvm").contains("+ java").contains("+ org"); // <2>
		assertThat(screen).contains("Modules").contains("Name").contains("Requires").contains("Provides"); // <3>
		assertThat(screen).contains("1Help").contains("3View").contains("10Quit");
		// end::start[]
	}

	@Test
	void theFolderViewCountsTheModulesAndTheirDirectives() {
		// tag::folder[]
		assertThat(explorer.select("org/nasdanika/sdk/runtime")).isTrue(); // <1>
		String screen = screen(100, 24);
		screenshot("explorer-folder");

		assertThat(screen).contains(" jvm: org/nasdanika/sdk/runtime ");
		assertThat(row(screen, "volume")).containsSubsequence("volume", "3", "1"); // <2>
		// end::folder[]

		ModuleNode runtime = modules.content("org/nasdanika/sdk/runtime");
		assertThat(runtime.moduleCount()).isEqualTo(3);
		assertThat(runtime.requiresCount()).isEqualTo(9); // With the java.base that every module requires
		assertThat(runtime.providesCount()).isEqualTo(1);

		explorer.select("org/nasdanika/sdk");
		assertThat(row(screen(100, 24), "/runtime")).containsSubsequence("/runtime", "3", "9", "1");
	}

	/** The row of the view that shows a name. */
	static String row(String screen, String name) {
		return screen.lines()
				.map(line -> line.substring(line.indexOf('│', 2) + 1)) // Past the tree
				.filter(line -> line.contains(" " + name + " "))
				.findFirst()
				.orElseThrow(() -> new AssertionError("No row for " + name + " in\n" + screen));
	}

	@Test
	void theArrowsOpenFoldersAndGoIntoThemAndBack() {
		// tag::arrows[]
		press(KeyCode.DOWN); // java
		assertThat(explorer.getSelected().path()).isEqualTo("java");
		assertThat(explorer.isExpanded("java")).isFalse();

		press(KeyCode.RIGHT); // <1>
		assertThat(explorer.isExpanded("java")).isTrue();
		press(KeyCode.RIGHT); // <2>
		assertThat(explorer.getSelected().path()).isEqualTo("java/base");

		press(KeyCode.LEFT); // <3>
		assertThat(explorer.getSelected().path()).isEqualTo("java");
		press(KeyCode.LEFT);
		assertThat(explorer.isExpanded("java")).isFalse();
		// end::arrows[]

		press(KeyCode.ENTER);
		assertThat(explorer.isExpanded("java")).isTrue();
		press(KeyCode.END);
		assertThat(explorer.getSelected().path()).isEqualTo("org");
		press(KeyCode.HOME, KeyCode.LEFT);
		assertThat(explorer.isExpanded(".")).isTrue(); // The root stays open
	}

	@Test
	void aModuleIsShownWithWhatItExportsRequiresAndProvides() {
		// tag::module[]
		explorer.select("org/nasdanika/sdk/runtime/volume");
		String screen = screen(100, 24);
		screenshot("explorer-module");

		assertThat(screen).contains("org.nasdanika.sdk.runtime.volume");
		assertThat(screen).contains("Exports").contains("- org.nasdanika.sdk.runtime.volume.http");
		assertThat(screen).contains("Requires").contains("- transitive org.nasdanika.sdk.runtime.common"); // <1>
		assertThat(screen).contains("Provides").contains("CapabilityFactory with"); // <2>
		// end::module[]
	}

	@Test
	void aModuleCanBeAFolderToo() {
		// tag::both[]
		Entry sql = modules.stat("java/sql").orElseThrow();
		assertThat(sql.kind()).isEqualTo(Entry.Kind.DIRECTORY); // <1>
		assertThat(modules.content("java/sql").isModule()).isTrue();

		explorer.select("java/sql");
		String screen = screen(100, 30);
		assertThat(screen).contains("- transitive java.logging"); // <2>
		assertThat(screen).contains("Under it").contains("rowset"); // <3>
		// end::both[]
	}

	@Test
	void aViewLongerThanItsPaneScrolls() {
		// tag::scroll[]
		explorer.select("org/nasdanika/sdk/runtime/volume");
		assertThat(screen(100, 12)).contains("rows 1-8 of 14").doesNotContain("Provides"); // <1>

		press(KeyCode.TAB); // <2>
		assertThat(explorer.getFocus()).isEqualTo(Focus.VIEW);
		press(KeyCode.END);
		assertThat(screen(100, 12)).contains("rows 7-14 of 14").contains("Provides");
		assertThat(explorer.getViewOffset()).isEqualTo(6);

		press(KeyCode.ESCAPE);
		assertThat(explorer.getFocus()).isEqualTo(Focus.TREE);
		press(KeyCode.UP); // <3>
		assertThat(explorer.getViewOffset()).isZero();
		// end::scroll[]
	}

	@Test
	void theTreeScrollsToItsSelection() {
		press(KeyCode.DOWN, KeyCode.RIGHT, KeyCode.END);
		explorer.select("org/nasdanika/sdk/runtime/volume");

		String screen = screen(100, 8); // Five rows for a tree of eleven
		assertThat(screen.lines().skip(1).filter(line -> line.contains("volume")).findFirst().orElseThrow()).contains("└─");
	}

	@Test
	void f4SaysThereIsNoEditorYet() {
		press(KeyCode.F4);
		assertThat(explorer.getStatus()).isEqualTo("No editor for jvm yet: viewing");
	}

	@Test
	void everyCommandHasALetterAndHelpClosesOnAnyKey() {
		type("h");
		assertThat(explorer.isHelpOpen()).isTrue();
		assertThat(screen(100, 24)).contains(" Keys ").contains("Any key closes");
		type("x");
		assertThat(explorer.isHelpOpen()).isFalse();

		type("v");
		assertThat(explorer.getFocus()).isEqualTo(Focus.VIEW);
		assertThat(screen(100, 24)).contains("3Tree");
		type("v");
		assertThat(explorer.getFocus()).isEqualTo(Focus.TREE);

		type("q");
		assertThat(explorer.isRunning()).isFalse();
	}

	@Test
	void aViewThatFailsIsShownAndNothingEnds() {
		// tag::failure[]
		explorer = new Explorer<>("jvm", modules, node -> {
			if (node.isModule()) {
				throw new IllegalStateException("no view for " + node.name());
			}
			return new ModuleViews<>(Kinds.of(TerminalContext.class)).view(node);
		});
		explorer.select("java/base");

		assertThat(screen(100, 24)).contains("Error: IllegalStateException: no view for java.base");
		press(KeyCode.UP);
		assertThat(screen(100, 24)).contains("logging").doesNotContain("Error"); // The folder above still has its view
		assertThat(explorer.isRunning()).isTrue();
		// end::failure[]
	}

	@Test
	void theModulesOfTheJvmThatRunsTheTest() {
		ModulesVolume jvm = ModulesVolume.ofBootLayer();

		assertThat(jvm.content("java/base").descriptor().name()).isEqualTo("java.base");
		assertThat(jvm.list(".")).extracting(Entry::path).contains("java");
		assertThat(jvm.stat("no/such/module")).isEmpty();
		assertThat(jvm.content(".").moduleCount()).isEqualTo(ModuleLayer.boot().modules().size());

		List<ModuleDescriptor> none = List.of();
		assertThat(new ModulesVolume(none).list(".")).isEmpty();
	}

}
