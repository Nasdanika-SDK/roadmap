package org.nasdanika.sdk.roadmap.runtime.commander;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nasdanika.sdk.roadmap.runtime.commander.Commander.Mode;
import org.nasdanika.sdk.roadmap.runtime.tui.Screenshots;
import org.nasdanika.sdk.runtime.volume.Volume;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;

/**
 * Nasdanika Commander, driven by keys and read from a buffer: no terminal is involved. The roadmap
 * book's chapter on the commander includes its listings and its screenshots from here.
 */
class CommanderTests {

	@TempDir
	Path directory;

	Path work;
	Path archive;
	Commander commander;

	// tag::setup[]
	@BeforeEach
	void setUp() throws IOException {
		work = Files.createDirectory(directory.resolve("work"));
		archive = Files.createDirectory(directory.resolve("archive"));
		Files.createDirectory(work.resolve("notes"));
		Files.writeString(work.resolve("notes").resolve("todo.txt"), "call the bank\n");
		Files.writeString(work.resolve("plan.md"), "# Plan\r\n\r\n1. Draw\r\n2. Run\r\n3. Bind\r\n"); // <1>
		Files.writeString(work.resolve("readme.txt"), "A directory to try the commander on.\n");

		commander = new Commander(
				new Panel("work", Volume.of(work), "."), // <2>
				new Panel("archive", Volume.of(archive), "."));
	}
	// end::setup[]

	// tag::driver[]
	void press(KeyCode... keys) {
		for (KeyCode key: keys) {
			commander.handle(KeyEvent.ofKey(key));
		}
	}

	void type(String text) {
		text.chars().forEach(ch -> commander.handle(KeyEvent.ofChar(ch)));
	}

	/** What is on the screen, as text. */
	String screen(int columns, int rows) {
		Buffer buffer = Screenshots.buffer(columns, rows);
		commander.render(Frame.forTesting(buffer)); // <1>
		return Screenshots.text(buffer);
	}

