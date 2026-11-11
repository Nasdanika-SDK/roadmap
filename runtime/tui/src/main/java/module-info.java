import org.nasdanika.sdk.roadmap.runtime.tui.TamboUIKinds;
import org.nasdanika.sdk.runtime.common.capability.CapabilityFactory;

/**
 * A candidate runtime module: {@code org.nasdanika.sdk.runtime.tui} once it graduates. The only
 * module, with {@code commander}, that sees TamboUI.
 */
module org.nasdanika.sdk.roadmap.runtime.tui {

	requires transitive org.nasdanika.sdk.roadmap.runtime.widget;
	requires transitive dev.tamboui.core;
	requires transitive dev.tamboui.widgets;

	exports org.nasdanika.sdk.roadmap.runtime.tui;

	provides CapabilityFactory with TamboUIKinds;

}
