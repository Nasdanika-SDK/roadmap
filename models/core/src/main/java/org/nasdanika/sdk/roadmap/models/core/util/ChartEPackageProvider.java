package org.nasdanika.sdk.roadmap.models.core.util;

import org.eclipse.emf.ecore.EPackage;
import org.nasdanika.sdk.roadmap.models.core.chart.ChartPackage;

/**
 * Registers the chart nano-model as a service, so that a resource set finds it by its namespace.
 */
public class ChartEPackageProvider {

	public static EPackage provider() {
		return ChartPackage.eINSTANCE;
	}

}
