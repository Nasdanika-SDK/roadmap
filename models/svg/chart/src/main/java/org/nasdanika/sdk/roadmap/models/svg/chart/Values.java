package org.nasdanika.sdk.roadmap.models.svg.chart;

import java.util.List;

import org.nasdanika.sdk.roadmap.models.core.document.AbstractFrame;
import org.nasdanika.sdk.roadmap.models.core.document.Column;
import org.nasdanika.sdk.roadmap.models.core.document.Frame;
import org.nasdanika.sdk.roadmap.models.core.document.FrameReference;

/**
 * The columns of a frame, found by name, as text or as numbers.
 */
final class Values {

	private final Frame frame;

	Values(AbstractFrame data) {
		frame = switch (data) {
			case null -> throw new IllegalArgumentException("A chart without data");
			case FrameReference reference -> reference.getTarget();
			case Frame own -> own;
			default -> throw new IllegalArgumentException("Unknown frame: " + data);
		};
		if (frame == null) {
			throw new IllegalArgumentException("A reference to no frame");
		}
		if (frame.getLocation() != null) {
			throw new UnsupportedOperationException("Not read yet: a frame at " + frame.getLocation());
		}
	}

	int rows() {
		return frame.getColumns().stream().mapToInt(column -> column.getValues().size()).max().orElse(0);
	}

	String firstColumn() {
		if (frame.getColumns().isEmpty()) {
			throw new IllegalArgumentException("A frame without columns");
		}
		return frame.getColumns().getFirst().getName();
	}

	Column column(String name) {
		return frame.getColumns().stream()
				.filter(column -> column.getName().equals(name))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No column " + name));
	}

	/** The label of a column, or its name. */
	String label(String name) {
		Column column = column(name);
		return column.getLabel() == null ? column.getName() : column.getLabel();
	}

	List<String> labels(String name) {
		List<String> values = column(name).getValues();
		return java.util.stream.IntStream.range(0, rows())
				.mapToObj(row -> row < values.size() ? values.get(row) : "")
				.toList();
	}

	/** The values of a column as numbers. A value that is empty or not a number is NaN: missing. */
	double[] numbers(String name) {
		List<String> labels = labels(name);
		double[] numbers = new double[labels.size()];
		for (int row = 0; row < numbers.length; ++row) {
			numbers[row] = number(labels.get(row));
		}
		return numbers;
	}

	static double number(String text) {
		if (text == null || text.isBlank()) {
			return Double.NaN;
		}
		try {
			return Double.parseDouble(text.trim());
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}

}
