package org.nasdanika.sdk.roadmap.runtime.explorer;

import org.nasdanika.sdk.roadmap.runtime.explorer.jvm.ModuleNode;
import org.nasdanika.sdk.roadmap.runtime.explorer.jvm.ModuleViews;
import org.nasdanika.sdk.roadmap.runtime.explorer.jvm.ModulesVolume;
import org.nasdanika.sdk.roadmap.runtime.widget.Kinds;
import org.nasdanika.sdk.roadmap.runtime.widget.terminal.TerminalContext;

import dev.tamboui.backend.jline3.JLineBackend;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.KeyEvent;

/**
 * Runs Nasdanika Explorer in the terminal, over the modules of the JVM that runs it:
 *
 * <pre>
 * java -jar explorer-VERSION.jar
 * </pre>
 *
 * Started this way the JVM has the modules of the JDK. An application with a volume of its own,
 * such as the elements of a model or the pages and shapes of a diagram, makes its own explorer and
 * runs the same loop.
 */
public final class ExplorerMain {

	private ExplorerMain() {

	}

	// tag::main[]
	public static void main(String[] args) throws Exception {
		ModuleViews<TerminalContext> views = new ModuleViews<>(Kinds.of(TerminalContext.class)); // <1>
		Explorer<ModuleNode> explorer = new Explorer<>("jvm", ModulesVolume.ofBootLayer(), views::view); // <2>

		run(explorer, TuiConfig.builder().backend(new JLineBackend()).build());
	}

	/**
	 * Runs an explorer in a terminal until it quits.
	 */
	public static void run(Explorer<?> explorer, TuiConfig config) throws Exception {
		try (TuiRunner runner = TuiRunner.create(config)) {
			runner.run((event, tui) -> {
				if (event instanceof KeyEvent key) {
					explorer.handle(key);
					if (!explorer.isRunning()) {
						tui.quit();
					}
					return true;
				}
				return false;
			}, explorer::render);
		}
	}
	// end::main[]

}
