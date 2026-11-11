package org.nasdanika.sdk.roadmap.runtime.widget;

/**
 * Logical colors, by what they mean and not by what they look like. The names are Bootstrap's, so
 * that a view says "danger" once and a Bootstrap page, a terminal's palette and a diagram each show
 * it their own way. A context maps a variant to what it has; a terminal with sixteen colors still
 * has all eight.
 */
public enum Variant {

	PRIMARY,
	SECONDARY,
	SUCCESS,
	DANGER,
	WARNING,
	INFO,
	LIGHT,
	DARK

}
