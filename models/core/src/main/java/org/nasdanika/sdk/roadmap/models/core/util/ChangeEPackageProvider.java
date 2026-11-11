package org.nasdanika.sdk.roadmap.models.core.util;

import org.eclipse.emf.ecore.EPackage;
import org.nasdanika.sdk.roadmap.models.core.change.ChangePackage;

/**
 * Registers the change nano-model as a service, so that a resource set finds it by its namespace.
 */
public class ChangeEPackageProvider {

	public static EPackage provider() {
		return ChangePackage.eINSTANCE;
	}

}
