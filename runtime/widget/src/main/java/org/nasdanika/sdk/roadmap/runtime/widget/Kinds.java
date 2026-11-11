package org.nasdanika.sdk.roadmap.runtime.widget;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.nasdanika.sdk.runtime.common.capability.CapabilityLoader;
import org.nasdanika.sdk.runtime.common.telemetry.Telemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;

/**
 * The kinds available for a type of context: what a view is written against.
 *
 * <pre>
 * Kinds&lt;GfmContext&gt; kinds = Kinds.of(GfmContext.class);
 * Table&lt;GfmContext&gt; table = kinds.require(Table.class);
 * </pre>
 *
 * <h2>Resolution</h2>
 *
 * A kind is resolved through the capability framework with a {@link KindOf} requirement, by context
 * <b>type</b> and never by instance. Providers are matched along the context's class hierarchy, as
 * views are matched along a class's supertypes: with no {@code Text} written for {@code GfmContext},
 * the one written for {@code MarkdownContext} answers, and a kind written for a specific context
 * wins over a neutral one. That is the escape hatch: a format that can do better than the neutral
 * kind says so by providing its own.
 *
 * <p>
 * A kind that no provider serves does not exist in that format: {@link #find(Class)} is empty, and
 * a view decides what to do instead, or does without.
 *
 * <p>
 * Resolutions are remembered per kind, since each one is traced and the answer does not change
 * while the set of modules does not.
 *
 * @param <C> The type of the context
 */
public final class Kinds<C> {

	private final Class<C> contextType;
	private final CapabilityLoader loader;
	private final Map<Class<?>, Optional<Object>> resolved = new ConcurrentHashMap<>();

	private Kinds(Class<C> contextType, CapabilityLoader loader) {
		this.contextType = Objects.requireNonNull(contextType, "contextType");
		this.loader = Objects.requireNonNull(loader, "loader");
	}

	/**
	 * The kinds for a context type, from the capability factories that modules provide as services.
	 */
	public static <C> Kinds<C> of(Class<C> contextType) {
		return new Kinds<>(contextType, CapabilityLoader.getInstance());
	}

	/**
	 * The kinds for a context type, from a loader: for a task with kinds of its own, or a test.
	 */
	public static <C> Kinds<C> of(Class<C> contextType, CapabilityLoader loader) {
		return new Kinds<>(contextType, loader);
	}

	public Class<C> getContextType() {
		return contextType;
	}

	/**
	 * The kind for this context type, if a format provides it.
	 *
	 * @param kind The kind's interface, such as {@code Table.class}
	 * @param <K> The kind for this context type, such as {@code Table<GfmContext>}, taken from the assignment
	 */
	// tag::find[]
	@SuppressWarnings("unchecked")
	public <K> Optional<K> find(Class<? super K> kind) {
		return (Optional<K>) resolved.computeIfAbsent(kind, this::resolve);
	}

	private Optional<Object> resolve(Class<?> kind) {
		List<KindProvider<?>> providers = loader.loadAll(new KindOf(kind, contextType)); // <1>
		KindProvider<?> best = null;
		for (KindProvider<?> provider: providers) {
			if (best == null || isMoreSpecific(provider, best)) { // <2>
				best = provider;
			} else if (provider.contextType() == best.contextType() && provider.implementation() != best.implementation()) {
				Telemetry.log( // <3>
						Telemetry.logger(Telemetry.current()),
						Severity.WARN,
						"widget.kind.ambiguous",
						"More than one " + kind.getSimpleName() + " for " + best.contextType().getName() + ", the first one is used",
						Attributes.empty());
			}
		}
		return Optional.ofNullable(best).map(KindProvider::implementation);
	}

	private static boolean isMoreSpecific(KindProvider<?> candidate, KindProvider<?> current) {
		return candidate.contextType() != current.contextType() && current.contextType().isAssignableFrom(candidate.contextType());
	}
	// end::find[]

	/**
	 * The kind for this context type.
	 *
	 * @throws UnsupportedOperationException if no format provides it
	 */
	public <K> K require(Class<? super K> kind) {
		return this.<K>find(kind).orElseThrow(() -> new UnsupportedOperationException("No " + kind.getSimpleName() + " for " + contextType.getName()));
	}

	@Override
	public String toString() {
		return "Kinds[" + contextType.getName() + "]";
	}

}
