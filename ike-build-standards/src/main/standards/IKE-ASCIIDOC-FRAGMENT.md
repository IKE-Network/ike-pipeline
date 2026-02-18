# IKE AsciiDoc Fragment Authoring Conventions

## Purpose

This document defines the authoring conventions for individual AsciiDoc topic files (fragments)
in the IKE topic library. Fragments are designed for reuse — they are included into assembly
documents via `include::` directives with `leveloffset` adjustments. These conventions ensure
that fragments compose correctly regardless of where they appear in an assembly's heading
hierarchy.

## File Structure

Every topic `.adoc` file must follow this structure:

```asciidoc
// {topic-id}
// Topic: {title}
// Type: {concept|task|reference}
// Status: {draft|review|published|deprecated}
:topic-id: arch-coord-versioning
:topic-type: concept
:topic-status: draft
:topic-keywords: versioning, coordinates, STAMP, temporal

[[{topic-id}]]
= {Title}

{Content body}
```

### Breakdown

1. **Comment header** (lines 1–4): Human-readable metadata for quick identification when
   viewing raw files. These are AsciiDoc comments and do not render.

2. **Attribute block** (lines 5–8): Machine-readable metadata. These attributes are available
   to the build pipeline and can be extracted for registry validation.

3. **Anchor** (line 10): An inline anchor using the `topic-id` as the anchor name. This is
   the cross-reference target. It must immediately precede the heading.

4. **Level-1 heading** (line 11): Every fragment starts with a level-1 heading (`=`). The
   assembly controls the actual rendered level via `leveloffset` in the `include::` directive.

5. **Content body** (line 13+): The topic content.

## Heading Rules

### Always Start at Level 1

Fragments must use `= Title` as their top-level heading, regardless of where they will appear
in an assembly. The assembly's `include::` directive applies `leveloffset` to shift headings
to the correct depth.

```asciidoc
// In the fragment:
= Coordinate-Based Versioning        <-- level 1 in the fragment

== Temporal Coordinates               <-- level 2 in the fragment
```

```asciidoc
// In the assembly:
== Architecture
include::topics/architecture/coord-versioning.adoc[leveloffset=+1]
// Renders "Coordinate-Based Versioning" as level 2 (=== equivalent)
// Renders "Temporal Coordinates" as level 3 (==== equivalent)
```

### Maximum Depth

Fragments should contain at most 2 levels of internal headings (the title plus one sublevel).
If deeper nesting is needed, the topic is likely too broad and should be decomposed further
per `IKE-TOPIC-DECOMPOSITION.md`.

### No Orphan Headings

Every heading must have content below it before the next heading. Do not use headings as
labels for empty sections or as organizational placeholders.

## Document Attributes: What to Include and What to Omit

### Include in Fragments

| Attribute         | Purpose                          | Example                                |
|-------------------|----------------------------------|----------------------------------------|
| `:topic-id:`      | Unique identifier                | `arch-coord-versioning`                |
| `:topic-type:`    | concept, task, or reference      | `concept`                              |
| `:topic-status:`  | Lifecycle status                 | `published`                            |
| `:topic-keywords:`| Comma-separated keyword list     | `versioning, coordinates, STAMP`       |

### Never Include in Fragments

These are document-level concerns controlled by the assembly:

- `:doctype:` — set by the assembly
- `:toc:` or `:toc-placement:` — set by the assembly
- `:sectnums:` — set by the assembly
- `:imagesdir:` — set by the assembly or build plugin configuration
- `:source-highlighter:` — set by the build plugin
- `:icons:` — set by the assembly
- Any attribute that affects global document rendering

## Cross-References

### Referencing Other Topics

Use the standard AsciiDoc `xref:` macro with the target topic's `topic-id` as the anchor:

```asciidoc
For details on the module coordinate, see xref:arch-module-coordinates[Module Coordinates].
```

The anchor `arch-module-coordinates` corresponds to the `[[arch-module-coordinates]]` anchor
in the target topic's fragment. This works within a single assembly because all fragments
are included into the same document tree.

### Cross-References Across Assemblies

If a topic needs to reference content that may not be present in every assembly, use a
conditional include or a note:

```asciidoc
ifdef::compendium[]
See xref:term-snomed-concept-model[SNOMED CT Concept Model] for terminology details.
endif::[]
ifndef::compendium[]
NOTE: For terminology details, refer to the SNOMED CT Concept Model topic in the IKE
Compendium.
endif::[]
```

Use this sparingly. Most cross-references should work within any assembly that includes both
the source and target topics.

### Placeholder Cross-References

During decomposition, when the target topic does not yet exist:

```asciidoc
// TODO: xref to description logic classifier topic (not yet decomposed)
```

