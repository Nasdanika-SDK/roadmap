package org.nasdanika.sdk.roadmap.runtime.widget;

import org.nasdanika.sdk.runtime.common.telemetry.Telemetry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;

/**
 * What every context has: a way to report what a widget could not render faithfully.
 *
 * <p>
 * Diagnostics go through the context and not into a widget's result, so that the result stays what
 * the format needs it to be. By default a diagnostic is a log record in the current span, like
 * everything else the runtime reports.
 */
public interface RenderContext {

	/**
	 * Reports a problem with rendering, such as text in a format that cannot be converted and was
	 * rendered as plain text.
	 */
	default void diagnostic(Severity severity, String message) {
		Telemetry.log(
				Telemetry.logger(Telemetry.current()),
				severity,
				"widget.diagnostic",
				message,
				Attributes.of(AttributeKey.stringKey("widget.context"), getClass().getName()));
	}

}