	/** A screenshot for the book, in target/screenshots. */
	void screenshot(String name) {
		Buffer buffer = Screenshots.buffer(100, 24);
		commander.render(Frame.forTesting(buffer));
		Path file = Path.of(System.getProperty("roadmap.target", "target"), "screenshots", name + ".svg");
		Screenshots.write(buffer, "Nasdanika Commander", file); // <2>
		try {
			Files.writeString(file.resolveSibling(name + ".txt"), Screenshots.text(buffer));
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}
	// end::driver[]

	@Test
	void twoPanelsOverTwoVolumes() {
		String screen = screen(100, 24);
		screenshot("panels");

		assertThat(screen).contains(" work: . ").contains(" archive: . ");
		assertThat(screen).contains("/notes").contains("plan.md").contains("readme.txt");
		assertThat(screen).contains("1Help").contains("5Copy").contains("8Delete").contains("10Quit");
		assertThat(commander.getActive()).isSameAs(commander.getLeft());
	}

	@Test
	void directoriesComeFirstAndEnterAndBackspaceNavigate() throws IOException {
		assertThat(commander.getLeft().getEntries()).extracting(entry -> Volumes.name(entry.path())).containsExactly("notes", "plan.md", "readme.txt");

		press(KeyCode.ENTER); // notes is selected first
		assertThat(commander.getLeft().getDirectory()).isEqualTo("notes");
		assertThat(screen(100, 24)).contains("/..").contains("todo.txt");

		press(KeyCode.BACKSPACE);
		assertThat(commander.getLeft().getDirectory()).isEqualTo(".");
		assertThat(Volumes.name(commander.getLeft().getSelected().path())).isEqualTo("notes"); // Back on where it came from
	}

	@Test
	void f3ViewsAFile() {
		// tag::view[]
		press(KeyCode.DOWN, KeyCode.F3); // <1>

		assertThat(commander.getMode()).isEqualTo(Mode.VIEW);
		assertThat(screen(100, 24)).contains(" View plan.md ").contains("# Plan").contains("3. Bind");

		type("x"); // <2>
		press(KeyCode.ESCAPE);
		assertThat(commander.getMode()).isEqualTo(Mode.BROWSE);
		// end::view[]
	}

	@Test
	void f4EditsAndF2SavesKeepingTheLineEndings() throws IOException {
		// tag::edit[]
		press(KeyCode.DOWN, KeyCode.F4); // <1>
		assertThat(commander.getMode()).isEqualTo(Mode.EDIT);

		type("Roadmap: ");
		press(KeyCode.END);
		type("!");
		assertThat(commander.isModified()).isTrue();
		screenshot("editor");

		press(KeyCode.F2); // <2>
		assertThat(commander.getStatus()).isEqualTo("Saved plan.md");
		assertThat(Files.readString(work.resolve("plan.md"))).isEqualTo("Roadmap: # Plan!\r\n\r\n1. Draw\r\n2. Run\r\n3. Bind\r\n"); // <3>

		press(KeyCode.ESCAPE);
		assertThat(commander.getMode()).isEqualTo(Mode.BROWSE);
		// end::edit[]
	}

	@Test
	void savingWithoutAChangeWritesNothing() throws IOException {
		byte[] before = Files.readAllBytes(work.resolve("plan.md"));
		var modified = Files.getLastModifiedTime(work.resolve("plan.md"));

		press(KeyCode.DOWN, KeyCode.F4, KeyCode.F2);

		assertThat(commander.getStatus()).isEqualTo("No change, nothing written");
		assertThat(Files.readAllBytes(work.resolve("plan.md"))).isEqualTo(before);
		assertThat(Files.getLastModifiedTime(work.resolve("plan.md"))).isEqualTo(modified);
	}

	@Test
	void closingAnEditedFileAsksFirst() throws IOException {
		press(KeyCode.DOWN, KeyCode.F4);
		type("draft ");
		press(KeyCode.ESCAPE);
		assertThat(commander.isDialogOpen()).isTrue();
		assertThat(screen(100, 24)).contains("Discard the changes to plan.md?");

		type("n"); // Keep editing
		assertThat(commander.getMode()).isEqualTo(Mode.EDIT);

		press(KeyCode.ESCAPE, KeyCode.ENTER); // Discard
		assertThat(commander.getMode()).isEqualTo(Mode.BROWSE);
		assertThat(Files.readString(work.resolve("plan.md"))).startsWith("# Plan");
	}

	@Test
	void f5CopiesToTheOtherPanel() throws IOException {
		// tag::copy[]
		press(KeyCode.DOWN, KeyCode.F5); // <1>
		assertThat(screen(100, 24)).contains("Copy plan.md to, in archive:").contains("plan.md");
		screenshot("copy");

		press(KeyCode.ENTER); // <2>

		assertThat(commander.getStatus()).isEqualTo("Copied 1 file to plan.md");
		assertThat(archive.resolve("plan.md")).hasSameBinaryContentAs(work.resolve("plan.md"));
		assertThat(work.resolve("plan.md")).exists();
		// end::copy[]
	}

	@Test
	void f5CopiesADirectoryWithWhatIsInIt() throws IOException {
		press(KeyCode.F5, KeyCode.ENTER); // notes

		assertThat(commander.getStatus()).isEqualTo("Copied 1 file to notes");
		assertThat(Files.readString(archive.resolve("notes").resolve("todo.txt"))).isEqualTo("call the bank\n");
	}

	@Test
	void copyingOverAnExistingFileAsksFirst() throws IOException {
		Files.writeString(archive.resolve("plan.md"), "old");

		press(KeyCode.DOWN, KeyCode.F5, KeyCode.ENTER);
		assertThat(screen(100, 24)).contains("plan.md exists. Replace it?");
		press(KeyCode.ESCAPE);
		assertThat(Files.readString(archive.resolve("plan.md"))).isEqualTo("old");

		press(KeyCode.F5, KeyCode.ENTER, KeyCode.ENTER);
		assertThat(Files.readString(archive.resolve("plan.md"))).startsWith("# Plan");
	}

	@Test
	void f6MovesToTheOtherPanelUnderANewName() throws IOException {
		// tag::move[]
		press(KeyCode.DOWN, KeyCode.DOWN, KeyCode.F6); // <1>
		for (int i = 0; i < "readme.txt".length(); ++i) {
			press(KeyCode.BACKSPACE);
		}
		type("about.txt"); // <2>
		press(KeyCode.ENTER);

		assertThat(commander.getStatus()).isEqualTo("Moved readme.txt to about.txt");
		assertThat(work.resolve("readme.txt")).doesNotExist(); // <3>
		assertThat(Files.readString(archive.resolve("about.txt"))).isEqualTo("A directory to try the commander on.\n");
		// end::move[]
	}

	@Test
	void f6RenamesWhenBothPanelsShowOneDirectory() throws IOException {
		Volume volume = Volume.of(work);
		commander = new Commander(new Panel("work", volume, "."), new Panel("work", volume, "."));

		press(KeyCode.DOWN, KeyCode.F6);
		for (int i = 0; i < "plan.md".length(); ++i) {
			press(KeyCode.BACKSPACE);
		}
		type("roadmap.md");
		press(KeyCode.ENTER);

		assertThat(work.resolve("plan.md")).doesNotExist();
		assertThat(work.resolve("roadmap.md")).exists();
		assertThat(commander.getRight().getEntries()).extracting(entry -> Volumes.name(entry.path())).contains("roadmap.md");
	}

	@Test
	void f7CreatesADirectory() {
		press(KeyCode.F7);
		type("drafts");
		press(KeyCode.ENTER);

		assertThat(work.resolve("drafts")).isDirectory();
		assertThat(Volumes.name(commander.getLeft().getSelected().path())).isEqualTo("drafts");
	}

	@Test
	void f8DeletesAfterAskingAndADirectoryGoesWithWhatIsInIt() {
		// tag::delete[]
		press(KeyCode.F8); // <1>
		assertThat(screen(100, 24)).contains("Delete the directory notes and everything in it?");

		press(KeyCode.ESCAPE); // <2>
		assertThat(work.resolve("notes")).exists();

		press(KeyCode.F8, KeyCode.ENTER);
		assertThat(work.resolve("notes")).doesNotExist(); // <3>
		assertThat(commander.getStatus()).isEqualTo("Deleted notes, 2 entries");
		// end::delete[]
	}

	@Test
	void shiftF4CreatesAFileWhenItIsSaved() throws IOException {
		// tag::create[]
		commander.handle(KeyEvent.ofKey(KeyCode.F4, KeyModifiers.SHIFT)); // <1>
		type("ideas.txt");
		press(KeyCode.ENTER);
		assertThat(commander.getMode()).isEqualTo(Mode.EDIT);
		assertThat(work.resolve("ideas.txt")).doesNotExist(); // <2>

		type("screens drawn in Draw.io");
		press(KeyCode.F2);

		assertThat(Files.readString(work.resolve("ideas.txt"))).isEqualTo("screens drawn in Draw.io");
		// end::create[]
	}

	@Test
	void everyCommandHasALetter() throws IOException {
		type("n"); // Create
		type("a.txt");
		press(KeyCode.ENTER, KeyCode.F2, KeyCode.ESCAPE);
		assertThat(work.resolve("a.txt")).exists();

		type("c"); // Copy; a.txt is selected after it was saved
		press(KeyCode.ENTER);
		assertThat(archive.resolve("a.txt")).exists();

		type("d"); // Delete
		type("y");
		assertThat(work.resolve("a.txt")).doesNotExist();

		type("q");
		assertThat(commander.isRunning()).isFalse();
	}

	@Test
	void aReadOnlyVolumeIsViewedAndNotChanged() throws IOException {
		// tag::read-only[]
		Volume modules = new ModulesVolume(); // <1>
		commander = new Commander(new Panel("jvm", modules, "."), new Panel("archive", Volume.of(archive), "."));
		commander.getLeft().select("java.base.txt");

		press(KeyCode.F4); // <2>
		assertThat(commander.getMode()).isEqualTo(Mode.VIEW);
		assertThat(commander.getStatus()).isEqualTo("This panel is read-only: viewing");
		assertThat(screen(100, 24)).contains("module java.base");
		press(KeyCode.ESCAPE);

		press(KeyCode.F8);
		assertThat(commander.getStatus()).isEqualTo("This panel is read-only");

		press(KeyCode.F5, KeyCode.ENTER); // <3>
		assertThat(Files.readString(archive.resolve("java.base.txt"))).startsWith("module java.base");
		screenshot("jvm");
		// end::read-only[]
	}

	@Test
	void aFileThatIsNotTextIsNotOpened() throws IOException {
		Files.write(work.resolve("image.bin"), new byte[] { (byte) 0xFF, (byte) 0xFE, 0, 1 });
		commander.handle(KeyEvent.ofChar('r', KeyModifiers.CTRL));

		press(KeyCode.DOWN, KeyCode.F3);

		assertThat(commander.getMode()).isEqualTo(Mode.BROWSE);
		assertThat(commander.getStatus()).isEqualTo("image.bin is not UTF-8 text");
	}

	@Test
	void aFailureIsAStatusLineAndNotACrash() throws IOException {
		press(KeyCode.F7);
		type("notes"); // Exists
		press(KeyCode.ENTER);

		assertThat(commander.getStatus()).startsWith("Error: FileAlreadyExistsException");
		assertThat(commander.isRunning()).isTrue();
	}

	@Test
	void theLocalFileSystemIsOneVolume() throws IOException {
		Volume files = MountVolume.ofLocalFileSystem();
		String path = MountVolume.localPath(work);

		assertThat(files.stat(path)).isPresent();
		assertThat(files.list(path)).extracting(entry -> Volumes.name(entry.path())).contains("notes", "plan.md", "readme.txt");
		assertThat(Volumes.writable(files)).isPresent();

		Volumes.copy(files, path + "/plan.md", files, path + "/copy.md");
		assertThat(work.resolve("copy.md")).hasSameBinaryContentAs(work.resolve("plan.md"));
	}

	@Test
	void mountsMakeOneVolumeOfSeveral() throws IOException {
		// tag::mounts[]
		Map<String, Volume> mounts = new LinkedHashMap<>();
		mounts.put("jvm", new ModulesVolume());
		mounts.put("work", Volume.of(work));
		Volume mounted = new MountVolume(mounts); // <1>

		commander = new Commander(new Panel("mounted", mounted, "."), new Panel("archive", Volume.of(archive), "."));
		assertThat(commander.getLeft().getEntries()).extracting(entry -> entry.path()).containsExactly("jvm", "work");

		press(KeyCode.DOWN, KeyCode.ENTER, KeyCode.DOWN, KeyCode.DOWN, KeyCode.F4); // <2>
		assertThat(commander.getMode()).isEqualTo(Mode.EDIT);
		type("> ");
		press(KeyCode.F2, KeyCode.ESCAPE);

		assertThat(new String(Files.readAllBytes(work.resolve("plan.md")), StandardCharsets.UTF_8)).startsWith("> # Plan");
		// end::mounts[]
	}

}
