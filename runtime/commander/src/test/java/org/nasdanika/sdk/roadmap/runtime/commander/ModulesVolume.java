package org.nasdanika.sdk.roadmap.runtime.commander;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.nasdanika.sdk.runtime.volume.Content;
import org.nasdanika.sdk.runtime.volume.Volume;

/**
 * The modules of the running JVM as a read-only volume: one text file per module, with what the
 * module requires. A stand-in for the JVM volume on the roadmap, and enough to show that a panel
 * is a volume and not a directory.
 */
// tag::volume[]
class ModulesVolume implements Volume {

	private final Map<String, Module> files = new TreeMap<>();

	ModulesVolume() {
		for (Module module: ModuleLayer.boot().modules()) {
			files.put(module.getName() + ".txt", module);
		}
	}

	private static byte[] describe(Module module) {
		StringBuilder text = new StringBuilder("module ").append(module.getName()).append('\n');
		module.getDescriptor().requires().stream().sorted().forEach(requires -> text.append("  requires ").append(requires.name()).append('\n'));
		return text.toString().getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public Optional<Entry> stat(String path) {
		if (".".equals(path)) {
			return Optional.of(new Entry(".", Entry.Kind.DIRECTORY, -1, null, null));
		}
		Module module = files.get(path);
		return module == null ? Optional.empty() : Optional.of(new Entry(path, Entry.Kind.FILE, describe(module).length, null, Instant.EPOCH));
	}

	@Override
	public List<Entry> list(String directory) {
		List<Entry> entries = new ArrayList<>();
		for (String file: files.keySet()) {
			entries.add(stat(file).orElseThrow());
		}
		return entries;
	}

	@Override
	public Content content(String path) {
		return Content.of(() -> {
			Module module = files.get(path);
			if (module == null) {
				throw new NoSuchFileException(path);
			}
			return new ByteArrayInputStream(describe(module));
		});
	}

	@Override
	public boolean caseSensitive() {
		return true;
	}

	@Override
	public <T> Optional<T> capability(Class<T> type) {
		return Optional.empty(); // <1>
	}

}
// end::volume[]
