package org.nasdanika.sdk.roadmap.runtime.commander;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.jline.terminal.Size;
import org.jline.terminal.impl.LineDisciplineTerminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.nasdanika.sdk.roadmap.runtime.commander.Commander.Mode;
import org.nasdanika.sdk.runtime.volume.Volume;

import dev.tamboui.backend.jline3.JLineBackend;
import dev.tamboui.tui.TuiConfig;

/**
 * The commander in its terminal loop, with the bytes a terminal sends for the keys. The terminal is
 * JLine's, in memory: what the application writes goes to a buffer and what the test sends is its
 * keyboard.
 */
class TerminalLoopTests {

	static final String SHIFT_F4 = "\u001b[1;2S"; // xterm: F4 with the shift modifier
	static final String F2 = "\u001bOQ";
	static final String F10 = "\u001b[21~";
	static final String ENTER = "\r";

	@TempDir
	Path directory;

	LineDisciplineTerminal terminal;

	void send(String keys) throws IOException {
		terminal.processInputBytes(keys.getBytes(StandardCharsets.UTF_8));
		terminal.flush();
	}

	static void await(String what, BooleanSupplier condition) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError("Timed out waiting for: " + what);
			}
			Thread.sleep(20);
		}
	}

	@Test
	@Timeout(90)
	void theClassicKeysArriveThroughARealTerminal() throws Exception {
		// tag::loop[]
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		terminal = new LineDisciplineTerminal("commander", "xterm-256color", out, StandardCharsets.UTF_8); // <1>
		terminal.setSize(new Size(100, 24));

		Volume volume = Volume.of(directory);
		Commander commander = new Commander(new Panel("left", volume, "."), new Panel("right", volume, "."));
		TuiConfig config = TuiConfig.builder().backend(new JLineBackend(terminal)).shutdownHook(false).build();

		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread loop = Thread.ofPlatform().daemon().name("commander").start(() -> {
			try {
				CommanderMain.run(commander, config); // <2>
			} catch (Throwable e) {
				failure.set(e);
			}
		});

		send(SHIFT_F4); // <3>
		await("the dialog that asks for a name", commander::isDialogOpen);
		send("hello.txt" + ENTER);
		await("the editor", () -> commander.getMode() == Mode.EDIT);
		send("typed in a terminal");
		send(F2);
		await("the file", () -> Files.exists(directory.resolve("hello.txt")));
		send(F10); // Closes the editor
		await("the panels", () -> commander.getMode() == Mode.BROWSE);
		send(F10); // Quits
		loop.join(TimeUnit.SECONDS.toMillis(15));
		// end::loop[]

		assertThat(failure.get()).isNull();
		assertThat(loop.isAlive()).isFalse();
		assertThat(Files.readString(directory.resolve("hello.txt"))).isEqualTo("typed in a terminal");
		assertThat(out.size()).isPositive(); // It drew
	}

}