Mark the topic as `status: draft` in the registry with a `notes` field documenting the
unresolved reference.

## Content Conventions

### Opening Paragraph

Every topic must begin with a context-setting paragraph immediately after the title heading.
This paragraph should:

- Establish what the topic covers in 1–2 sentences.
- Be comprehensible without having read any other topic.
- Not begin with relational phrases like "As mentioned in..." or "Building on the previous
  section..."

Good:
```asciidoc
= Coordinate-Based Versioning

Coordinate-based versioning identifies each component version using a tuple of module, path,
and temporal coordinates. This pattern decouples version identity from sequential numbering
and supports concurrent development across organizational boundaries.
```

Bad:
```asciidoc
= Coordinate-Based Versioning

As discussed in the architecture overview, we need a versioning approach. This section
explains coordinates.
```

### Admonitions

Use standard AsciiDoc admonitions. Keep them concise:

```asciidoc
NOTE: Coordinate values are immutable once published. Corrections require a new coordinate.

WARNING: Changing a published topic-id breaks cross-references across all assemblies.

IMPORTANT: The module coordinate must match a registered namespace in the governance registry.
```

### Code Listings

Use source blocks with language annotation:

```asciidoc
[source,java]
----
public interface StampCoordinate {
    ModuleCoordinate getModule();
    PathCoordinate getPath();
    TemporalCoordinate getTime();
}
----
```

For listings over 30 lines, extract to a separate file:

```asciidoc
[source,java]
----
\include::examples/architecture/stamp-coordinate.java[]
----
```

### Tables

Use AsciiDoc table syntax. Always include a title:

```asciidoc
.Coordinate Fields
[cols="1,2,3", options="header"]
|===
| Field    | Type               | Description
| Module   | ModuleCoordinate   | Identifies the authoring organization and namespace
| Path     | PathCoordinate     | Identifies the development branch or release context
| Time     | TemporalCoordinate | Identifies the point-in-time or range
|===
```

### Images

Reference images using the `imagesdir` attribute (set by the assembly or build):

```asciidoc
.Architecture layer diagram
image::{imagesdir}/architecture/layer-diagram.png[Architecture layers, width=600]
```

- Always include alt text (the text in square brackets).
- Always include a block title (the line starting with `.`).
- Place image files in `src/docs/asciidoc/images/{domain}/`.

## Task-Type Topic Conventions

Task topics follow a structured pattern:

```asciidoc
= Configuring a New Module Namespace

Brief context paragraph explaining when and why you would perform this task.

.Prerequisites
- Access to the governance registry
- A registered organization identifier

.Procedure
. Log into the governance console.
. Navigate to **Namespaces > Register New**.
. Enter the module identifier using the format `{org}.{project}.{module}`.
. Submit the registration request.

.Verification
After registration, verify the namespace appears:

[source,bash]
----
ike namespace list --org my-org
----

.Result
The new namespace is available for use in coordinate-based version identifiers.
```

Use titled blocks (`.Prerequisites`, `.Procedure`, `.Verification`, `.Result`) rather than
headings for the internal structure of task topics. This keeps the heading depth shallow
and provides visual consistency across task topics.

## Reference-Type Topic Conventions

Reference topics are primarily structured data — tables, schemas, or specification extracts:

```asciidoc
= Coordinate Field Definitions

This reference defines all fields in the STAMP coordinate system.

.STAMP Coordinate Fields
[cols="1,1,2,1", options="header"]
|===
| Field    | Type               | Description                              | Required
| Module   | ModuleCoordinate   | Authoring namespace                      | Yes
| Path     | PathCoordinate     | Development context                      | Yes
| Time     | TemporalCoordinate | Point-in-time or range                   | Yes
| Author   | AuthorCoordinate   | Individual contributor                   | No
|===
```

Reference topics should minimize narrative and maximize scannable structure.

## Things to Avoid

- **Document-level attributes**: `:doctype:`, `:toc:`, `:sectnums:`, etc.
- **Preamble text before the title heading**: The anchor and title must come first (after the
  attribute block).
- **Hard-coded heading levels**: Never use `===` as the top heading in a fragment to "pre-adjust"
  for assembly context. Always use `=` and let `leveloffset` do the work.
- **Inline HTML**: Stick to AsciiDoc markup.
- **Relative paths outside the topic tree**: All paths should resolve relative to the
  `src/docs/asciidoc/` root or via build-configured attributes like `{imagesdir}`.
- **Conditional logic for content within a single topic**: If content varies by audience or
  context, create separate topics rather than using `ifdef` blocks within the body. Reserve
  `ifdef` for cross-reference handling as described above.
