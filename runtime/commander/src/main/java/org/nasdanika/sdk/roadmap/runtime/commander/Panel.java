package org.nasdanika.sdk.roadmap.runtime.commander;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.nasdanika.sdk.runtime.volume.GenericVolume.Entry;
import org.nasdanika.sdk.runtime.volume.Volume;

import dev.tamboui.widgets.list.ListState;

/**
 * One panel of the commander: a volume, the directory shown, its entries, and which one is
 * selected. The panel knows nothing about files on disk. Whatever is a volume can be a panel.
 */
public class Panel {

	private static final DateTimeFormatter MODIFIED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

	private static final Comparator<Entry> ORDER = Comparator
			.<Entry, Boolean>comparing(entry -> entry.kind() != Entry.Kind.DIRECTORY) // Directories first
			.thenComparing(entry -> Volumes.name(entry.path()), String.CASE_INSENSITIVE_ORDER);

	private final String title;
	private final Volume volume;
	private final ListState state = new ListState();
	private String directory;
	private List<Entry> entries = new ArrayList<>();
	private int pageSize = 10;

	/**
	 * @param title What the panel is called in its border, such as "files" or "jvm"
	 * @param directory The directory to start in: {@code .} for the root of the volume
	 */
	public Panel(String title, Volume volume, String directory) {
		this.title = Objects.requireNonNull(title, "title");
		this.volume = Objects.requireNonNull(volume, "volume");
		this.directory = Objects.requireNonNull(directory, "directory");
	}

	public String getTitle() {
		return title;
	}

	public Volume getVolume() {
		return volume;
	}

	public String getDirectory() {
		return directory;
	}

	public List<Entry> getEntries() {
		return entries;
	}

	ListState getState() {
		return state;
	}

	void setPageSize(int pageSize) {
		this.pageSize = Math.max(1, pageSize);
	}

	public boolean isWritable() {
		return Volumes.writable(volume).isPresent();
	}

	/** Whether the first row is the way up. The root of a volume has none. */
	boolean hasParentRow() {
		return !".".equals(directory);
	}

	/** The number of rows: the entries, and the way up where there is one. */
	int rowCount() {
		return entries.size() + (hasParentRow() ? 1 : 0);
	}

	/**
	 * Reads the directory again, keeping the selection on the same row where it can.
	 */
	// tag::refresh[]
	public void refresh() throws IOException {
		List<Entry> listed = new ArrayList<>(volume.list(directory)); // <1>
		listed.sort(ORDER);
		entries = listed;
		int rows = rowCount();
		Integer selected = state.selected();
		state.select(rows == 0 ? null : Math.clamp(selected == null ? 0 : selected, 0, rows - 1));
	}
	// end::refresh[]

	/**
	 * The selected entry, or null when nothing is selected or the way up is.
	 */
	public Entry getSelected() {
		Integer selected = state.selected();
		if (selected == null) {
			return null;
		}
		int index = selected - (hasParentRow() ? 1 : 0);
		return index < 0 || index >= entries.size() ? null : entries.get(index);
	}

	boolean isParentRowSelected() {
		return hasParentRow() && Integer.valueOf(0).equals(state.selected());
	}

	/** Puts the selection on the entry with the name, if the directory has one. */
	public void select(String name) {
		for (int i = 0; i < entries.size(); ++i) {
			if (Volumes.name(entries.get(i).path()).equals(name)) {
				state.select(i + (hasParentRow() ? 1 : 0));
				return;
			}
		}
	}

	public void enter(String path) throws IOException {
		String previous = directory;
		directory = path;
		try {
			state.select(0);
			refresh();
		} catch (IOException | RuntimeException e) {
			directory = previous; // A directory that cannot be read is not entered
			refresh();
			throw e;
		}
	}

	/** Goes up one directory and selects the directory it came from. */
	public void up() throws IOException {
		if (hasParentRow()) {
			String from = Volumes.name(directory);
			enter(Volumes.parent(directory));
			select(from);
		}
	}

	void move(int rows) {
		int count = rowCount();
		if (count > 0) {
			Integer selected = state.selected();
			state.select(Math.clamp((selected == null ? 0 : selected) + rows, 0, count - 1));
		}
	}

	void page(int pages) {
		move(pages * pageSize);
	}

	void first() {
		if (rowCount() > 0) {
			state.select(0);
		}
	}

	void last() {
		if (rowCount() > 0) {
			state.select(rowCount() - 1);
		}
	}

	/**
	 * The rows of the panel as text of a width: the name, and the size and the time of the last
	 * change where the width allows. A directory is marked with a slash.
	 */
	List<String> rows(int width) {
		boolean time = width >= 46;
		boolean size = width >= 24;
		int nameWidth = Math.max(1, width - (size ? 8 : 0) - (time ? 17 : 0));
		List<String> rows = new ArrayList<>();
		if (hasParentRow()) {
			rows.add("/..");
		}
		for (Entry entry: entries) {
			boolean isDirectory = entry.kind() == Entry.Kind.DIRECTORY;
			String name = (isDirectory ? "/" : " ") + Volumes.name(entry.path());
			if (name.length() > nameWidth) {
				name = name.substring(0, Math.max(0, nameWidth - 1)) + "~";
			}
			StringBuilder row = new StringBuilder(String.format("%-" + nameWidth + "s", name));
			if (size) {
				row.append(String.format("%8s", isDirectory ? "" : size(entry.size())));
			}
			if (time) {
				row.append(' ').append(entry.modified() == null ? " ".repeat(16) : MODIFIED.format(entry.modified()));
			}
			rows.add(row.toString());
		}
		return rows;
	}

	static String size(long bytes) {
		if (bytes < 0) {
			return "";
		}
		if (bytes < 10_000) {
			return Long.toString(bytes);
		}
		String[] units = { "K", "M", "G", "T" };
		double value = bytes;
		int unit = -1;
		do {
			value /= 1024;
			++unit;
		} while (value >= 1000 && unit < units.length - 1);
		return String.format(java.util.Locale.ROOT, value < 10 ? "%.1f%s" : "%.0f%s", value, units[unit]);
	}

	@Override
	public String toString() {
		return title + ":" + directory;
	}

}
