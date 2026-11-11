package org.nasdanika.sdk.roadmap.runtime.widget;

import java.util.List;
import java.util.concurrent.Flow;

import org.nasdanika.sdk.runtime.common.capability.CapabilityFactory;
import org.nasdanika.sdk.runtime.common.capability.CapabilityProvider;
import org.nasdanika.sdk.runtime.common.flow.Flows;

import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;

/**
 * The base of a format's contribution to the capability framework: a list of {@link KindProvider}s,
 * offered for every {@link KindOf} requirement they serve. A format registers one subclass with
 * {@code provides CapabilityFactory with ...} in its module descriptor.
 */
// tag::kind-factory[]
public abstract class KindFactory implements CapabilityFactory<KindOf, KindProvider<?>> {

	private final List<KindProvider<?>> providers;

	protected KindFactory(KindProvider<?>... providers) {
		this.providers = List.of(providers);
	}

	@Override
	public boolean canHandle(Object requirement) {
		return requirement instanceof KindOf kindOf && providers.stream().anyMatch(provider -> provider.serves(kindOf));
	}

	@Override
	public Flow.Publisher<CapabilityProvider<KindProvider<?>>> create(KindOf requirement, Loader loader, Span span, Logger logger, Meter meter) {
		List<CapabilityProvider<KindProvider<?>>> serving = providers.stream()
				.filter(provider -> provider.serves(requirement))
				.<CapabilityProvider<KindProvider<?>>>map(CapabilityProvider::of)
				.toList();
		return Flows.fromIterable(serving);
	}

}
// end::kind-factory[]
