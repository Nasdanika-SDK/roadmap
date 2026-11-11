/**
 * Candidates for the runtime's {@code common} module. A package here keeps the name it would have
 * there, with {@code roadmap} added: {@code org.nasdanika.sdk.roadmap.runtime.common.flow} would
 * graduate to {@code org.nasdanika.sdk.runtime.common.flow}.
 */
module org.nasdanika.sdk.roadmap.runtime.common {

	requires transitive org.nasdanika.sdk.runtime.common;

	exports org.nasdanika.sdk.roadmap.runtime.common.flow;
	exports org.nasdanika.sdk.roadmap.runtime.common.waypoint;

}
