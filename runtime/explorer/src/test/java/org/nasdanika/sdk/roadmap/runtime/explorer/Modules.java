package org.nasdanika.sdk.roadmap.runtime.explorer;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Requires.Modifier;
import java.util.List;
import java.util.Set;

/**
 * A handful of module descriptors for the tests, so that what the tests assert and what the book
 * shows do not depend on the JVM that runs them.
 */
final class Modules {

	private Modules() {

	}

	// tag::modules[]
	static final List<ModuleDescriptor> SAMPLE = List.of(
			ModuleDescriptor.newModule("java.base")
					.exports("java.io")
					.exports("java.lang")
					.exports("java.util")
					.build(),
			ModuleDescriptor.newModule("java.logging")
					.exports("java.util.logging")
					.build(),
			ModuleDescriptor.newModule("java.sql") // <1>
					.requires(Set.of(Modifier.TRANSITIVE), "java.logging")
					.exports("java.sql")
					.exports("javax.sql")
					.build(),
			ModuleDescriptor.newModule("java.sql.rowset")
					.requires(Set.of(Modifier.TRANSITIVE), "java.sql")
					.exports("javax.sql.rowset")
					.provides("javax.sql.rowset.RowSetFactory", List.of("com.sun.rowset.RowSetFactoryImpl"))
					.build(),
			ModuleDescriptor.newModule("org.nasdanika.sdk.runtime.common")
					.requires(Set.of(Modifier.TRANSITIVE), "io.opentelemetry.api")
					.requires(Set.of(Modifier.TRANSITIVE), "org.eclipse.emf.ecore")
					.exports("org.nasdanika.sdk.runtime.common.capability")
					.exports("org.nasdanika.sdk.runtime.common.flow")
					.exports("org.nasdanika.sdk.runtime.common.telemetry")
					.build(),
			ModuleDescriptor.newModule("org.nasdanika.sdk.runtime.volume")
					.requires(Set.of(Modifier.TRANSITIVE), "org.nasdanika.sdk.runtime.common")
					.requires("java.net.http")
					.exports("org.nasdanika.sdk.runtime.volume")
					.exports("org.nasdanika.sdk.runtime.volume.emf")
					.exports("org.nasdanika.sdk.runtime.volume.http")
					.provides(
							"org.nasdanika.sdk.runtime.common.capability.CapabilityFactory",
							List.of("org.nasdanika.sdk.runtime.volume.emf.VolumeURIHandlerFactory"))
					.build(),
			ModuleDescriptor.newModule("org.nasdanika.sdk.runtime.cli")
					.requires(Set.of(Modifier.TRANSITIVE), "org.nasdanika.sdk.runtime.common")
					.requires("info.picocli")
					.exports("org.nasdanika.sdk.runtime.cli")
					.build());
	// end::modules[]

}
