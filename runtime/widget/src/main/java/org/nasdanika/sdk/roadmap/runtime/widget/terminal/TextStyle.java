package org.nasdanika.sdk.roadmap.runtime.widget.terminal;

import org.nasdanika.sdk.roadmap.runtime.widget.Variant;

/**
 * How text looks in a terminal, in terms every terminal has. Colors are logical; the provider maps
 * them to its palette.
 *
 * @param variant The logical color, or null for the terminal's default
 * @param link Where the text leads, or null. A provider that supports hyperlinks makes the text one
 */
public record TextStyle(Variant variant, boolean bold, boolean underlined, boolean reversed, String link) {

	public static final TextStyle PLAIN = new TextStyle(null, false, false, false, null);

	public TextStyle withVariant(Variant variant) {
		return new TextStyle(variant, bold, underlined, reversed, link);
	}

	public TextStyle withBold() {
		return new TextStyle(variant, true, underlined, reversed, link);
	}

	public TextStyle withUnderline() {
		return new TextStyle(variant, bold, true, reversed, link);
	}

	public TextStyle withReverse() {
		return new TextStyle(variant, bold, underlined, true, link);
	}

	public TextStyle withLink(String link) {
		return new TextStyle(variant, bold, underlined, reversed, link);
	}

	/**
	 * This style on top of a base: what this style does not say, the base says.
	 */
	public TextStyle over(TextStyle base) {
		if (base == null) {
			return this;
		}
		return new TextStyle(
				variant == null ? base.variant : variant,
				bold || base.bold,
				underlined || base.underlined,
				reversed || base.reversed,
				link == null ? base.link : link);
	}

}
