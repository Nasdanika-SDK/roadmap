package org.nasdanika.sdk.roadmap.runtime.explorer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.jline.terminal.Size;
import org.jline.terminal.impl.LineDisciplineTerminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.nasdanika.sdk.roadmap.runtime.explorer.Explorer.Focus;
import org.nasdanika.sdk.roadmap.runtime.explorer.jvm.ModuleNode;
import org.nasdanika.sdk.roadmap.runtime.explorer.jvm.ModuleViews;
import org.nasdanika.sdk.roadmap.runtime.explorer.jvm.ModulesVolume;
import org.nasdanika.sdk.roadmap.runtime.widget.Kinds;
import org.nasdanika.sdk.roadmap.runtime.widget.terminal.TerminalContext;

import dev.tamboui.backend.jline3.JLineBackend;
import dev.tamboui.tui.TuiConfig;

/**
 * The explorer in its terminal loop, with the bytes a terminal sends for the keys. The terminal is
 * JLine's, in memory, as in the commander's test of the same name.
 */
class TerminalLoopTests {

	static final String DOWN = "\u001b[B";
	static final String RIGHT = "\u001b[C";
	static final String TAB = "\t";
	static final String F10 = "\u001b[21~";

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
	void theKeysArriveThroughARealTerminal() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		terminal = new LineDisciplineTerminal("explorer", "xterm-256color", out, StandardCharsets.UTF_8);
		terminal.setSize(new Size(100, 24));

		ModuleViews<TerminalContext> views = new ModuleViews<>(Kinds.of(TerminalContext.class));
		Explorer<ModuleNode> explorer = new Explorer<>("jvm", new ModulesVolume(Modules.SAMPLE), views::view);
		TuiConfig config = TuiConfig.builder().backend(new JLineBackend(terminal)).shutdownHook(false).build();

		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread loop = Thread.ofPlatform().daemon().name("explorer").start(() -> {
			try {
				ExplorerMain.run(explorer, config);
			} catch (Throwable e) {
				failure.set(e);
			}
		});

		send(DOWN);
		await("java selected", () -> "java".equals(explorer.getSelected().path()));
		send(RIGHT);
		await("java open", () -> explorer.isExpanded("java"));
		send(TAB);
		await("the view", () -> explorer.getFocus() == Focus.VIEW);
		send(F10);
		loop.join(TimeUnit.SECONDS.toMillis(15));

		assertThat(failure.get()).isNull();
		assertThat(loop.isAlive()).isFalse();
		assertThat(out.size()).isPositive(); // It drew
	}

}
