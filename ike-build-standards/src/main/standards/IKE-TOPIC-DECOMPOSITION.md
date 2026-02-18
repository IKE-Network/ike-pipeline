# IKE Topic Decomposition Standards

## Purpose

This document defines the rules for decomposing large-format documents (DocBook, AsciiDoc, or
other structured content) into reusable topic fragments suitable for inclusion in multiple
assembly documents. The approach is modeled on DITA topic-based authoring adapted to AsciiDoc
and Maven build pipelines.

## Definitions

- **Topic**: A self-contained unit of content that addresses a single subject. A topic is the
  atomic unit of reuse.
- **Assembly**: A document that composes topics into a coherent deliverable via `include::`
  directives. See `IKE-ASSEMBLY.md`.
- **Compendium**: The master assembly containing all available topics. No reader consumes it
  linearly; it serves as the comprehensive reference and validation target.
- **Registry**: The `topic-registry.yaml` file that catalogs all topics. See
  `IKE-TOPIC-REGISTRY.md`.

## Topic Types

Every topic must be classified as exactly one of:

| Type        | Purpose                                    | Example                              |
|-------------|--------------------------------------------|--------------------------------------|
| `concept`   | Explains what something is or why it exists | "Coordinate-Based Versioning"        |
| `task`      | Step-by-step procedure to accomplish a goal | "Configuring a New Module Namespace" |
| `reference` | Lookup information (tables, schemas, APIs)  | "Coordinate Field Definitions"       |

If a source section mixes explanation with procedure, split it into a concept topic and a task
topic. Do not create hybrid topics.

## Granularity Rules

### Size Bounds

- **Target**: 1500–3000 characters per topic (roughly 0.75–1.5 printed pages).
- **Hard minimum**: 500 characters. Below this, the topic is likely too fine-grained — consider
  merging with a related topic.
- **Hard maximum**: 5000 characters. Above this, the topic almost certainly addresses multiple
  concerns and should be decomposed further.
- **Exceptions**: Reference topics (tables, schemas) may exceed 5000 characters when the table
  or listing is indivisible. Document the exception in the registry entry with a note.

### Structural Integrity

- A topic must never contain a partial section. If the source has a section "Versioning" with
  subsections "Temporal Coordinates" and "Module Coordinates," the valid decompositions are:
  - One topic covering all of "Versioning" (if it fits within size bounds).
  - Three topics: a parent concept topic for "Versioning" plus one topic per subsection.
  - Two topics: one per subsection (if the parent section is purely organizational with no
    substantive content of its own).
- **Never** split mid-paragraph or mid-list to meet size targets.

### Self-Containment

- A reader should be able to understand a topic without reading other topics, though
  cross-references to related topics are expected and encouraged.
- Avoid forward references that make the topic incomprehensible without the referenced material.
  Prefer a brief inline summary followed by an `xref:` for detail.
- Each topic must have a clear opening sentence or short paragraph that establishes context.
  Do not begin with "As described above..." or similar relational phrasing.

## Decomposition Procedure

### Step 1: Structural Analysis

Examine the source document's heading hierarchy. Map each section to a candidate topic,
annotating it with:

- Proposed `topic-id`
- Proposed `topic-type`
- Estimated character count
- Dependencies on other candidate topics

### Step 2: Apply Granularity Rules

For each candidate topic:

- If the content exceeds 5000 characters, identify natural split points (subsections,
  distinct concerns, concept-vs-task boundaries).
- If the content is below 500 characters, identify a sibling or parent topic to merge with.
- Verify structural integrity — no partial sections.

### Step 3: Resolve Cross-References

- Replace internal cross-references (e.g., DocBook `<xref linkend="...">`) with AsciiDoc
  `xref:` macros using `topic-id` as the anchor.
- Record dependencies in the registry fragment.
- For references to content outside the current decomposition scope, use a placeholder:
  `// TODO: xref to [description of target]` and note it in the registry entry's
  `status: draft` with a comment.

### Step 4: Extract and Author

For each topic, produce:

1. An `.adoc` file conforming to `IKE-ASCIIDOC-FRAGMENT.md`.
2. A registry entry conforming to `IKE-TOPIC-REGISTRY.md`.

### Step 5: Validate Assembly

Include all new topics in the compendium assembly and build. Verify:

- All `include::` paths resolve.
- All `xref:` targets resolve.
- Heading levels render correctly with `leveloffset`.
- No content from the source document was lost.

## Directory Placement

Topics are organized by domain under `src/docs/asciidoc/topics/`:

```
topics/
  {domain}/
    {sub-domain}/         # optional nesting, max 3 levels
      {topic-slug}.adoc
```

- `{domain}` corresponds to a major subject area (e.g., `architecture`, `terminology`,
  `safety`, `operations`).
- `{topic-slug}` is the final segment of the `topic-id`, using lowercase kebab-case.
- Mirror the directory structure in the `topic-id`: `arch-coord-versioning` maps to
  `topics/architecture/coord-versioning.adoc`.

## Naming Conventions

- **topic-id**: `{domain-prefix}-{descriptive-slug}`, lowercase kebab-case. Once assigned, a
  `topic-id` is **immutable** — it is an identifier, not a description. If the topic's scope
  changes enough that the name is misleading, create a new topic and deprecate the old one.
- **File name**: Matches the slug portion of the `topic-id` with `.adoc` extension.
- **Domain prefixes**: Define in the registry's `domains` section. Keep them short (3-5 chars):
  `arch`, `term`, `safe`, `ops`, `api`, etc.

## Handling Special Content During Decomposition

### Images and Diagrams

- Place images in `src/docs/asciidoc/images/{domain}/` mirroring the topic directory structure.
- Use relative paths from the topic file: `image::{imagesdir}/{domain}/diagram.png[]`.
- If an image is shared across topics, place it in `images/shared/` and note the sharing in
  both registry entries.

### Tables

- If a table is the primary content of a section, it becomes a `reference` topic.
- If a table is supporting content within a concept or task, it stays with that topic.
- Do not split a table across topics.

### Code Listings

- Short listings (under 30 lines) stay inline in the topic.
- Longer listings should be extracted to a separate file under `examples/{domain}/` and
  included with `include::`.

### Admonitions and Sidebars

- Keep admonitions with their parent topic.
- Sidebars that are substantive (over 500 chars) and independently meaningful should become
  their own concept topic.

## Instructing Claude for Decomposition

When requesting decomposition in a chat or Claude Code session, provide:

1. The source content (pasted or as an uploaded file).
2. The target domain and any existing registry context (upload `topic-registry.yaml` or the
   relevant domain section).
3. A directive such as:

> Decompose this into topics per IKE-TOPIC-DECOMPOSITION standards. Use domain prefix `term`.
> Output each topic as a separate `.adoc` file conforming to IKE-ASCIIDOC-FRAGMENT, and
> provide the registry YAML fragment conforming to IKE-TOPIC-REGISTRY.

Claude should produce:

- Individual `.adoc` files ready for placement in the topic directory.
- A YAML fragment that can be merged into `topic-registry.yaml`.
- A brief decomposition rationale noting any judgment calls (merges, splits, type assignments).
