package org.nasdanika.sdk.roadmap.runtime.explorer.jvm;

import java.lang.module.ModuleDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.nasdanika.sdk.roadmap.runtime.widget.ItemList;
import org.nasdanika.sdk.roadmap.runtime.widget.Kinds;
import org.nasdanika.sdk.roadmap.runtime.widget.Section;
import org.nasdanika.sdk.roadmap.runtime.widget.Table;
import org.nasdanika.sdk.roadmap.runtime.widget.Text;
import org.nasdanika.sdk.roadmap.runtime.widget.Widget;

/**
 * The views of a {@link ModuleNode}: a module as its name with what it exports, requires and
 * provides, and a folder as a table of what is in it. They name no format. They ask for kinds, so
 * the view the explorer shows in a terminal is also a page of Markdown, and later of AsciiDoc.
 *
 * @param <C> The type of the context the views render into
 */
public final class ModuleViews<C> {

	private final Text<C> text;
	private final Section<C> section;
	private final ItemList<C> list;
	private final Optional<Table<C>> table;

	public ModuleViews(Kinds<C> kinds) {
		text = kinds.require(Text.class);
		section = kinds.require(Section.class);
		list = kinds.require(ItemList.class);
		table = kinds.find(Table.class);
	}

	/**
	 * The view of a place: what the module is, if it is one, and what is under it, if anything is.
	 */
	// tag::view[]
	public Widget<?, C> view(ModuleNode node) {
		List<Widget<?, C>> body = new ArrayList<>();
		if (node.isModule()) {
			body.addAll(directives(node.descriptor())); // <1>
		}
		if (node.isFolder()) {
			Widget<?, C> contents = contents(node);
			body.add(node.isModule() ? section.section(text.text("Under it"), List.of(contents)) : contents); // <2>
		}
		String title = node.name().isEmpty() ? "Modules" : node.name();
		return section.section(text.text(title), body); // <3>
	}
	// end::view[]

	// tag::module[]
	private List<Widget<?, C>> directives(ModuleDescriptor module) {
		List<String> exports = module.exports().stream()
				.map(export -> export.source() + (export.isQualified() ? " to " + join(export.targets()) : ""))
				.sorted()
				.toList();
		List<String> requires = module.requires().stream()
				.sorted() // <1>
				.map(require -> require.modifiers().stream()
						.map(modifier -> modifier.name().toLowerCase() + " ")
						.sorted()
						.collect(Collectors.joining()) + require.name())
				.toList();
		List<String> provides = module.provides().stream()
				.map(provide -> provide.service() + " with " + join(provide.providers()))
				.sorted()
				.toList();
		return List.of(listed("Exports", exports), listed("Requires", requires), listed("Provides", provides));
	}

	private Widget<?, C> listed(String title, List<String> items) {
		Widget<?, C> body = items.isEmpty() ? text.text("None") : list.list(false, items.stream().map(text::text).toList());
		return section.section(text.text(title), List.of(body));
	}
	// end::module[]

	// tag::folder[]
	private Widget<?, C> contents(ModuleNode folder) {
		return table
				.<Widget<?, C>>map(kind -> kind.table(
						List.of(
								new Table.Column<ModuleNode, C>(text.text("Name"), child -> text.text(name(child)), 4),
								new Table.Column<ModuleNode, C>(text.text("Modules"), child -> text.text(modules(child)), 2), // <1>
								new Table.Column<ModuleNode, C>(text.text("Requires"), child -> text.text(String.valueOf(child.requiresCount())), 2),
								new Table.Column<ModuleNode, C>(text.text("Provides"), child -> text.text(String.valueOf(child.providesCount())), 2)),
						Table.Rows.of(folder.children())))
				.orElseGet(() -> list.list(false, folder.children().stream() // <2>
						.map(child -> text.text(name(child) + ": " + child.requiresCount() + " requires, " + child.providesCount() + " provides"))
						.toList()));
	}

	/** A folder is shown as the commander shows a directory, with a slash before its name. */
	private static String name(ModuleNode node) {
		return node.isFolder() ? "/" + node.label() : node.label();
	}

	/** The number of modules is for folders. A module that is not a folder leaves the cell empty. */
	private static String modules(ModuleNode node) {
		return node.isFolder() ? String.valueOf(node.moduleCount()) : "";
	}
	// end::folder[]

	private static String join(java.util.Collection<String> names) {
		return names.stream().sorted().collect(Collectors.joining(", "));
	}

}
