package org.nasdanika.sdk.roadmap.models.core.util;

import org.eclipse.emf.ecore.EPackage;
import org.nasdanika.sdk.roadmap.models.core.document.DocumentPackage;

/**
 * Registers the document nano-model as a service, so that a resource set finds it by its namespace.
 */
public class DocumentEPackageProvider {

	public static EPackage provider() {
		return DocumentPackage.eINSTANCE;
	}

}
