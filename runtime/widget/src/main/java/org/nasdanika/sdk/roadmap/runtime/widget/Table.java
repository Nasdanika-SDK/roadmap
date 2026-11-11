package org.nasdanika.sdk.roadmap.runtime.widget;

import java.util.List;
import java.util.function.Function;

/**
 * The kind for a table: columns that know how to show a row, and rows that are indexed and read
 * when they are needed. This is the shape of JFace's content and label providers. A terminal reads
 * the rows that fit its area, Markdown reads them all, and neither needs a list in memory.
 *
 * @param <C> The type of the context
 */
// tag::table[]
public interface Table<C> {

	<T> Widget<?, C> table(List<Column<T, C>> columns, Rows<T> rows);

	/**
	 * @param header The column's header
	 * @param cell The widget that shows a row in this column
	 * @param weight The column's share of the width, where a format has widths. Others ignore it
	 */
	record Column<T, C>(
			Widget<?, ? super C> header,
			Function<? super T, ? extends Widget<?, ? super C>> cell,
			int weight) {

		public Column(Widget<?, ? super C> header, Function<? super T, ? extends Widget<?, ? super C>> cell) {
			this(header, cell, 1);
		}

	}

	interface Rows<T> {

		int size();

		T get(int index);

		static <T> Rows<T> of(List<? extends T> rows) {
			return new Rows<>() {

				@Override
				public int size() {
					return rows.size();
				}

				@Override
				public T get(int index) {
					return rows.get(index);
				}

			};
		}

	}

}
// end::table[]
