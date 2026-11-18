package org.nasdanika.sdk.roadmap.models.svg.chart;

import java.util.ArrayList;
import java.util.List;

/**
 * The range of a numeric axis and its ticks at round numbers: steps of 1, 2 or 5 times a power of
 * ten. Nothing here knows SVG, so a Draw.io writer can use it as it is.
 *
 * @param min The value at the start of the axis
 * @param max The value at the end of the axis
 * @param step The distance between ticks
 */
record Ticks(double min, double max, double step) {

	/**
	 * @param low The least value to show
	 * @param high The greatest value to show
	 * @param roundLow Whether the start may move down to a tick
	 * @param roundHigh Whether the end may move up to a tick
	 * @param count About how many ticks
	 */
	static Ticks of(double low, double high, boolean roundLow, boolean roundHigh, int count) {
		if (!(high > low)) {
			high = low + (low == 0 ? 1 : Math.abs(low));
		}
		double step = niceStep((high - low) / Math.max(1, count - 1));
		double min = roundLow ? Math.floor(low / step) * step : low;
		double max = roundHigh ? Math.ceil(high / step) * step : high;
		return new Ticks(min, max, step);
	}

	static double niceStep(double raw) {
		double power = Math.pow(10, Math.floor(Math.log10(raw)));
		double fraction = raw / power;
		// Heckbert's nice numbers: the nearest of 1, 2, 5 and 10
		double nice = fraction < 1.5 ? 1 : fraction < 3 ? 2 : fraction < 7 ? 5 : 10;
		return nice * power;
	}

	List<Double> values() {
		List<Double> values = new ArrayList<>();
		double epsilon = step * 1e-9;
		for (double index = Math.ceil((min - epsilon) / step); index * step <= max + epsilon; ++index) {
			values.add(index * step + 0.0); // -0.0 is 0
		}
		return values;
	}

	/** Where a value falls between two positions: from at min, to at max. */
	double position(double value, double from, double to) {
		return from + (value - min) / (max - min) * (to - from);
	}

	double clamp(double value) {
		return Math.max(min, Math.min(max, value));
	}

}
