package org.nasdanika.sdk.roadmap.models.core.util;

import org.eclipse.emf.ecore.EPackage;
import org.nasdanika.sdk.roadmap.models.core.related.RelatedPackage;

/**
 * Registers the related nano-model as a service, so that a resource set finds it by its namespace.
 */
public class RelatedEPackageProvider {

	public static EPackage provider() {
		return RelatedPackage.eINSTANCE;
	}

}
