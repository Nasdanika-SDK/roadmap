package org.nasdanika.sdk.roadmap.models.core.util;

import org.eclipse.emf.ecore.EPackage;
import org.nasdanika.sdk.roadmap.models.core.kind.KindPackage;

/**
 * Registers the kind nano-model as a service, so that a resource set finds it by its namespace.
 */
public class KindEPackageProvider {

	public static EPackage provider() {
		return KindPackage.eINSTANCE;
	}

}
