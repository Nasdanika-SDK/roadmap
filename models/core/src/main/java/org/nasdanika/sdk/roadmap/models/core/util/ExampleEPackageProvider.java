package org.nasdanika.sdk.roadmap.models.core.util;

import org.eclipse.emf.ecore.EPackage;
import org.nasdanika.sdk.roadmap.models.core.example.ExamplePackage;

/**
 * Registers the example nano-model as a service, so that a resource set finds it by its namespace.
 */
public class ExampleEPackageProvider {

	public static EPackage provider() {
		return ExamplePackage.eINSTANCE;
	}

}
