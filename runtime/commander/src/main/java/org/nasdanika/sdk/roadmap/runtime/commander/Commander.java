package org.nasdanika.sdk.roadmap.runtime.commander;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.nasdanika.sdk.roadmap.runtime.tui.TamboUIContext;
import org.nasdanika.sdk.roadmap.runtime.widget.Variant;
import org.nasdanika.sdk.roadmap.runtime.widget.terminal.TextStyle;
import org.nasdanika.sdk.runtime.volume.Content;
import org.nasdanika.sdk.runtime.volume.GenericVolume;
import org.nasdanika.sdk.runtime.volume.GenericVolume.Entry;
import org.nasdanika.sdk.runtime.volume.Volume;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.input.TextArea;
import dev.tamboui.widgets.input.TextAreaState;
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListWidget;

/**
 * Nasdanika Commander: two panels over volumes, a viewer, an editor, and the classic keys.
 *
 * <p>
 * This class is the whole application apart from the terminal: it takes key events and draws
 * frames. {@link CommanderMain} connects it to a terminal, and a test connects it to a buffer, which
 * is how the tests press keys and how the book's screenshots are taken.
 *
 * <p>
 * Every command also has a letter, for terminals that keep the function keys to themselves.
 */
public class Commander {

	/** What the screen shows under a dialog, if there is one. */
	public enum Mode {

		BROWSE,
		VIEW,
		EDIT

	}

	private enum Command {

		HELP,
		VIEW,
		EDIT,
		CREATE,
		COPY,
		MOVE,
		MKDIR,
		DELETE,
		REFRESH,
		QUIT,
		NONE

	}

	@FunctionalInterface
	private interface Action {

		void run(String input) throws IOException;

	}

	/**
	 * A box over the screen: a question with a line to type in, a question answered with yes or no,
	 * or a message.
	 *
	 * @param input The line to type in, or null
	 * @param action What a confirmation does, with what was typed. Null for a message, which any key closes
	 */
	private record Dialog(String title, List<String> lines, TextInputState input, Action action) {

	}

	private static final int MAX_TEXT = 8 * 1024 * 1024;

	private static final String HINT = "F1 or h for the keys";

	// tag::keys[]
	private static final List<String> HELP = List.of(
			"Tab             the other panel",
			"Enter           open a directory, view a file",
			"Backspace       up one directory",
			"F3  v           view",
			"F4  e           edit: F2 or Ctrl-S saves, Esc closes",
			"Shift-F4  n     create a file and edit it",
			"F5  c           copy to the other panel",
			"F6  m           move or rename",
			"F7  k           create a directory",
			"F8  d           delete",
			"Ctrl-R  r       read the panels again",
			"F10  q          quit");
	// end::keys[]

	private static final String[] BROWSE_KEYS = { "Help", "", "View", "Edit", "Copy", "Move", "MkDir", "Delete", "", "Quit" };
	private static final String[] VIEW_KEYS = { "", "", "Close", "Edit", "", "", "", "", "", "Close" };
	private static final String[] EDIT_KEYS = { "", "Save", "", "", "", "", "", "", "", "Close" };

	private final Panel left;
	private final Panel right;
	private Panel active;
	// Volatile: a test, or a caller on another thread, may ask where the application is
	private volatile Mode mode = Mode.BROWSE;
	private volatile Dialog dialog;
	private volatile String status = HINT;
	private volatile boolean running = true;

	// The viewer and the editor
	private TextAreaState text;
	private Panel textPanel;
	private String textPath;
	private String original;
	private String lineSeparator = "\n";
	private boolean newFile;
	private int textRows = 10;

	public Commander(Panel left, Panel right) throws IOException {
		this.left = Objects.requireNonNull(left, "left");
		this.right = Objects.requireNonNull(right, "right");
		this.active = left;
		left.refresh();
		right.refresh();
	}

	public boolean isRunning() {
		return running;
	}

	public Mode getMode() {
		return mode;
	}

