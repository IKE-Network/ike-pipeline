# IKE Document Ingestion Standards

## Purpose

Ingestion is the process of importing an existing document into an IKE
documentation project by decomposing it into reusable topics, indexing
them, and wiring up an assembly that reconstructs the original document.

The target project already exists. Ingestion populates it with content.

## Prerequisites

- A target documentation multi-module project (the "into" project)
- A source document to ingest
- Familiarity with:
  - `IKE-TOPIC-DECOMPOSITION.md` — decomposition rules and granularity
  - `IKE-TOPIC-REGISTRY.md` — registry schema and maintenance
  - `IKE-ASCIIDOC-FRAGMENT.md` — fragment authoring conventions
  - `IKE-ASSEMBLY.md` — assembly document conventions
  - `IKE-INDEX.md` — index term authoring

## Standard Project Structure

Every documentation multi-module project follows this layout:

```
{project}/                          # reactor POM (packaging: pom)
├── topics/                         # STANDARD name — always "topics"
│   ├── pom.xml                     #   artifactId: topics
│   └── src/docs/asciidoc/
│       ├── index.adoc              #   all-topics HTML preview
│       ├── topic-registry.yaml     #   topic catalog
│       └── topics/                 #   topic fragments by domain
│           ├── {domain}/
│           │   └── {topic}.adoc
│           └── {domain}/
│               └── {topic}.adoc
├── {assembly-name}/                # descriptive name (e.g., arch-guide)
│   ├── pom.xml
│   └── src/docs/asciidoc/
│       └── {assembly-name}.adoc    #   assembly master document
├── {another-assembly}/             # additional assemblies as needed
└── pom.xml
```

### The `topics` module convention

- **Directory**: always `topics/`
- **ArtifactId**: always `topics`
- **GroupId**: carries project uniqueness (e.g., `network.ike`)
- **Unpack location**: `target/generated-sources/asciidoc/topics-asciidoc/`
- **AsciiDoc attribute**: `:topics: {generated}/topics-asciidoc`
- **Include pattern**: `include::{topics}/topics/{domain}/{topic}.adoc[leveloffset=+1]`

This convention means:
- Every doc project has topics in the same predictable location
- Assembly modules always depend on `${project.groupId}:topics:asciidoc:zip`
- Cross-project topic consumption follows a uniform pattern where
  groupId identifies the source

### Assembly modules

Assembly modules get descriptive names reflecting their content:
`arch-guide`, `dev-guide`, `compendium`, `safety-report`, etc.
The compendium assembly is the validation target — every published
topic must appear in it.

## Ingestion Workflow

### Step 1: Import

Receive the source document. Identify its structure:
- Heading hierarchy and section boundaries
- Content types (narrative, procedures, reference tables, diagrams)
- Cross-references and dependencies between sections
- Existing index terms or glossary entries

### Step 2: Decompose

Split the source into topic fragments per `IKE-TOPIC-DECOMPOSITION.md`:

1. Map sections to candidate topics with proposed IDs, types, and sizes.
2. Apply granularity rules (500-5000 chars, no partial sections).
3. Resolve cross-references to `xref:` macros using topic IDs.
4. Author each fragment per `IKE-ASCIIDOC-FRAGMENT.md` with index
   terms per `IKE-INDEX.md`.

### Step 3: Index

Register every topic in `topic-registry.yaml` per
`IKE-TOPIC-REGISTRY.md`:

- Assign domain, topic-id, type, keywords, and summary.
- Check for redundancy against existing topics in the registry.
- Resolve any overlaps before proceeding.

### Step 4: Place

Put topic files into the target project's `topics/` module:

1. Create domain directories under `topics/src/docs/asciidoc/topics/`
   if they don't exist.
2. Place each `.adoc` fragment in the appropriate domain directory.
3. Update `topics/src/docs/asciidoc/index.adoc` to include the new
   topics for the HTML preview.
4. Merge registry entries into `topic-registry.yaml`.

### Step 5: Assemble

Create or update an assembly in the target project:

1. If this is a new document, create an assembly module with a
   descriptive name and a POM that depends on `topics`.
2. Author the assembly `.adoc` file per `IKE-ASSEMBLY.md` with
   `include::` directives referencing the placed topics.
3. Add the assembly entry to `topic-registry.yaml` with nested
   `sections` mirroring the heading hierarchy.
4. Add the new module to the reactor POM's `<subprojects>`.

### Step 6: Validate

Build and verify:

```bash
mvn clean verify
```

- All `include::` paths resolve.
- All `xref:` targets resolve.
- Heading levels render correctly with `leveloffset`.
- No content from the source document was lost.
- Registry topic-count matches actual count.
- Every new topic appears in the compendium assembly.

## Dialog Ingestion

Dialogs (Socratic or dramatic dialogues) follow a simplified ingestion
workflow. They are **not decomposed** — the entire document becomes a
single topic. See `IKE-TOPIC-DECOMPOSITION.md` § "Dialog Topics."

### Dialog Ingestion Workflow

1. **Import**: Receive the source dialog document.
2. **Convert to fragment**: Strip document-level AsciiDoc attributes
   (`:doctype:`, `:toc:`, `:icons:`, etc.). Add topic metadata
   attributes (`:topic-id:`, `:topic-type: dialog`, etc.), the
   anchor, and a level-1 heading per `IKE-ASCIIDOC-FRAGMENT.md`.
   Preserve all dialog content — speakers, stage directions, and
   structure — intact.
3. **Add index terms**: Insert 5–15 index terms at points of first
   substantive discussion per `IKE-INDEX.md`.
4. **Place**: Put the single `.adoc` file in
   `topics/src/docs/asciidoc/topics/dialog/`.
5. **Register**: Add the topic entry to `topic-registry.yaml` under
   the `dialog` domain. Include a `notes` field documenting that this
   is a dialog topic exempt from size bounds.
6. **Assemble**: Add the topic to the `dialogs` assembly and to the
   compendium. If a `dialogs` assembly module does not yet exist,
   create one following the assembly module template in `IKE-DOC.md`.
7. **Update reactor**: Add the `dialogs` assembly module to the
   reactor POM's `<subprojects>` if it is new.
8. **Validate**: Build and verify per Step 6 of the standard workflow.

## Ingestion into an Existing Corpus

When the target project already has topics, follow the integration
workflow from `IKE-TOPIC-DECOMPOSITION.md` § "Topic Integration."
The additional constraint: search the existing registry and term index
for overlap before placing any new topics. Resolve redundancy before
committing.

## Instructing Claude for Ingestion

Provide:

1. The source document (pasted, uploaded, or as a file path).
2. The target project path.
3. A directive such as:

> Ingest this document into {target-project} per IKE-INGEST standards.
> Use domain prefix `{prefix}`. Create assembly module `{name}`.

Claude should:

1. Read the target project's `topic-registry.yaml` (if it exists).
2. Decompose the source document into topics.
3. Check for redundancy against existing topics.
4. Place topic files in `topics/src/docs/asciidoc/topics/{domain}/`.
5. Update the registry.
6. Create or update the assembly module.
7. Build and verify.
