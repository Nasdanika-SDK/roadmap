# Models

The models on the roadmap. The book's part "Models" describes them: what each is, what the SDK
version changes, and what it waits for.

| Path | What it is | Built |
|---|---|---|
| `core/` | The core nano-models as a Maven module: `related`, `change`, `occupancy`, `kind`, `enterprise`, `document` and `example`, one Xcore file each in `src/main/resources`. Java is generated into `src-gen` and compiled, and `NanoModelTests` instantiates each model | Yes |
| `core/sketches/` | Role, IAM, Seal and Lifecycle, which are to become nano-models, as they are in the model tower today | No |
| `runtime/` | Models of the runtime itself: `cli`, `tool`, `inference` and `expression` as drafted, and `cypher` as it is before the SDK | No |
| `tower/` | The model tower: 22 models as they are before the SDK | No |
| `technology/` | Technology models to migrate. Only GitHub has an Xcore file. The others are defined in Ecore in their own repositories | No |

The files that are not built were written against the older core model
(`org.nasdanika.models.nxcore`), so they do not generate here. They are kept to be read, and each
becomes a module when it is rewritten on the runtime's core model and the nano-models.

The tower and technology files are copies of the Xcore in the
[Nasdanika-Models](https://github.com/Nasdanika-Models) repositories as of November 2026. Those
repositories remain where the pre-SDK models are published and documented.

## Adding or migrating a model

1. For a sketch, add the Xcore file to the group it belongs to, with a header comment that says what
   the model is and why it is a model of its own.
2. To make it part of the build, give it a module with the layout of `core/`: the `xtext-maven-plugin`
   and the `build-helper-maven-plugin` from the root `pom.xml`, a `modelDirectory` of
   `/<module directory>/src-gen` in the Xcore file, a `module-info.java` that provides its packages
   as `EPackage` services, and a test that instantiates it.
3. Add or change its row in the book: the chapter for its group, and the items appendix.
