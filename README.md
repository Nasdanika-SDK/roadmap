# Nasdanika SDK Roadmap

The public roadmap of the [Nasdanika SDK](https://sdk.nasdanika.org): what is planned for the runtime
and its models, as a book and as code.

- **The book:** [roadmap.sdk.nasdanika.org](https://roadmap.sdk.nasdanika.org), also as
  [PDF](https://roadmap.sdk.nasdanika.org/nasdanika-roadmap.pdf) and
  [EPUB](https://roadmap.sdk.nasdanika.org/nasdanika-roadmap.epub), and on
  [Leanpub](https://leanpub.com/nasdanika-sdk-roadmap) as part of the Nasdanika Body of Knowledge.
- **The code:** modules that may graduate to the SDK, with tests that show how to use them, built on
  the released runtime, so everything here can be tried today.
- **The models:** the nano-models that are to join the core model, and the models to migrate.

This is a roadmap and not a release. What is here is a sketch: it builds and its tests pass, and its
names and behavior may change. Nothing is published to Maven Central from this repository.

## What is in it

| Path | What it is | Status |
|---|---|---|
| `runtime/common` | Bindings: values that are read now and followed as they change. Waypoints: the history of an execution, with state, realms and telemetry | Sketch |
| `runtime/widget` | One API for many formats: views written against kinds, rendered as Markdown, GitHub Flavored Markdown and on a terminal | Sketch |
| `runtime/tui` | The terminal on TamboUI, and screenshots of screens as SVG | Sketch |
| `runtime/commander` | Nasdanika Commander: two panels over volumes, with the classic keys | Sketch, usable |
| `models/core` | Core nano-models: related, change, occupancy, kind, enterprise, document, example | Sketch |
| `models/core/sketches`, `models/runtime`, `models/tower`, `models/technology` | Models to migrate, as Xcore files to read | Planned |
| `tests/widgets` | One view rendered four ways, across modules | |
| `book` | The book and the site | |

The book describes each item: why, what it is, how it is used, what is not there yet, and how to help.

## Build

Java 25 and Maven 3.9.

```
mvn verify
```

builds the modules, runs the tests, generates the nano-models from Xcore, and renders the book into
`book/target/book` and the site into `book/target/site`. The runtime comes from Maven Central.

```
mvn verify -Dasciidoctor.skip=true          # everything but the book
mvn -pl runtime/commander -am package       # one module and what it needs
```

## Nasdanika Commander

A file manager for a terminal, for wherever the usual one is not installed:

```
mvn -q -pl runtime/commander -am package -DskipTests
java -jar runtime/commander/target/commander-2026.11.0.jar [left directory] [right directory]
```

`F3` view, `F4` edit, `Shift-F4` create, `F5` copy, `F6` move, `F7` new directory, `F8` delete, `F10`
quit, `F1` for all the keys. Every command also has a letter, for terminals that keep function keys
for themselves.

## Taking part

Try it, take a piece, say what is wrong with a design while it is cheap to change, contribute, or
sponsor an item. The chapter "Taking Part" of the book says how, and
[issues](https://github.com/Nasdanika-SDK/roadmap/issues) are the place to start.

If you work with an AI assistant, point it at this repository. `AGENTS.md` tells it how the repository
is laid out, and the chapter "Working with AI Assistants" has requests that work.

## How it is kept

The chapters say what holds now, and the "Revisions" appendix says when and why it came to hold. A
module graduates to the [runtime](https://github.com/Nasdanika-SDK/runtime) by dropping `roadmap` from
its names: `org.nasdanika.sdk.roadmap.runtime.widget` becomes `org.nasdanika.sdk.runtime.widget`.

## License

The code and the models are licensed under the [Eclipse Public License 2.0](LICENSE). The text and
figures of the book are licensed under the
[Creative Commons Attribution 4.0 International License](book/LICENSE). Neither license covers the
Nasdanika name or logo.
