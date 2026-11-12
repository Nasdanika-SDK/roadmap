# For AI assistants

This repository is the public roadmap of the Nasdanika SDK: a book in AsciiDoc, modules that may
graduate to the SDK runtime, tests that show how to use them, and models to migrate. This file says
how to find your way and what the conventions are.

## Where things are

| You need | Look at |
|---|---|
| What an item is, why, and what is not built | `book/src/docs/asciidoc/chapters/`, one chapter per item |
| Every item, with its id, status and dependencies | `book/src/docs/asciidoc/appendices/a-items.adoc` |
| When and why a decision was made | `book/src/docs/asciidoc/appendices/b-revisions.adoc` |
| How a module is used | Its tests in `src/test/java`: one behavior per test, named for what it shows |
| The modules | The root `pom.xml`, then `runtime/pom.xml`, `models/pom.xml`, `tests/pom.xml` |
| The released runtime the code builds on | https://github.com/Nasdanika-SDK/runtime, version in `nasdanika.runtime.version` of the root `pom.xml` |

A listing in a chapter is an `include::` of a tagged region of a source file or a test. Follow it to
the code. Do not trust a chapter over a test: the test runs with every build.

## Status

- **SKETCH**: there is code here, built and tested, and it is not settled.
- **PLANNED**: designed or intended, not built. A chapter may show code for a planned item. Such
  code was never compiled, and the text says so.
- The Xcore files in `models/core/sketches`, `models/runtime`, `models/tower` and
  `models/technology` are pre-SDK models kept to be read. They are not part of the build.

## Conventions

- Java 25, JPMS: every module has a `module-info.java`. Tests are patched into their module.
- Group, module name and packages are the runtime's with `roadmap` added:
  `org.nasdanika.sdk.roadmap.runtime.widget`. Keep that mapping exact, since a module graduates by
  dropping `roadmap`.
- Dependencies on the runtime and on third parties have no versions here. The runtime's bill of
  materials manages them. TamboUI is the exception, with its version in the root `pom.xml`.
- The foundation is Java, EMF core and the OpenTelemetry API. Do not add a reactive library. Flows are
  `java.util.concurrent.Flow` with the runtime's `Flows` and `Pump`.
- Only `runtime/tui`, `runtime/commander` and `runtime/explorer` may import TamboUI. Neutral terminal widgets are written
  against `TerminalContext` in `runtime/widget`.
- Tabs for indentation in Java. Keep lines that a chapter quotes short: the PDF wraps a listing line
  past 96 characters.
- A region a chapter quotes is marked with `// tag::name[]` and `// end::name[]`, and a callout is a
  `// <1>` comment at the end of a line. If you change a tagged region, check the chapter's callout
  list.
- Prose: plain, first person where the author speaks, no em or en dashes.
- A change of design gets a dated row in the Revisions appendix, and the chapter is rewritten to say
  what now holds. Do not rewrite history silently.

## Build and check

```
mvn verify                             # everything, including the book
mvn verify -Dasciidoctor.skip=true     # without the book
mvn -pl runtime/widget -am verify      # one module and what it needs
```

A warning from Asciidoctor fails the build: a missing tag, a broken cross-reference, a missing image.
The book includes files that the tests write (`tests/widgets/target/showcase`,
`runtime/*/target/screenshots`), so the tests have to run before the book is rendered.

## Adding to it

A new item needs a row in the items appendix, a chapter or a section with the six things an item
says (capability, demonstration, status, depends on, value and effort, how to help), and, if it has
code, a module with tests. State what you ran and what you only read. Do not claim that something
works unless a test shows it.