	public boolean isDialogOpen() {
		return dialog != null;
	}

	public String getStatus() {
		return status;
	}

	public Panel getLeft() {
		return left;
	}

	public Panel getRight() {
		return right;
	}

	public Panel getActive() {
		return active;
	}

	private Panel other() {
		return active == left ? right : left;
	}

	// --- Keys --------------------------------------------------------------------------------

	/**
	 * Takes a key. A failure, such as a file that cannot be read, becomes the status line and never
	 * ends the application.
	 */
	// tag::handle[]
	public void handle(KeyEvent key) {
		if (key.isRelease()) {
			return;
		}
		try {
			if (dialog != null) {
				dialog(key);
			} else {
				switch (mode) {
					case BROWSE -> browse(key);
					case VIEW -> view(key);
					case EDIT -> edit(key);
				}
			}
		} catch (IOException | RuntimeException e) {
			status = "Error: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()); // <1>
		}
	}
	// end::handle[]

	// tag::command[]
	private static Command command(KeyEvent key) {
		switch (key.code()) {
			case F1:
				return Command.HELP;
			case F3:
				return Command.VIEW;
			case F4:
				return key.hasShift() ? Command.CREATE : Command.EDIT; // <1>
			case F16:
				return Command.CREATE; // <2>
			case F5:
				return Command.COPY;
			case F6:
				return Command.MOVE;
			case F7:
				return Command.MKDIR;
			case F8:
			case DELETE:
				return Command.DELETE;
			case F10:
				return Command.QUIT;
			case CHAR:
				if (key.hasAlt()) {
					return Command.NONE;
				}
				if (key.hasCtrl()) {
					return key.isCharIgnoreCase('r') ? Command.REFRESH : Command.NONE;
				}
				return switch (Character.toLowerCase(key.character())) { // <3>
					case 'h', '?' -> Command.HELP;
					case 'v' -> Command.VIEW;
					case 'e' -> Command.EDIT;
					case 'n' -> Command.CREATE;
					case 'c' -> Command.COPY;
					case 'm' -> Command.MOVE;
					case 'k' -> Command.MKDIR;
					case 'd' -> Command.DELETE;
					case 'r' -> Command.REFRESH;
					case 'q' -> Command.QUIT;
					default -> Command.NONE;
				};
			default:
				return Command.NONE;
		}
	}
	// end::command[]

	private void browse(KeyEvent key) throws IOException {
		status = HINT;
		switch (key.code()) {
			case TAB -> active = other();
			case UP -> active.move(-1);
			case DOWN -> active.move(1);
			case PAGE_UP -> active.page(-1);
			case PAGE_DOWN -> active.page(1);
			case HOME -> active.first();
			case END -> active.last();
			case BACKSPACE -> active.up();
			case ENTER -> {
				Entry selected = active.getSelected();
				if (active.isParentRowSelected()) {
					active.up();
				} else if (selected != null && selected.kind() == Entry.Kind.DIRECTORY) {
					active.enter(selected.path());
				} else {
					openText(selected, false);
				}
			}
			default -> {
				switch (command(key)) {
					case HELP -> dialog = new Dialog("Keys", HELP, null, null);
					case VIEW -> openText(active.getSelected(), false);
					case EDIT -> openText(active.getSelected(), true);
					case CREATE -> create();
					case COPY -> transfer(false);
					case MOVE -> transfer(true);
					case MKDIR -> createDirectory();
					case DELETE -> delete();
					case REFRESH -> refresh();
					case QUIT -> running = false;
					case NONE -> { }
				}
			}
		}
	}

	private void refresh() throws IOException {
		left.refresh();
		right.refresh();
	}

	// --- Commands on files -------------------------------------------------------------------

