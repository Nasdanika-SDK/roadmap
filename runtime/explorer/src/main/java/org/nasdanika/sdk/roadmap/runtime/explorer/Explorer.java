package org.nasdanika.sdk.roadmap.runtime.explorer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.nasdanika.sdk.roadmap.runtime.tui.TamboUIContext;
import org.nasdanika.sdk.roadmap.runtime.widget.Variant;
import org.nasdanika.sdk.roadmap.runtime.widget.Widget;
import org.nasdanika.sdk.roadmap.runtime.widget.terminal.TerminalKinds;
import org.nasdanika.sdk.roadmap.runtime.widget.terminal.TextStyle;
import org.nasdanika.sdk.runtime.volume.GenericVolume;
import org.nasdanika.sdk.runtime.volume.GenericVolume.Entry;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.tree.GuideStyle;
import dev.tamboui.widgets.tree.TreeState;
import dev.tamboui.widgets.tree.TreeWidget;

/**
 * Nasdanika Explorer: a tree on the left and, on the right, a view of what is selected in it.
 *
 * <p>
 * The tree is a {@link GenericVolume}: a tree of entries whose content is whatever the volume
 * holds, a module of a JVM, an element of a model, a shape of a diagram. The view of a content is
 * a {@link Widget}, made by a function the explorer is given, so the explorer knows neither what it
 * shows nor how. How the function chooses a view, by the class of the content for instance, is the
 * caller's business.
 *
 * <p>
 * Like the commander, this class is the whole application apart from the terminal: it takes key
 * events and draws frames. {@link ExplorerMain} connects it to a terminal and a test connects it to
 * a buffer.
 *
 * @param <C> The type of content of the volume
 */
public class Explorer<C> {

	/** Which pane takes the keys. */
	public enum Focus {

		TREE,
		VIEW

	}

	private enum Command {

		HELP,
		VIEW,
		EDIT,
		REFRESH,
		QUIT,
		NONE

	}

	private static final String ROOT = ".";

	private static final String HINT = "F1 or h for the keys";

	// tag::keys[]
	private static final List<String> HELP = List.of(
			"Up, Down        move in the tree, scroll the view",
			"Right           open a folder, then go into it",
			"Left            close a folder, or go to the one above",
			"Enter           open or close a folder, go to the view of a leaf",
			"Backspace       the folder above",
			"Tab             the other pane",
			"F3  v           the view, and back to the tree",
			"F4  e           edit, where an editor exists",
			"Ctrl-R  r       read the tree again",
			"F10  q          quit");
	// end::keys[]

	private static final String[] TREE_KEYS = { "Help", "", "View", "Edit", "", "", "", "", "", "Quit" };
	private static final String[] VIEW_KEYS = { "Help", "", "Tree", "Edit", "", "", "", "", "", "Quit" };

	/** TamboUI's markers of a closed and an open node, which not every font has. */
	private static final String CLOSED = "▶";
	private static final String OPEN = "▼";

	/** A view longer than this is cut: the prototype draws a view whole, then shows a window of it. */
	private static final int MAX_VIEW_ROWS = 4096;

	private final String title;
	private final GenericVolume<C> volume;
	private final Function<? super C, ? extends Widget<?, ? super TamboUIContext>> viewer;
	private final Entry root = new Entry(ROOT, Entry.Kind.DIRECTORY, -1, null, null);

	// The tree
	private final Set<String> expanded = new HashSet<>();
	private final Map<String, List<Entry>> children = new HashMap<>();
	private final TreeState tree = new TreeState();
	private List<Entry> rows = List.of();
	private int treePage = 10;

	// The view: drawn whole into a buffer of its own when the selection or the width changes
	private Buffer view;
	private String viewKey;
	private int viewRows;
	private int viewOffset;
	private int viewPage = 10;

