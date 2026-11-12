/**
 * A candidate runtime module: {@code org.nasdanika.sdk.runtime.explorer} once it graduates.
 */
module org.nasdanika.sdk.roadmap.runtime.explorer {

	requires transitive org.nasdanika.sdk.roadmap.runtime.tui;
	requires transitive org.nasdanika.sdk.runtime.volume;
	requires transitive dev.tamboui.tui;
	requires dev.tamboui.jline_three.backend;

	exports org.nasdanika.sdk.roadmap.runtime.explorer;
	exports org.nasdanika.sdk.roadmap.runtime.explorer.jvm;

}