	private static String clean(String path) {
		String cleaned = path.trim().replace('\\', '/');
		while (cleaned.startsWith("/")) {
			cleaned = cleaned.substring(1);
		}
		while (cleaned.endsWith("/")) {
			cleaned = cleaned.substring(0, cleaned.length() - 1);
		}
		if (cleaned.isEmpty()) {
			throw new IllegalArgumentException("A name is needed");
		}
		return cleaned;
	}

	private Entry selectedOrExplain() {
		Entry selected = active.getSelected();
		if (selected == null) {
			status = "Select a file or a directory first";
		}
		return selected;
	}

	/**
	 * Copies or moves the selected entry to a path in the other panel's volume, asked for with the
	 * other panel's directory filled in. A target that exists is asked about before it is replaced.
	 */
	// tag::transfer[]
	private void transfer(boolean move) {
		Entry selected = selectedOrExplain();
		if (selected == null) {
			return;
		}
		Panel from = active;
		Panel to = other();
		if (!to.isWritable()) {
			status = "The other panel is read-only";
			return;
		}
		String name = Volumes.name(selected.path());
		String verb = move ? "Move" : "Copy";
		dialog = new Dialog(
				move ? "Move or rename" : "Copy",
				List.of(verb + " " + name + " to, in " + to.getTitle() + ":"),
				new TextInputState(Volumes.child(to.getDirectory(), name)), // <1>
				input -> {
					String target = clean(input);
					Action action = ignored -> {
						if (move) {
							Volumes.move(from.getVolume(), selected.path(), to.getVolume(), target); // <2>
							status = "Moved " + name + " to " + target;
						} else {
							int copied = Volumes.copy(from.getVolume(), selected.path(), to.getVolume(), target);
							status = "Copied " + copied + (copied == 1 ? " file to " : " files to ") + target;
						}
						refresh();
						to.select(Volumes.name(target));
					};
					boolean same = from.getVolume() == to.getVolume() && target.equals(selected.path());
					if (same) {
						status = "Source and target are the same";
					} else if (to.getVolume().stat(target).filter(existing -> existing.kind() != Entry.Kind.DIRECTORY).isPresent()) {
						dialog = new Dialog("Overwrite", List.of(target + " exists. Replace it?"), null, action); // <3>
					} else {
						action.run(null);
					}
				});
	}
	// end::transfer[]

	private void createDirectory() {
		if (!active.isWritable()) {
			status = "This panel is read-only";
			return;
		}
		Panel panel = active;
		dialog = new Dialog("Create a directory", List.of("Name, in " + panel.getDirectory() + ":"), new TextInputState(), input -> {
			String name = clean(input);
			Volumes.writable(panel.getVolume()).orElseThrow().createDirectory(Volumes.child(panel.getDirectory(), name));
			status = "Created " + name;
			refresh();
			panel.select(Volumes.name(name));
		});
	}

	private void delete() {
		Entry selected = selectedOrExplain();
		if (selected == null) {
			return;
		}
		if (!active.isWritable()) {
			status = "This panel is read-only";
			return;
		}
		Panel panel = active;
		String name = Volumes.name(selected.path());
		String question = selected.kind() == Entry.Kind.DIRECTORY ? "Delete the directory " + name + " and everything in it?" : "Delete " + name + "?";
		dialog = new Dialog("Delete", List.of(question), null, input -> {
			int deleted = Volumes.delete(panel.getVolume(), selected.path());
			status = "Deleted " + name + (deleted == 1 ? "" : ", " + deleted + " entries");
			refresh();
		});
	}

	private void create() {
		if (!active.isWritable()) {
			status = "This panel is read-only";
			return;
		}
		Panel panel = active;
		dialog = new Dialog("Create a file", List.of("Name, in " + panel.getDirectory() + ":"), new TextInputState(), input -> {
			String path = Volumes.child(panel.getDirectory(), clean(input));
			java.util.Optional<Entry> existing = panel.getVolume().stat(path);
			if (existing.isPresent()) {
				openText(existing.get(), true); // It exists: edit it
			} else {
				startText(panel, path, "", true); // <1>
				newFile = true;
			}
		});
	}

