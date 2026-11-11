package org.nasdanika.sdk.roadmap.models.core.util;

import org.eclipse.emf.ecore.EPackage;
import org.nasdanika.sdk.roadmap.models.core.occupancy.OccupancyPackage;

/**
 * Registers the occupancy nano-model as a service, so that a resource set finds it by its namespace.
 */
public class OccupancyEPackageProvider {

	public static EPackage provider() {
		return OccupancyPackage.eINSTANCE;
	}

}
