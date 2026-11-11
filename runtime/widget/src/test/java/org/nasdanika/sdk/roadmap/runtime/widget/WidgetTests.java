package org.nasdanika.sdk.roadmap.runtime.widget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.nasdanika.sdk.roadmap.runtime.common.flow.Bindings;
import org.nasdanika.sdk.roadmap.runtime.common.flow.ValueBinding;
import org.nasdanika.sdk.roadmap.runtime.widget.markdown.GfmContext;
import org.nasdanika.sdk.roadmap.runtime.widget.markdown.MarkdownContext;
import org.nasdanika.sdk.roadmap.runtime.widget.markdown.MarkdownKinds;
import org.nasdanika.sdk.roadmap.runtime.widget.terminal.GridContext;
import org.nasdanika.sdk.roadmap.runtime.widget.terminal.TerminalContext;
import org.nasdanika.sdk.runtime.common.capability.CapabilityLoader;

/**
 * The widget API by example: kinds asked for by context type, the formats in the module, and the
 * rules that make one view safe in all of them. The roadmap book's chapter on widgets includes its
 * listings from here.
 */
class WidgetTests {

	record Account(String name, String balance) {

	}

	static final List<Account> ACCOUNTS = List.of(new Account("Checking", "1,200.00"), new Account("Savings | joint", "15,000.00"));

	@Test
	void aPlainStringIsNeverMarkup() {
		// tag::plain[]
		Text<MarkdownContext> text = Kinds.of(MarkdownContext.class).require(Text.class); // <1>
		Widget<?, MarkdownContext> widget = text.text("<script>alert(1)</script> *bold* [x](javascript:alert(1))"); // <2>

		StringBuilder out = new StringBuilder();
		widget.render(new MarkdownContext(out)); // <3>

		assertThat(out.toString()).isEqualTo("\\<script\\>alert(1)\\</script\\> \\*bold\\* \\[x\\](javascript:alert(1))\n\n");
		// end::plain[]
	}

	@Test
	void textThatLooksLikeAHeadingOrAListStaysText() {
		Text<MarkdownContext> text = Kinds.of(MarkdownContext.class).require(Text.class);
		StringBuilder out = new StringBuilder();
		text.text("# not a heading\n- not an item\n1. not a step\n2026. A year, said twice: 2026.").render(new MarkdownContext(out));

		assertThat(out.toString()).isEqualTo("\\# not a heading\n\\- not an item\n1\\. not a step\n2026\\. A year, said twice: 2026.\n\n");
	}

	@Test
	void textInTheTargetsFormatIsPassedThroughAndAnUnknownFormatIsReported() {
		// tag::formats[]
		Text<MarkdownContext> text = Kinds.of(MarkdownContext.class).require(Text.class);
		List<String> diagnostics = new ArrayList<>();
		StringBuilder out = new StringBuilder();
		MarkdownContext context = new MarkdownContext(out, 1, MarkdownContext.Mode.BLOCK, (severity, message) -> diagnostics.add(severity + ": " + message));

		text.text(() -> "Balance is **overdue**", Text.MARKDOWN).render(context); // <1>
		text.text(() -> "Balance is <b>overdue</b>", Text.HTML).render(context); // <2>

		assertThat(out.toString()).isEqualTo("Balance is **overdue**\n\nBalance is \\<b\\>overdue\\</b\\>\n\n");
		assertThat(diagnostics).containsExactly("WARN: No conversion from html to Markdown, rendered as plain text");
		// end::formats[]
	}

	/**
	 * A view: written once against kinds, for any context type that has them.
	 */
	// tag::view[]
	static <C> Widget<?, C> accounts(Kinds<C> kinds, Supplier<String> asOf, List<Account> accounts) {
		Text<C> text = kinds.require(Text.class);
		Link<C> link = kinds.require(Link.class);
		Section<C> section = kinds.require(Section.class);
		ItemList<C> list = kinds.require(ItemList.class);
		Optional<Table<C>> table = kinds.find(Table.class); // <1>

		Widget<?, C> balances = table
				.<Widget<?, C>>map(t -> t.table(
						List.of(
								new Table.Column<Account, C>(text.text("Account"), account -> text.text(account.name())),
								new Table.Column<Account, C>(text.text("Balance"), account -> text.text(account.balance()))),
						Table.Rows.of(accounts)))
				.orElseGet(() -> list.list(false, accounts.stream().map(account -> text.text(account.name() + ": " + account.balance())).toList())); // <2>

		return section.section(text.text("Accounts"), List.of(
				text.text(() -> "Balances as of " + asOf.get() + "."), // <3>
				balances,
				section.section(text.text("See also"), List.of(
						list.list(true, List.of(
								link.link(text.text("Statements"), "statements/index.md"),
								text.text("Closed accounts: none")))))));
	}
	// end::view[]

	@Test
	void oneViewInMarkdownWithoutTables() {
		// tag::markdown[]
		StringBuilder out = new StringBuilder();
		accounts(Kinds.of(MarkdownContext.class), () -> "2026-11-11", ACCOUNTS).render(new MarkdownContext(out));

		assertThat(out.toString()).isEqualTo("""
				# Accounts

				Balances as of 2026-11-11.

				- Checking: 1,200.00
				- Savings | joint: 15,000.00

				## See also

				1. [Statements](<statements/index.md>)
				2. Closed accounts: none

				""");
		// end::markdown[]
	}

