package org.nasdanika.sdk.roadmap.models.core.util;

import org.eclipse.emf.ecore.EPackage;
import org.nasdanika.sdk.roadmap.models.core.enterprise.EnterprisePackage;

/**
 * Registers the enterprise nano-model as a service, so that a resource set finds it by its namespace.
 */
public class EnterpriseEPackageProvider {

	public static EPackage provider() {
		return EnterprisePackage.eINSTANCE;
	}

}
