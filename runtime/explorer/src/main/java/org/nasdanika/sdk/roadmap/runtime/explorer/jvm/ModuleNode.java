package org.nasdanika.sdk.roadmap.runtime.explorer.jvm;

import java.lang.module.ModuleDescriptor;
import java.util.List;
import java.util.stream.Stream;

/**
 * A place in the tree of module names: a folder, a module, or both. {@code java.sql} is a module,
 * and it is also the folder of {@code java.sql.rowset}, as a Maven group can be an artifact too.
 *
 * @param name The dotted name of the place: empty for the root, {@code org.nasdanika} for a folder,
 * a module's name for a module
 * @param descriptor The module with this name, or null where the place is only a folder
 * @param children The places one segment below this one, by name
 */
// tag::node[]
public record ModuleNode(String name, ModuleDescriptor descriptor, List<ModuleNode> children) {

	public boolean isModule() {
		return descriptor != null;
	}

	public boolean isFolder() {
		return !children.isEmpty();
	}

	/** The last segment of the name: what a tree shows. */
	public String label() {
		return name.substring(name.lastIndexOf('.') + 1);
	}

	/** The modules at this place and below it. */
	public Stream<ModuleDescriptor> modules() {
		Stream<ModuleDescriptor> below = children.stream().flatMap(ModuleNode::modules);
		return isModule() ? Stream.concat(Stream.of(descriptor), below) : below;
	}

	public int moduleCount() {
		return (int) modules().count();
	}

	/** The number of requires directives of the modules at this place and below it. */
	public int requiresCount() {
		return modules().mapToInt(module -> module.requires().size()).sum();
	}

	/** The number of provides directives of the modules at this place and below it. */
	public int providesCount() {
		return modules().mapToInt(module -> module.provides().size()).sum();
	}

}
// end::node[]