	@Test
	void theSameViewInGitHubFlavoredMarkdownWithATable() {
		// tag::gfm[]
		StringBuilder out = new StringBuilder();
		accounts(Kinds.of(GfmContext.class), () -> "2026-11-11", ACCOUNTS).render(new GfmContext(out));

		assertThat(out.toString()).isEqualTo("""
				# Accounts

				Balances as of 2026-11-11.

				| Account | Balance |
				| --- | --- |
				| Checking | 1,200.00 |
				| Savings \\| joint | 15,000.00 |

				## See also

				1. [Statements](<statements/index.md>)
				2. Closed accounts: none

				""");
		// end::gfm[]
	}

	@Test
	void theSameViewOnATerminal() {
		// tag::terminal[]
		GridContext screen = new GridContext(44, 12);
		accounts(Kinds.of(TerminalContext.class), () -> "2026-11-11", ACCOUNTS).render(screen);

		assertThat(screen.toString()).isEqualTo("""
				Accounts
				  Balances as of 2026-11-11.
				  Account              Balance
				  -------------------- ---------------------
				  Checking             1,200.00
				  Savings | joint      15,000.00
				  See also
				    1. Statements
				    2. Closed accounts: none
				""");
		// end::terminal[]
	}

	@Test
	void aKindIsFoundAlongTheContextsClassHierarchy() {
		// tag::hierarchy[]
		Kinds<MarkdownContext> markdown = Kinds.of(MarkdownContext.class);
		Kinds<GfmContext> gfm = Kinds.of(GfmContext.class);

		Text<MarkdownContext> markdownText = markdown.require(Text.class);
		Text<GfmContext> gfmText = gfm.require(Text.class);
		assertThat((Object) gfmText).isSameAs(markdownText); // <1>

		assertThat(gfm.find(Table.class)).isPresent();
		assertThat(markdown.find(Table.class)).isEmpty(); // <2>
		assertThatThrownBy(() -> markdown.require(Table.class))
				.isInstanceOf(UnsupportedOperationException.class)
				.hasMessage("No Table for " + MarkdownContext.class.getName());
		// end::hierarchy[]
	}

	/** A format that can do better than the neutral kind says so by providing its own. */
	// tag::specific[]
	static final Text<GfmContext> SHOUTING = (value, format) -> context -> {
		context.emit(context.escape(value.get().toUpperCase()));
		return null;
	};

	static final class ShoutingKinds extends KindFactory {

		ShoutingKinds() {
			super(new KindProvider<>(Text.class, GfmContext.class, SHOUTING));
		}

	}

	@Test
	void aKindForAMoreSpecificContextWins() {
		CapabilityLoader loader = CapabilityLoader.of(new MarkdownKinds(), new ShoutingKinds()); // <1>

		Text<GfmContext> gfmText = Kinds.of(GfmContext.class, loader).require(Text.class);
		Text<MarkdownContext> markdownText = Kinds.of(MarkdownContext.class, loader).require(Text.class);

		assertThat((Object) gfmText).isSameAs(SHOUTING); // <2>
		assertThat((Object) markdownText).isNotSameAs(SHOUTING);
	}
	// end::specific[]

	@Test
	void aBindingIsAValue() {
		// tag::binding[]
		ValueBinding<String> asOf = Bindings.value("2026-11-11");
		Widget<?, TerminalContext> view = accounts(Kinds.of(TerminalContext.class), asOf, ACCOUNTS); // <1>

		GridContext first = new GridContext(44, 3);
		view.render(first);
		asOf.set("2026-11-12");
		GridContext second = new GridContext(44, 3);
		view.render(second); // <2>

		assertThat(first.toString()).contains("Balances as of 2026-11-11.");
		assertThat(second.toString()).contains("Balances as of 2026-11-12.");
		// end::binding[]
	}

	@Test
	void aValueCannotSendEscapeSequencesToATerminal() {
		// tag::sanitize[]
		Text<TerminalContext> text = Kinds.of(TerminalContext.class).require(Text.class);
		GridContext screen = new GridContext(30, 1);

		text.text("x\u001b]8;;http://evil\u0007link").render(screen); // <1>

		assertThat(screen.toString()).isEqualTo("x�]8;;http://evil�link\n");
		// end::sanitize[]
	}

	@Test
	void aWidgetCannotDrawOutsideItsArea() {
		Text<TerminalContext> text = Kinds.of(TerminalContext.class).require(Text.class);
		GridContext screen = new GridContext(20, 4);

		int rows = TerminalKindsAccess.rows(text.text("one two three four five six seven"), screen.area(2, 1, 9, 2));

		assertThat(screen.toString()).isEqualTo("\n  one two\n  three\n");
		assertThat(rows).isEqualTo(4); // It needed four rows and was given two
	}

	/** The rows a widget used, as the terminal kinds read them. */
	static final class TerminalKindsAccess {

		static int rows(Widget<?, ? super TerminalContext> widget, TerminalContext context) {
			return org.nasdanika.sdk.roadmap.runtime.widget.terminal.TerminalKinds.rows(widget, context);
		}

	}

	@Test
	void rowsBelowTheAreaAreNeverRead() {
		Kinds<TerminalContext> kinds = Kinds.of(TerminalContext.class);
		Text<TerminalContext> text = kinds.require(Text.class);
		Table<TerminalContext> table = kinds.require(Table.class);
		List<Integer> read = new ArrayList<>();
		Table.Rows<Integer> million = new Table.Rows<>() {

			@Override
			public int size() {
				return 1_000_000;
			}

			@Override
			public Integer get(int index) {
				read.add(index);
				return index;
			}

		};

		table.table(List.of(new Table.Column<Integer, TerminalContext>(text.text("Row"), row -> text.text("row " + row))), million).render(new GridContext(20, 5));

		assertThat(read).containsExactly(0, 1, 2);
	}

}
