import org.nasdanika.sdk.roadmap.runtime.widget.markdown.MarkdownKinds;
import org.nasdanika.sdk.roadmap.runtime.widget.terminal.TerminalKinds;
import org.nasdanika.sdk.runtime.common.capability.CapabilityFactory;

/**
 * A candidate runtime module: {@code org.nasdanika.sdk.runtime.widget} once it graduates.
 */
module org.nasdanika.sdk.roadmap.runtime.widget {

	requires transitive org.nasdanika.sdk.runtime.common;
	requires transitive org.nasdanika.sdk.roadmap.runtime.common;

	exports org.nasdanika.sdk.roadmap.runtime.widget;
	exports org.nasdanika.sdk.roadmap.runtime.widget.markdown;
	exports org.nasdanika.sdk.roadmap.runtime.widget.terminal;

	// tag::provides[]
	provides CapabilityFactory with
		MarkdownKinds,
		TerminalKinds;
	// end::provides[]

}
