import org.eclipse.emf.ecore.EPackage;
import org.nasdanika.sdk.roadmap.models.core.util.RelatedEPackageProvider;
import org.nasdanika.sdk.roadmap.models.core.util.ChangeEPackageProvider;
import org.nasdanika.sdk.roadmap.models.core.util.OccupancyEPackageProvider;
import org.nasdanika.sdk.roadmap.models.core.util.KindEPackageProvider;
import org.nasdanika.sdk.roadmap.models.core.util.EnterpriseEPackageProvider;
import org.nasdanika.sdk.roadmap.models.core.util.DocumentEPackageProvider;
import org.nasdanika.sdk.roadmap.models.core.util.ExampleEPackageProvider;

/**
 * Candidate nano-models of the core model: packages that would join
 * {@code org.nasdanika.sdk.runtime.models.core} when they graduate.
 */
module org.nasdanika.sdk.roadmap.models.core {

	requires transitive org.nasdanika.sdk.runtime.models.core;

	exports org.nasdanika.sdk.roadmap.models.core.related;
	exports org.nasdanika.sdk.roadmap.models.core.related.impl;
	exports org.nasdanika.sdk.roadmap.models.core.related.util;

	exports org.nasdanika.sdk.roadmap.models.core.change;
	exports org.nasdanika.sdk.roadmap.models.core.change.impl;
	exports org.nasdanika.sdk.roadmap.models.core.change.util;

	exports org.nasdanika.sdk.roadmap.models.core.occupancy;
	exports org.nasdanika.sdk.roadmap.models.core.occupancy.impl;
	exports org.nasdanika.sdk.roadmap.models.core.occupancy.util;

	exports org.nasdanika.sdk.roadmap.models.core.kind;
	exports org.nasdanika.sdk.roadmap.models.core.kind.impl;
	exports org.nasdanika.sdk.roadmap.models.core.kind.util;

	exports org.nasdanika.sdk.roadmap.models.core.enterprise;
	exports org.nasdanika.sdk.roadmap.models.core.enterprise.impl;
	exports org.nasdanika.sdk.roadmap.models.core.enterprise.util;

	exports org.nasdanika.sdk.roadmap.models.core.document;
	exports org.nasdanika.sdk.roadmap.models.core.document.impl;
	exports org.nasdanika.sdk.roadmap.models.core.document.util;

	exports org.nasdanika.sdk.roadmap.models.core.example;
	exports org.nasdanika.sdk.roadmap.models.core.example.impl;
	exports org.nasdanika.sdk.roadmap.models.core.example.util;

	exports org.nasdanika.sdk.roadmap.models.core.util;

	provides EPackage with
		RelatedEPackageProvider,
		ChangeEPackageProvider,
		OccupancyEPackageProvider,
		KindEPackageProvider,
		EnterpriseEPackageProvider,
		DocumentEPackageProvider,
		ExampleEPackageProvider;

}