	// Volatile: a test, or a caller on another thread, may ask where the application is
	private volatile Focus focus = Focus.TREE;
	private volatile boolean help;
	private volatile String status = HINT;
	private volatile boolean running = true;

	/**
	 * @param title What the root of the tree is called, such as "jvm" or the name of a diagram
	 * @param volume What is explored
	 * @param viewer The view of a content, for the right pane
	 */
	// tag::constructor[]
	public Explorer(
			String title,
			GenericVolume<C> volume,
			Function<? super C, ? extends Widget<?, ? super TamboUIContext>> viewer) { // <1>

		this.title = Objects.requireNonNull(title, "title");
		this.volume = Objects.requireNonNull(volume, "volume");
		this.viewer = Objects.requireNonNull(viewer, "viewer");
		expanded.add(ROOT);
		flatten();
	}
	// end::constructor[]

	public boolean isRunning() {
		return running;
	}

	public Focus getFocus() {
		return focus;
	}

	public boolean isHelpOpen() {
		return help;
	}

	public String getStatus() {
		return status;
	}

	/** The entry selected in the tree. The root's path is {@code .}. */
	public Entry getSelected() {
		return rows.get(Math.clamp(tree.selected(), 0, rows.size() - 1));
	}

	public boolean isExpanded(String path) {
		return expanded.contains(path);
	}

	/** The first row of the view that is shown. */
	public int getViewOffset() {
		return viewOffset;
	}

	/**
	 * Selects the entry at a path, opening the folders above it.
	 *
	 * @return false if nothing is at the path
	 */
	public boolean select(String path) {
		if (!ROOT.equals(path) && volume.stat(path).isEmpty()) {
			return false;
		}
		for (String parent = parent(path); parent != null; parent = parent(parent)) {
			expanded.add(parent);
		}
		flatten();
		for (int i = 0; i < rows.size(); ++i) {
			if (rows.get(i).path().equals(path)) {
				moveTo(i);
				return true;
			}
		}
		return false;
	}

	// --- The tree ----------------------------------------------------------------------------

	private static String parent(String path) {
		if (ROOT.equals(path)) {
			return null;
		}
		int slash = path.lastIndexOf('/');
		return slash < 0 ? ROOT : path.substring(0, slash);
	}

	private String label(Entry entry) {
		String path = entry.path();
		return ROOT.equals(path) ? title : path.substring(path.lastIndexOf('/') + 1);
	}

