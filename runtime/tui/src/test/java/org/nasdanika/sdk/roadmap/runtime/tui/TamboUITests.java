package org.nasdanika.sdk.roadmap.runtime.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.nasdanika.sdk.roadmap.runtime.widget.ItemList;
import org.nasdanika.sdk.roadmap.runtime.widget.Kinds;
import org.nasdanika.sdk.roadmap.runtime.widget.Link;
import org.nasdanika.sdk.roadmap.runtime.widget.Section;
import org.nasdanika.sdk.roadmap.runtime.widget.Text;
import org.nasdanika.sdk.roadmap.runtime.widget.Variant;
import org.nasdanika.sdk.roadmap.runtime.widget.terminal.TerminalContext;
import org.nasdanika.sdk.roadmap.runtime.widget.terminal.TextStyle;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.style.Color;
import dev.tamboui.style.Modifier;

/**
 * TamboUI as a provider of the terminal context. The roadmap book's chapter on the terminal
 * includes its listings from here.
 */
class TamboUITests {

	@Test
	void neutralKindsServeTamboUIAndASpecificOneWins() {
		// tag::kinds[]
		Kinds<TamboUIContext> tamboui = Kinds.of(TamboUIContext.class);
		Kinds<TerminalContext> terminal = Kinds.of(TerminalContext.class);

		Text<TamboUIContext> text = tamboui.require(Text.class);
		Text<TerminalContext> neutralText = terminal.require(Text.class);
		assertThat((Object) text).isSameAs(neutralText); // <1>

		Section<TamboUIContext> section = tamboui.require(Section.class);
		Section<TerminalContext> neutralSection = terminal.require(Section.class);
		assertThat((Object) section).isNotSameAs(neutralSection); // <2>
		// end::kinds[]
	}

	@Test
	void aSectionIsABlockWithItsTitleOnTheBorder() {
		// tag::block[]
		Kinds<TamboUIContext> kinds = Kinds.of(TamboUIContext.class);
		Text<TamboUIContext> text = kinds.require(Text.class);
		Section<TamboUIContext> section = kinds.require(Section.class);
		ItemList<TamboUIContext> list = kinds.require(ItemList.class);

		Buffer buffer = Screenshots.draw(30, 6, context -> section.section(
				text.text("Accounts"),
				List.of(list.list(false, List.of(text.text("Checking"), text.text("Savings"))))).render(context));
		// end::block[]
		Screenshots.write(buffer, "Accounts", java.nio.file.Path.of(System.getProperty("roadmap.target", "target"), "screenshots", "section.svg"));

		assertThat(Screenshots.text(buffer).stripTrailing()).isEqualTo("""
				┌─Accounts───────────────────┐
				│ - Checking                 │
				│ - Savings                  │
				└────────────────────────────┘""");
	}

	@Test
	void logicalStylesBecomeTheTerminalsOwn() {
		// tag::styles[]
		Buffer buffer = Screenshots.buffer(20, 1);
		TamboUIContext context = new TamboUIContext(buffer);

		context.put(0, 0, "overdue", TextStyle.PLAIN.withVariant(Variant.DANGER).withBold()); // <1>

		assertThat(buffer.get(0, 0).symbol()).isEqualTo("o");
		assertThat(buffer.get(0, 0).style().fg()).contains(Color.RED);
		assertThat(buffer.get(0, 0).style().effectiveModifiers()).contains(Modifier.BOLD);
		// end::styles[]
	}

	@Test
	void aLinkIsAHyperlinkAndAnUnsafeTargetIsNot() {
		Kinds<TamboUIContext> kinds = Kinds.of(TamboUIContext.class);
		Text<TamboUIContext> text = kinds.require(Text.class);
		Link<TamboUIContext> link = kinds.require(Link.class);

		Buffer buffer = Screenshots.buffer(20, 2);
		TamboUIContext context = new TamboUIContext(buffer);
		link.link(text.text("Statements"), "https://example.org/statements").render(context.area(0, 0, 20, 1));
		link.link(text.text("Evil"), "https://example.org/\u001b]8;;x").render(context.area(0, 1, 20, 1));

		assertThat(buffer.get(0, 0).style().hyperlink()).isPresent();
		assertThat(buffer.get(0, 0).style().effectiveModifiers()).contains(Modifier.UNDERLINED);
		assertThat(buffer.get(0, 1).style().hyperlink()).isEmpty();
	}

	@Test
	void textIsClippedToTheArea() {
		Buffer buffer = Screenshots.buffer(10, 2);
		TamboUIContext area = new TamboUIContext(buffer).area(2, 0, 5, 1);

		area.put(-2, 0, "0123456789", TextStyle.PLAIN);
		area.put(0, 1, "below", TextStyle.PLAIN);

		assertThat(Screenshots.text(buffer).stripTrailing()).isEqualTo("  23456");
	}

	@Test
	void aScreenshotIsSvgThatLoadsNothing() {
		// tag::screenshot[]
		Buffer buffer = Screenshots.draw(30, 3, context -> context.put(1, 1, "Balance  1,200.00", TextStyle.PLAIN.withBold()));

		String svg = Screenshots.svg(buffer, "Accounts"); // <1>
		String selfContained = Screenshots.selfContained(svg); // <2>

		assertThat(svg).startsWith("<svg").contains("Accounts").contains("@font-face");
		assertThat(selfContained).startsWith("<svg").doesNotContain("@font-face").doesNotContain("https://cdnjs");
		assertThat(Screenshots.svg(buffer, "Accounts")).isEqualTo(svg); // <3>
		// end::screenshot[]
	}

}
