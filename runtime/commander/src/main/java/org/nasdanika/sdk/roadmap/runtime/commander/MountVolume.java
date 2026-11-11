package org.nasdanika.sdk.roadmap.runtime.commander;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.nasdanika.sdk.runtime.volume.Content;
import org.nasdanika.sdk.runtime.volume.GenericVolume;
import org.nasdanika.sdk.runtime.volume.Volume;

/**
 * One volume made of others, each mounted under a name at the root: the drives of a Windows
 * machine, or a directory next to a running JVM next to a repository. A path's first segment picks
 * the mount, and the rest is a path in it.
 *
 * <p>
 * Writing goes to the mount that the path names, if that volume is writable. A move between mounts
 * is refused here, because it is a copy and a delete, which the caller can do and report on.
 */
// tag::mount[]
public class MountVolume implements Volume {

	private final Map<String, Volume> mounts;

	public MountVolume(Map<String, Volume> mounts) {
		this.mounts = new LinkedHashMap<>(mounts);
	}
	// end::mount[]

	/**
	 * The local file system as one volume: its root on a system that has one, and its roots mounted
	 * by name, such as {@code C:}, on a system that has several.
	 */
	public static Volume ofLocalFileSystem() {
		List<Path> roots = new ArrayList<>();
		FileSystems.getDefault().getRootDirectories().forEach(roots::add);
		if (roots.size() == 1) {
			return Volume.of(roots.get(0));
		}
		Map<String, Volume> mounts = new LinkedHashMap<>();
		for (Path root: roots) {
			mounts.put(mountName(root), Volume.of(root));
		}
		return new MountVolume(mounts);
	}

	/**
	 * The path of a local directory in {@link #ofLocalFileSystem()}.
	 */
	public static String localPath(Path directory) {
		Path absolute = directory.toAbsolutePath().normalize();
		List<String> segments = new ArrayList<>();
		long roots = 0;
		for (Path ignored: FileSystems.getDefault().getRootDirectories()) {
			++roots;
		}
		if (roots != 1 && absolute.getRoot() != null) {
			segments.add(mountName(absolute.getRoot()));
		}
		for (Path segment: absolute) {
			segments.add(segment.toString());
		}
		return segments.isEmpty() ? "." : String.join("/", segments);
	}

	private static String mountName(Path root) {
		String name = root.toString().replace("\\", "").replace("/", "");
		return name.isEmpty() ? "root" : name;
	}

	private static String mountOf(String path) {
		int slash = path.indexOf('/');
		return slash < 0 ? path : path.substring(0, slash);
	}

	private static String inner(String path) {
		int slash = path.indexOf('/');
		return slash < 0 ? "." : path.substring(slash + 1);
	}

	private Volume mount(String path) throws NoSuchFileException {
		Volume volume = mounts.get(mountOf(path));
		if (volume == null) {
			throw new NoSuchFileException(path);
		}
		return volume;
	}

	private static Entry directory(String path) {
		return new Entry(path, Entry.Kind.DIRECTORY, -1, null, null);
	}

	@Override
	public Optional<Entry> stat(String path) {
		if (".".equals(path)) {
			return Optional.of(directory("."));
		}
		Volume volume = mounts.get(mountOf(path));
		if (volume == null) {
			return Optional.empty();
		}
		return volume.stat(inner(path)).map(entry -> new Entry(path, entry.kind(), entry.size(), entry.hash(), entry.modified()));
	}

	// tag::list[]
	@Override
	public List<Entry> list(String directory) throws IOException {
		List<Entry> entries = new ArrayList<>();
		if (".".equals(directory)) {
			for (String mount: mounts.keySet()) {
				entries.add(directory(mount)); // <1>
			}
			return entries;
		}
		String mount = mountOf(directory);
		for (Entry entry: mount(directory).list(inner(directory))) {
			entries.add(new Entry(mount + "/" + entry.path(), entry.kind(), entry.size(), entry.hash(), entry.modified())); // <2>
		}
		return entries;
	}
	// end::list[]

	@Override
	public Content content(String path) {
		Volume volume = mounts.get(mountOf(path));
		if (volume == null) {
			return Content.of(() -> {
				throw new NoSuchFileException(path);
			});
		}
		return volume.content(inner(path));
	}

	@Override
	public boolean caseSensitive() {
		return mounts.values().stream().allMatch(Volume::caseSensitive);
	}

	@Override
	public <T> Optional<T> capability(Class<T> type) {
		if (type != Volume.Writable.class && type != GenericVolume.Writable.class) {
			return Optional.empty();
		}
		return Optional.of(type.cast(new Volume.Writable() {

			private GenericVolume.Writable<Content> target(String path) throws IOException {
				return Volumes.writable(mount(path)).orElseThrow(() -> new IOException(mountOf(path) + " is read-only"));
			}

			@Override
			public void write(String path, Content content) throws IOException {
				target(path).write(inner(path), content);
			}

			@Override
			public void delete(String path) throws IOException {
				target(path).delete(inner(path));
			}

			@Override
			public void move(String from, String to) throws IOException {
				if (!mountOf(from).equals(mountOf(to))) {
					throw new IOException("A move from " + mountOf(from) + " to " + mountOf(to) + " is a copy and a delete");
				}
				target(from).move(inner(from), inner(to));
			}

			@Override
			public void createDirectory(String path) throws IOException {
				target(path).createDirectory(inner(path));
			}

			@Override
			public boolean atomicMove() {
				return false;
			}

		}));
	}

	@Override
	public String toString() {
		return "MountVolume" + mounts.keySet();
	}

}