	/** The entries of a folder, read once and kept until the tree is read again. */
	// tag::children[]
	private List<Entry> children(Entry entry) {
		if (entry.kind() != Entry.Kind.DIRECTORY) {
			return List.of();
		}
		return children.computeIfAbsent(entry.path(), path -> {
			try {
				return List.copyOf(volume.list(path)); // <1>
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}
	// end::children[]

	/** The rows of the tree as they are shown: a folder that is open is followed by what is in it. */
	private void flatten() {
		List<Entry> flat = new ArrayList<>();
		flatten(root, flat);
		rows = flat;
	}

	private void flatten(Entry entry, List<Entry> flat) {
		flat.add(entry);
		if (expanded.contains(entry.path())) {
			for (Entry child: children(entry)) {
				flatten(child, flat);
			}
		}
	}

	private void moveTo(int row) {
		int target = Math.clamp(row, 0, rows.size() - 1);
		if (target != tree.selected()) {
			tree.select(target);
			viewOffset = 0;
		}
	}

	private void setExpanded(Entry entry, boolean open) {
		if (entry.kind() != Entry.Kind.DIRECTORY) {
			return;
		}
		if (open) {
			expanded.add(entry.path());
		} else if (!ROOT.equals(entry.path())) { // The root stays open: closed, it would show nothing
			expanded.remove(entry.path());
		}
		flatten();
	}

	private void selectParent() {
		String parent = parent(getSelected().path());
		for (int i = 0; parent != null && i < rows.size(); ++i) {
			if (rows.get(i).path().equals(parent)) {
				moveTo(i);
			}
		}
	}

	private void refresh() {
		String selected = getSelected().path();
		children.clear();
		viewKey = null;
		flatten();
		if (!select(selected)) {
			moveTo(tree.selected());
		}
	}

	// --- Keys --------------------------------------------------------------------------------

	/**
	 * Takes a key. A failure, such as a folder that cannot be listed, becomes the status line and
	 * never ends the application.
	 */
	public void handle(KeyEvent key) {
		if (key.isRelease()) {
			return;
		}
		try {
			if (help) {
				help = false; // Any key closes
			} else if (focus == Focus.TREE) {
				tree(key);
			} else {
				view(key);
			}
		} catch (RuntimeException e) {
			status = error(e);
		}
	}

	private static String error(Exception e) {
		Throwable cause = e instanceof UncheckedIOException ? e.getCause() : e;
		return "Error: " + cause.getClass().getSimpleName() + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
	}

	private static Command command(KeyEvent key) {
		switch (key.code()) {
			case F1:
				return Command.HELP;
			case F3:
				return Command.VIEW;
			case F4:
				return Command.EDIT;
			case F10:
				return Command.QUIT;
			case CHAR:
				if (key.hasAlt()) {
					return Command.NONE;
				}
				if (key.hasCtrl()) {
					return key.isCharIgnoreCase('r') ? Command.REFRESH : Command.NONE;
				}
				return switch (Character.toLowerCase(key.character())) {
					case 'h', '?' -> Command.HELP;
					case 'v' -> Command.VIEW;
					case 'e' -> Command.EDIT;
					case 'r' -> Command.REFRESH;
					case 'q' -> Command.QUIT;
					default -> Command.NONE;
				};
			default:
				return Command.NONE;
		}
	}

	// tag::tree-keys[]
	private void tree(KeyEvent key) {
		status = HINT;
		Entry selected = getSelected();
		boolean folder = selected.kind() == Entry.Kind.DIRECTORY;
		boolean open = expanded.contains(selected.path());
		switch (key.code()) {
			case UP -> moveTo(tree.selected() - 1);
			case DOWN -> moveTo(tree.selected() + 1);
			case PAGE_UP -> moveTo(tree.selected() - treePage);
			case PAGE_DOWN -> moveTo(tree.selected() + treePage);
			case HOME -> moveTo(0);
			case END -> moveTo(rows.size() - 1);
			case RIGHT -> {
				if (folder && !open) {
					setExpanded(selected, true);
				} else if (folder && !children(selected).isEmpty()) {
					moveTo(tree.selected() + 1); // <1>
				}
			}
			case LEFT -> {
				if (folder && open && !ROOT.equals(selected.path())) {
					setExpanded(selected, false);
				} else {
					selectParent(); // <2>
				}
			}
			case BACKSPACE -> selectParent();
			case ENTER -> {
				if (folder) {
					setExpanded(selected, !open);
				} else {
					focus = Focus.VIEW;
				}
			}
			case TAB -> focus = Focus.VIEW;
			default -> command(command(key));
		}
	}
	// end::tree-keys[]

	private void view(KeyEvent key) {
		status = HINT;
		switch (key.code()) {
			case UP -> viewOffset = Math.max(0, viewOffset - 1);
			case DOWN -> ++viewOffset; // Held to the length of the view when it is drawn
			case PAGE_UP -> viewOffset = Math.max(0, viewOffset - viewPage);
			case PAGE_DOWN -> viewOffset += viewPage;
			case HOME -> viewOffset = 0;
			case END -> viewOffset = MAX_VIEW_ROWS;
			case TAB, ESCAPE, LEFT -> focus = Focus.TREE;
			default -> command(command(key));
		}
	}

	private void command(Command command) {
		switch (command) {
			case HELP -> help = true;
			case VIEW -> focus = focus == Focus.TREE ? Focus.VIEW : Focus.TREE;
			case EDIT -> status = "No editor for " + label(getSelected()) + " yet: viewing"; // The prototype only views
			case REFRESH -> refresh();
			case QUIT -> running = false;
			case NONE -> { }
		}
	}

	// --- Drawing -----------------------------------------------------------------------------

	/**
	 * Draws the screen: the tree, the view, the status line, the key bar, and the keys over them
	 * if help is open.
	 */
	// tag::render[]
	public void render(Frame frame) {
		Rect area = frame.area();
		List<Rect> bands = Layout.vertical().constraints(Constraint.fill(), Constraint.length(1), Constraint.length(1)).split(area);
		List<Rect> panes = Layout.horizontal().constraints(Constraint.percentage(35), Constraint.percentage(65)).split(bands.get(0));
		renderTree(frame, panes.get(0));
		String position = renderView(frame, panes.get(1));

		TamboUIContext line = new TamboUIContext(frame.buffer(), bands.get(1));
		line.put(1, 0, status, TextStyle.PLAIN.withVariant(status.startsWith("Error") ? Variant.DANGER : null));
		line.put(line.getColumns() - position.length() - 1, 0, position, TextStyle.PLAIN.withVariant(Variant.SECONDARY)); // <1>
		renderKeys(new TamboUIContext(frame.buffer(), bands.get(2)));
		if (help) {
			renderHelp(frame, area);
		}
	}
	// end::render[]

	/** A title on a border, in bold: a style of its own, so that it is drawn apart from the border. */
	private static Title heading(String text) {
		return Title.from(Span.styled(text, Style.EMPTY.bold()));
	}

	private static Block pane(String title, boolean focused) {
		return Block.builder()
				.title(heading(" " + title + " "))
				.borders(Borders.ALL)
				.borderType(focused ? BorderType.DOUBLE : BorderType.PLAIN)
				.build();
	}

	// tag::tree[]
	private void renderTree(Frame frame, Rect area) {
		boolean focused = focus == Focus.TREE;
		TreeWidget<Entry> widget = TreeWidget.<Entry>builder()
				.roots(root)
				.children(this::children) // <1>
				.isLeaf(entry -> entry.kind() != Entry.Kind.DIRECTORY)
				.expansionState(entry -> expanded.contains(entry.path()), this::setExpanded) // <2>
				.simpleNodeRenderer(entry -> Paragraph.from(label(entry)))
				.guideStyle(GuideStyle.UNICODE)
				.indentWidth(2)
				.leafIndicator("  ")
				.highlightSymbol(" ")
				.highlightStyle(focused ? Style.EMPTY.reversed() : Style.EMPTY.bold())
				.block(pane(title, focused))
				.build();
		frame.renderStatefulWidget(widget, area, tree); // <3>
		plainMarkers(frame.buffer(), area);
		treePage = Math.max(1, area.height() - 2);
	}
	// end::tree[]

	/**
	 * Replaces TamboUI's markers of closed and open nodes, two triangles, with a plus and a minus,
	 * which every font has. TamboUI 0.7.0 has no option for them.
	 */
	// tag::markers[]
	private static void plainMarkers(Buffer buffer, Rect area) {
		for (int y = area.y() + 1; y < area.y() + area.height() - 1; ++y) {
			for (int x = area.x() + 1; x < area.x() + area.width() - 1; ++x) {
				Cell cell = buffer.get(x, y);
				if (CLOSED.equals(cell.symbol()) || OPEN.equals(cell.symbol())) {
					buffer.set(x, y, cell.symbol(CLOSED.equals(cell.symbol()) ? "+" : "-"));
					break; // The marker is the first one on its row: a label keeps its own
				}
			}
		}
	}
	// end::markers[]

	/**
	 * Draws the view of the selection and returns where the pane is in it, such as "rows 1-18 of
	 * 60", or nothing when the pane shows all of it.
	 */
	// tag::view[]
	private String renderView(Frame frame, Rect area) {
		Entry selected = getSelected();
		frame.renderWidget(pane(ROOT.equals(selected.path()) ? title : title + ": " + selected.path(), focus == Focus.VIEW), area);
		Rect inner = new Rect(area.x() + 2, area.y() + 1, Math.max(0, area.width() - 4), Math.max(0, area.height() - 2));
		if (inner.width() == 0 || inner.height() == 0) {
			return "";
		}

		String key = selected.path() + "@" + inner.width();
		if (!key.equals(viewKey)) { // <1>
			draw(selected, inner.width());
			viewKey = key;
		}
		viewPage = inner.height();
		viewOffset = Math.clamp(viewOffset, 0, Math.max(0, viewRows - inner.height()));
		int shown = Math.min(inner.height(), viewRows - viewOffset);
		for (int y = 0; y < shown; ++y) { // <2>
			for (int x = 0; x < inner.width(); ++x) {
				frame.buffer().set(inner.x() + x, inner.y() + y, view.get(x, viewOffset + y));
			}
		}
		return viewRows > inner.height() ? "rows " + (viewOffset + 1) + "-" + (viewOffset + shown) + " of " + viewRows : "";
	}

	/** Draws the whole view of an entry into a buffer of its own, as tall as the view turns out to be. */
	private void draw(Entry entry, int columns) {
		for (int height = 256; ; height *= 4) {
			view = Buffer.empty(Rect.of(columns, height));
			TamboUIContext context = new TamboUIContext(view);
			try {
				Widget<?, ? super TamboUIContext> widget = viewer.apply(volume.content(entry.path())); // <3>
				viewRows = Math.min(height, TerminalKinds.rows(widget, context));
			} catch (RuntimeException e) {
				view.clear();
				context.put(0, 0, error(e), TextStyle.PLAIN.withVariant(Variant.DANGER)); // <4>
				viewRows = 1;
			}
			if (viewRows < height || height >= MAX_VIEW_ROWS) {
				return;
			}
		}
	}
	// end::view[]

	private void renderKeys(TamboUIContext bar) {
		String[] labels = focus == Focus.TREE ? TREE_KEYS : VIEW_KEYS;
		int cell = Math.max(6, bar.getColumns() / labels.length);
		for (int i = 0; i < labels.length; ++i) {
			String number = String.valueOf(i + 1);
			int width = cell - number.length() - 1;
			String label = labels[i].length() > width ? labels[i].substring(0, width) : labels[i];
			bar.put(i * cell, 0, number, TextStyle.PLAIN);
			bar.put(i * cell + number.length(), 0, String.format("%-" + width + "s", label), TextStyle.PLAIN.withReverse());
		}
	}

	private void renderHelp(Frame frame, Rect area) {
		String hint = "Any key closes";
		int longest = hint.length();
		for (String line: HELP) {
			longest = Math.max(longest, line.length());
		}
		int width = Math.clamp(longest + 4, 12, Math.max(12, area.width() - 2));
		int height = Math.min(area.height(), HELP.size() + 4);
		Rect box = new Rect(area.x() + Math.max(0, (area.width() - width) / 2), area.y() + Math.max(0, (area.height() - height) / 2), Math.min(width, area.width()), height);

		frame.buffer().clear(box);
		frame.renderWidget(Block.builder().title(heading(" Keys ")).borders(Borders.ALL).borderType(BorderType.DOUBLE).build(), box);
		TamboUIContext inside = new TamboUIContext(frame.buffer(), box).area(2, 1, box.width() - 4, box.height() - 2);
		int row = 0;
		for (String line: HELP) {
			inside.put(0, row++, line, TextStyle.PLAIN);
		}
		inside.put(0, inside.getRows() - 1, hint, TextStyle.PLAIN.withVariant(Variant.SECONDARY));
	}

}
