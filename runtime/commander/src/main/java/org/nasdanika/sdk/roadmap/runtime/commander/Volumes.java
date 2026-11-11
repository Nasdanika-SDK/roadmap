package org.nasdanika.sdk.roadmap.runtime.commander;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import org.nasdanika.sdk.runtime.volume.Content;
import org.nasdanika.sdk.runtime.volume.GenericVolume;
import org.nasdanika.sdk.runtime.volume.GenericVolume.Entry;
import org.nasdanika.sdk.runtime.volume.Volume;

/**
 * File work on volumes: what a file manager does, written against the volume SPI and nothing else,
 * so it works the same over a directory, a zip file, a running JVM or anything else that is a
 * volume. Directories are copied and deleted with what is in them.
 */
public final class Volumes {

	private Volumes() {

	}

	/**
	 * The volume's capability to be written to, if it has one.
	 */
	// tag::writable[]
	@SuppressWarnings("unchecked")
	public static Optional<GenericVolume.Writable<Content>> writable(Volume volume) {
		Optional<Volume.Writable> specific = volume.capability(Volume.Writable.class);
		if (specific.isPresent()) {
			return Optional.of(specific.get());
		}
		return (Optional<GenericVolume.Writable<Content>>) (Optional<?>) volume.capability(GenericVolume.Writable.class);
	}
	// end::writable[]

	/** The last segment of a path. */
	public static String name(String path) {
		return path.substring(path.lastIndexOf('/') + 1);
	}

	/** The directory a path is in: {@code .} for a path at the root. */
	public static String parent(String path) {
		int slash = path.lastIndexOf('/');
		return slash < 0 ? "." : path.substring(0, slash);
	}

	/** A path in a directory. */
	public static String child(String directory, String name) {
		return ".".equals(directory) ? name : directory + "/" + name;
	}

	public static byte[] read(Volume volume, String path) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		volume.content(path).writeTo(out);
		return out.toByteArray();
	}

	/**
	 * Copies a file, or a directory with everything in it. An existing file at the target is
	 * replaced, and an existing directory is copied into.
	 *
	 * @return The number of files copied
	 */
	// tag::copy[]
	public static int copy(Volume source, String path, Volume target, String targetPath) throws IOException {
		GenericVolume.Writable<Content> writable = writable(target).orElseThrow(() -> new IOException("The target volume is read-only"));
		Entry entry = source.stat(path).orElseThrow(() -> new IOException("Not found: " + path));
		if (entry.kind() != Entry.Kind.DIRECTORY) {
			writable.write(targetPath, source.content(path)); // <1>
			return 1;
		}
		if (source == target && (targetPath.equals(path) || targetPath.startsWith(path + "/"))) {
			throw new IOException("Cannot copy a directory into itself");
		}
		if (target.stat(targetPath).isEmpty()) {
			writable.createDirectory(targetPath);
		}
		int copied = 0;
		for (Entry child: source.list(path)) { // <2>
			copied += copy(source, child.path(), target, child(targetPath, name(child.path())));
		}
		return copied;
	}
	// end::copy[]

	/**
	 * Deletes a file, or a directory with everything in it.
	 *
	 * @return The number of files and directories deleted
	 */
	public static int delete(Volume volume, String path) throws IOException {
		GenericVolume.Writable<Content> writable = writable(volume).orElseThrow(() -> new IOException("The volume is read-only"));
		Entry entry = volume.stat(path).orElseThrow(() -> new IOException("Not found: " + path));
		int deleted = 0;
		if (entry.kind() == Entry.Kind.DIRECTORY) {
			for (Entry child: volume.list(path)) {
				deleted += delete(volume, child.path());
			}
		}
		writable.delete(path);
		return deleted + 1;
	}

	/**
	 * Moves or renames a file or a directory. Within one volume the volume moves it, atomically
	 * where it can. Between volumes, and where the volume cannot, it is copied and then deleted.
	 */
	// tag::move[]
	public static void move(Volume source, String path, Volume target, String targetPath) throws IOException {
		if (source == target) {
			try {
				writable(source).orElseThrow(() -> new IOException("The volume is read-only")).move(path, targetPath);
				return;
			} catch (IOException e) {
				if (source.stat(path).isEmpty() || source.stat(targetPath).isPresent()) {
					throw e; // <1>
				}
			}
		}
		copy(source, path, target, targetPath); // <2>
		delete(source, path);
	}
	// end::move[]

}