	// --- The viewer and the editor ------------------------------------------------------------

	private static String decode(byte[] bytes) throws CharacterCodingException {
		return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes))
				.toString();
	}

	// tag::open[]
	private void openText(Entry entry, boolean edit) throws IOException {
		if (entry == null || entry.kind() == Entry.Kind.DIRECTORY) {
			status = "Select a file first";
			return;
		}
		String name = Volumes.name(entry.path());
		if (entry.size() > MAX_TEXT) {
			status = name + " is too large to open here";
			return;
		}
		String decoded;
		try {
			decoded = decode(Volumes.read(active.getVolume(), entry.path())); // <1>
		} catch (CharacterCodingException e) {
			status = name + " is not UTF-8 text"; // <2>
			return;
		}
		lineSeparator = decoded.contains("\r\n") ? "\r\n" : "\n"; // <3>
		boolean writable = active.isWritable();
		startText(active, entry.path(), decoded.replace("\r\n", "\n"), edit && writable);
		if (edit && !writable) {
			status = "This panel is read-only: viewing";
		}
	}
	// end::open[]

	private void startText(Panel panel, String path, String content, boolean edit) {
		text = new TextAreaState(content);
		text.moveCursorToStart();
		original = text.text(); // What the editor holds: the measure of "changed"
		textPanel = panel;
		textPath = path;
		newFile = false;
		mode = edit ? Mode.EDIT : Mode.VIEW;
		if (edit) {
			status = "F2 or Ctrl-S saves, Esc closes";
		}
	}

	public boolean isModified() {
		return text != null && !text.text().equals(original);
	}

	// tag::save[]
	private void save() throws IOException {
		if (!newFile && !isModified()) {
			status = "No change, nothing written"; // <1>
			return;
		}
		GenericVolume.Writable<Content> writable = Volumes.writable(textPanel.getVolume()).orElseThrow(() -> new IOException("The volume is read-only"));
		String current = text.text();
		writable.write(textPath, Content.of(current.replace("\n", lineSeparator).getBytes(StandardCharsets.UTF_8))); // <2>
		original = current;
		newFile = false;
		status = "Saved " + Volumes.name(textPath);
		refresh();
		textPanel.select(Volumes.name(textPath));
	}
	// end::save[]

	private void closeText() {
		if (mode == Mode.EDIT && isModified()) {
			dialog = new Dialog("Close", List.of("Discard the changes to " + Volumes.name(textPath) + "?"), null, input -> {
				mode = Mode.BROWSE;
				status = "Changes discarded";
			});
		} else {
			mode = Mode.BROWSE;
			status = HINT;
		}
	}

	/** Moves around the text. Returns false for a key that does not. */
	private boolean navigate(KeyEvent key) {
		switch (key.code()) {
			case UP -> text.moveCursorUp();
			case DOWN -> text.moveCursorDown();
			case LEFT -> text.moveCursorLeft();
			case RIGHT -> text.moveCursorRight();
			case HOME -> {
				if (key.hasCtrl()) {
					text.moveCursorToStart();
				} else {
					text.moveCursorToLineStart();
				}
			}
			case END -> {
				if (key.hasCtrl()) {
					text.moveCursorToEnd();
				} else {
					text.moveCursorToLineEnd();
				}
			}
			case PAGE_UP -> {
				for (int i = 0; i < textRows; ++i) {
					text.moveCursorUp();
				}
			}
			case PAGE_DOWN -> {
				for (int i = 0; i < textRows; ++i) {
					text.moveCursorDown();
				}
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	private void view(KeyEvent key) throws IOException {
		if (navigate(key)) {
			return;
		}
		switch (key.code()) {
			case ESCAPE, F3, F10 -> closeText();
			case F4 -> switchToEdit();
			case CHAR -> {
				if (key.isCharIgnoreCase('q')) {
					closeText();
				} else if (key.isCharIgnoreCase('e')) {
					switchToEdit();
				}
			}
			default -> { }
		}
	}

	private void switchToEdit() {
		if (textPanel.isWritable()) {
			mode = Mode.EDIT;
			status = "F2 or Ctrl-S saves, Esc closes";
		} else {
			status = "This panel is read-only";
		}
	}

	private void edit(KeyEvent key) throws IOException {
		if (navigate(key)) {
			return;
		}
		switch (key.code()) {
			case ESCAPE, F10 -> closeText();
			case F2 -> save();
			case BACKSPACE -> text.deleteBackward();
			case DELETE -> text.deleteForward();
			case ENTER -> text.insert('\n');
			case TAB -> text.insert('\t');
			case CHAR -> {
				if (key.hasCtrl()) {
					if (key.isCharIgnoreCase('s')) {
						save();
					}
				} else if (!key.hasAlt()) {
					text.insert(key.string());
				}
			}
			default -> { }
		}
	}

	// --- Dialogs -----------------------------------------------------------------------------

	private void dialog(KeyEvent key) throws IOException {
		Dialog current = dialog;
		if (current.action() == null) {
			dialog = null; // A message: any key closes it
			return;
		}
		switch (key.code()) {
			case ESCAPE -> cancel();
			case ENTER -> confirm(current);
			default -> {
				TextInputState input = current.input();
				if (input == null) {
					if (key.isCharIgnoreCase('y')) {
						confirm(current);
					} else if (key.isCharIgnoreCase('n')) {
						cancel();
					}
					return;
				}
				switch (key.code()) {
					case LEFT -> input.moveCursorLeft();
					case RIGHT -> input.moveCursorRight();
					case HOME -> input.moveCursorToStart();
					case END -> input.moveCursorToEnd();
					case BACKSPACE -> input.deleteBackward();
					case DELETE -> input.deleteForward();
					case CHAR -> {
						if (!key.hasCtrl() && !key.hasAlt()) {
							input.insert(key.string());
						}
					}
					default -> { }
				}
			}
		}
	}

	private void cancel() {
		dialog = null;
		status = "Cancelled";
	}

	private void confirm(Dialog current) throws IOException {
		dialog = null; // First, so that the action can open another
		current.action().run(current.input() == null ? null : current.input().text());
	}

	// --- Drawing -----------------------------------------------------------------------------

	/**
	 * Draws the screen: the panels or the text, the status line, the key bar, and a dialog over
	 * them if one is open.
	 */
	// tag::render[]
	public void render(Frame frame) {
		Rect area = frame.area();
		List<Rect> rows = Layout.vertical().constraints(Constraint.fill(), Constraint.length(1), Constraint.length(1)).split(area);
		if (mode == Mode.BROWSE) {
			List<Rect> columns = Layout.horizontal().constraints(Constraint.percentage(50), Constraint.percentage(50)).split(rows.get(0));
			renderPanel(frame, left, columns.get(0));
			renderPanel(frame, right, columns.get(1));
		} else {
			renderText(frame, rows.get(0));
		}
		new TamboUIContext(frame.buffer(), rows.get(1)).put(1, 0, status, TextStyle.PLAIN.withVariant(status.startsWith("Error") ? Variant.DANGER : null)); // <1>
		renderKeys(new TamboUIContext(frame.buffer(), rows.get(2)));
		if (dialog != null) {
			renderDialog(frame, area);
		}
	}
	// end::render[]

	/** A title on a border, in bold: a style of its own, so that it is drawn apart from the border. */
	private static Title heading(String text) {
		return Title.from(Span.styled(text, Style.EMPTY.bold()));
	}

	private void renderPanel(Frame frame, Panel panel, Rect area) {
		boolean isActive = panel == active;
		List<ListItem> items = new ArrayList<>();
		for (String row: panel.rows(Math.max(1, area.width() - 3))) { // The borders, and the column of the selection
			ListItem item = ListItem.from(row);
			items.add(row.startsWith("/") ? item.style(Style.EMPTY.bold()) : item);
		}
		ListWidget widget = ListWidget.builder()
				.items(items.toArray(ListItem[]::new))
				.block(Block.builder()
						.title(heading(" " + panel.getTitle() + ": " + panel.getDirectory() + " "))
						.borders(Borders.ALL)
						.borderType(isActive ? BorderType.DOUBLE : BorderType.PLAIN)
						.build())
				.highlightSymbol(" ")
				.highlightStyle(isActive ? Style.EMPTY.reversed() : Style.EMPTY)
				.build();
		frame.renderStatefulWidget(widget, area, panel.getState());
		panel.setPageSize(area.height() - 2);
	}

	private void renderText(Frame frame, Rect area) {
		String title = (mode == Mode.EDIT ? " Edit " : " View ") + textPath + (isModified() ? " * " : " ");
		TextArea widget = TextArea.builder()
				.showLineNumbers(true)
				.block(Block.builder().title(heading(title)).borders(Borders.ALL).borderType(BorderType.DOUBLE).build())
				.build();
		if (mode == Mode.EDIT && dialog == null) {
			widget.renderWithCursor(area, frame.buffer(), text, frame);
		} else {
			frame.renderStatefulWidget(widget, area, text);
		}
		textRows = Math.max(1, area.height() - 2);
	}

	// tag::key-bar[]
	private void renderKeys(TamboUIContext bar) {
		String[] labels = switch (mode) {
			case BROWSE -> BROWSE_KEYS;
			case VIEW -> VIEW_KEYS;
			case EDIT -> EDIT_KEYS;
		};
		int cell = Math.max(6, bar.getColumns() / labels.length);
		for (int i = 0; i < labels.length; ++i) {
			String number = String.valueOf(i + 1);
			int width = cell - number.length() - 1;
			String label = labels[i].length() > width ? labels[i].substring(0, width) : labels[i];
			bar.put(i * cell, 0, number, TextStyle.PLAIN); // <1>
			bar.put(i * cell + number.length(), 0, String.format("%-" + width + "s", label), TextStyle.PLAIN.withReverse());
		}
	}
	// end::key-bar[]

	private void renderDialog(Frame frame, Rect area) {
		Dialog current = dialog;
		String hint = current.action() == null ? "Any key closes" : current.input() == null ? "Enter or y: yes    Esc or n: no" : "Enter: OK    Esc: cancel";
		int longest = Math.max(hint.length(), current.title().length() + 2);
		for (String line: current.lines()) {
			longest = Math.max(longest, line.length());
		}
		int width = Math.clamp(Math.max(longest + 4, current.input() == null ? 0 : 56), 12, Math.max(12, area.width() - 2));
		int height = Math.min(area.height(), current.lines().size() + (current.input() == null ? 0 : 2) + 4);
		Rect box = new Rect(area.x() + Math.max(0, (area.width() - width) / 2), area.y() + Math.max(0, (area.height() - height) / 2), Math.min(width, area.width()), height);

		frame.buffer().clear(box);
		frame.renderWidget(Block.builder().title(heading(" " + current.title() + " ")).borders(Borders.ALL).borderType(BorderType.DOUBLE).build(), box);
		TamboUIContext inside = new TamboUIContext(frame.buffer(), box).area(2, 1, box.width() - 4, box.height() - 2);
		int row = 0;
		for (String line: current.lines()) {
			inside.put(0, row++, line, TextStyle.PLAIN);
		}
		if (current.input() != null) {
			Rect field = new Rect(box.x() + 2, box.y() + 1 + row + 1, Math.max(1, box.width() - 4), 1);
			TextInput.builder().style(Style.EMPTY.underlined()).build().renderWithCursor(field, frame.buffer(), current.input(), frame);
		}
		inside.put(0, inside.getRows() - 1, hint, TextStyle.PLAIN.withVariant(Variant.SECONDARY));
	}

}
