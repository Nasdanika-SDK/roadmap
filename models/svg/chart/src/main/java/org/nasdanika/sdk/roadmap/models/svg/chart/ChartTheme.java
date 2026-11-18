package org.nasdanika.sdk.roadmap.models.svg.chart;

import java.util.List;

/**
 * What a chart does not say and a page does: the colors of series that have none, the colors of
 * ink, grid and axes, and the font. Colors are SVG colors.
 *
 * @param palette Colors of series, or of slices, in order, for those that have no color of their own
 * @param surface The background the chart is drawn on. Gaps between marks and rings around dots are drawn in it
 * @param ink Text that is read first: the labels of reference lines and slices
 * @param secondaryInk Text that is looked up: ticks, axis titles, legend entries
 * @param grid Grid lines
 * @param axis The baseline of the category axis
 * @param fontFamily A CSS font list
 * @param fontSize In pixels
 */
public record ChartTheme(
		List<String> palette,
		String surface,
		String ink,
		String secondaryInk,
		String grid,
		String axis,
		String fontFamily,
		double fontSize) {

	/**
	 * Eight categorical colors in an order validated for color vision deficiency on a light surface,
	 * and recessive grey for everything that is not data. Noto Sans is first because it is the sans
	 * font of the roadmap's PDF, which then draws the charts with it; a browser without it falls back
	 * to the system font.
	 */
	public static ChartTheme light() {
		return new ChartTheme(
				List.of("#2a78d6", "#eb6834", "#1baf7a", "#eda100", "#e87ba4", "#008300", "#6250d6", "#e34948"),
				"#ffffff",
				"#0b0b0b",
				"#52514e",
				"#e1e0d9",
				"#c3c2b7",
				"Noto Sans, system-ui, -apple-system, Segoe UI, sans-serif",
				12);
	}

}
