package org.nasdanika.sdk.roadmap.runtime.explorer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.nasdanika.sdk.roadmap.runtime.explorer.jvm.ModuleViews;
import org.nasdanika.sdk.roadmap.runtime.explorer.jvm.ModulesVolume;
import org.nasdanika.sdk.roadmap.runtime.widget.Kinds;
import org.nasdanika.sdk.roadmap.runtime.widget.markdown.GfmContext;
import org.nasdanika.sdk.roadmap.runtime.widget.markdown.MarkdownContext;

/**
 * The views the explorer shows, rendered as Markdown: what is browsed in a terminal is also a page
 * of documentation, from the same code. The roadmap book includes the pages from
 * {@code target/showcase}.
 */
class ModuleViewsTests {

	ModulesVolume modules = new ModulesVolume(Modules.SAMPLE);

	static Path showcase(String file, String content) throws IOException {
		Path directory = Path.of(System.getProperty("roadmap.target", "target"), "showcase");
		Files.createDirectories(directory);
		return Files.writeString(directory.resolve(file), content, StandardCharsets.UTF_8);
	}

	@Test
	void aModuleAsAPageOfMarkdown() throws IOException {
		// tag::markdown[]
		ModuleViews<GfmContext> views = new ModuleViews<>(Kinds.of(GfmContext.class)); // <1>
		StringBuilder out = new StringBuilder();
		views.view(modules.content("org/nasdanika/sdk/runtime/volume")).render(new GfmContext(out));
		// end::markdown[]
		showcase("explorer-module.md", out.toString());

		assertThat(out.toString()).isEqualTo("""
				# org.nasdanika.sdk.runtime.volume

				## Exports

				- org.nasdanika.sdk.runtime.volume
				- org.nasdanika.sdk.runtime.volume.emf
				- org.nasdanika.sdk.runtime.volume.http

				## Requires

				- mandated java.base
				- java.net.http
				- transitive org.nasdanika.sdk.runtime.common

				## Provides

				- org.nasdanika.sdk.runtime.common.capability.CapabilityFactory with org.nasdanika.sdk.runtime.volume.emf.VolumeURIHandlerFactory

				""");
	}

	@Test
	void aFolderAsATableAndAsAListWhereThereAreNoTables() throws IOException {
		StringBuilder gfm = new StringBuilder();
		new ModuleViews<>(Kinds.of(GfmContext.class)).view(modules.content("org/nasdanika/sdk/runtime")).render(new GfmContext(gfm));
		showcase("explorer-folder.md", gfm.toString());

		assertThat(gfm.toString()).contains("# org.nasdanika.sdk.runtime").contains("| Name | Modules | Requires | Provides |").contains("| volume |  | 3 | 1 |");

		StringBuilder commonMark = new StringBuilder();
		new ModuleViews<>(Kinds.of(MarkdownContext.class)).view(modules.content("org/nasdanika/sdk/runtime")).render(new MarkdownContext(commonMark));

		assertThat(commonMark.toString()).contains("- volume: 3 requires, 1 provides").doesNotContain("|");
	}

}
