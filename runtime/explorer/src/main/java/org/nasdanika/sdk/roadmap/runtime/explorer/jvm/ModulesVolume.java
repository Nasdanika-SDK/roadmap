package org.nasdanika.sdk.roadmap.runtime.explorer.jvm;

import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.nasdanika.sdk.runtime.volume.GenericVolume;

/**
 * Modules as a read-only generic volume, in folders by the segments of their names, as a Maven
 * repository lays out groups: {@code org.nasdanika.sdk.runtime.common} is at
 * {@code org/nasdanika/sdk/runtime/common}. The content at a path is a {@link ModuleNode} and not
 * bytes, which is what makes the volume generic: whoever reads it gets the thing itself.
 *
 * <p>
 * A stand-in for the JVM volume on the roadmap, here so that the explorer has something to explore
 * that every reader has: the modules of the JVM that runs it.
 */
// tag::volume[]
public class ModulesVolume implements GenericVolume<ModuleNode> {

	private final ModuleNode root;

	public ModulesVolume(Collection<ModuleDescriptor> modules) {
		Folder folder = new Folder();
		for (ModuleDescriptor module: modules) {
			Folder place = folder;
			for (String segment: module.name().split("\\.")) { // <1>
				place = place.children.computeIfAbsent(segment, name -> new Folder());
			}
			place.descriptor = module;
		}
		root = folder.node("");
	}

	/** The modules of the JVM that runs this. */
	public static ModulesVolume ofBootLayer() {
		return new ModulesVolume(ModuleLayer.boot().modules().stream().map(Module::getDescriptor).toList());
	}

	@Override
	public Optional<Entry> stat(String path) {
		return find(path).map(node -> entry(path, node));
	}

	@Override
	public List<Entry> list(String directory) {
		String prefix = ".".equals(directory) ? "" : directory + "/";
		List<Entry> entries = new ArrayList<>();
		for (ModuleNode child: content(directory).children()) {
			entries.add(entry(prefix + child.label(), child));
		}
		return entries;
	}

	@Override
	public ModuleNode content(String path) { // <2>
		return find(path).orElseThrow(() -> new UncheckedIOException(new NoSuchFileException(path)));
	}

	@Override
	public boolean caseSensitive() {
		return true;
	}

	@Override
	public <T> Optional<T> capability(Class<T> type) {
		return Optional.empty(); // <3>
	}
	// end::volume[]

	private static Entry entry(String path, ModuleNode node) {
		return new Entry(path, node.isFolder() ? Entry.Kind.DIRECTORY : Entry.Kind.FILE, -1, null, null);
	}

	private Optional<ModuleNode> find(String path) {
		ModuleNode node = root;
		if (!".".equals(path)) {
			for (String segment: path.split("/")) {
				Optional<ModuleNode> child = node.children().stream().filter(candidate -> candidate.label().equals(segment)).findFirst();
				if (child.isEmpty()) {
					return Optional.empty();
				}
				node = child.get();
			}
		}
		return Optional.of(node);
	}

	/** A place while the tree is built. */
	private static final class Folder {

		private final Map<String, Folder> children = new TreeMap<>();
		private ModuleDescriptor descriptor;

		ModuleNode node(String name) {
			List<ModuleNode> nodes = new ArrayList<>();
			children.forEach((segment, child) -> nodes.add(child.node(name.isEmpty() ? segment : name + "." + segment)));
			return new ModuleNode(name, descriptor, List.copyOf(nodes));
		}

	}

}
