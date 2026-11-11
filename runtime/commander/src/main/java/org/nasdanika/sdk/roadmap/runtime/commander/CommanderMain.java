package org.nasdanika.sdk.roadmap.runtime.commander;

import java.nio.file.Path;

import org.nasdanika.sdk.runtime.volume.Volume;

import dev.tamboui.backend.jline3.JLineBackend;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.KeyEvent;

/**
 * Runs Nasdanika Commander in the terminal, over the local file system:
 *
 * <pre>
 * java -jar commander-VERSION.jar [left directory] [right directory]
 * </pre>
 *
 * Both panels start in the current directory unless told otherwise. An application with volumes of
 * its own, such as a running JVM or a repository, makes its own panels and runs the same loop.
 */
public final class CommanderMain {

	private CommanderMain() {

	}

	// tag::main[]
	public static void main(String[] args) throws Exception {
		Volume files = MountVolume.ofLocalFileSystem(); // <1>
		String leftDirectory = MountVolume.localPath(Path.of(args.length > 0 ? args[0] : ""));
		String rightDirectory = MountVolume.localPath(Path.of(args.length > 1 ? args[1] : args.length > 0 ? args[0] : ""));
		Commander commander = new Commander(new Panel("files", files, leftDirectory), new Panel("files", files, rightDirectory));

		run(commander, TuiConfig.builder().backend(new JLineBackend()).build()); // <2>
	}

	/**
	 * Runs a commander in a terminal until it quits.
	 */
	public static void run(Commander commander, TuiConfig config) throws Exception {
		try (TuiRunner runner = TuiRunner.create(config)) {
			runner.run((event, tui) -> {
				if (event instanceof KeyEvent key) {
					commander.handle(key); // <3>
					if (!commander.isRunning()) {
						tui.quit();
					}
					return true;
				}
				return false;
			}, commander::render);
		}
	}
	// end::main[]

}
