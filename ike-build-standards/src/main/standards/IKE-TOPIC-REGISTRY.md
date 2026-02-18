# IKE Topic Registry Standards

## Purpose

The `topic-registry.yaml` file is the authoritative catalog of all topics in a topic library
module. It serves three functions:

1. **Build validation**: CI checks that every `.adoc` file under `topics/` has a registry
   entry and every registry entry resolves to a file.
2. **Assembly planning**: Authors and tooling use the registry to understand what content
   exists, its status, and its dependencies when constructing assembly documents.
3. **Claude navigation**: The registry provides Claude (chat or Claude Code) with a searchable
   index of the corpus so that content can be located by keyword, topic-id, or domain without
   uploading the full topic library.

## File Location

```
{topic-library-module}/src/docs/asciidoc/topic-registry.yaml
```

The registry travels with the topic content it catalogs. When the topic library is packaged
and unpacked into a dependent module's `target/` directory, the registry is available alongside
the topics.

## Schema

```yaml
# topic-registry.yaml
registry-version: "1.0"            # schema version for forward compatibility
generated: 2025-06-15              # ISO date of last registry update
topic-count: 247                   # total number of topic entries (validation target)

domains:
  - id: arch                       # domain identifier, used as topic-id prefix
    title: "System Architecture"   # human-readable domain name
    description: >                 # optional: domain scope statement
      Topics covering system architecture, design patterns,
      and infrastructure decisions.
    topics:                        # ordered list of topics in this domain
      - id: arch-overview
        file: topics/architecture/overview.adoc
        title: "Architecture Overview"
        type: concept
        keywords: [architecture, overview, IKE, layers]
        status: published
        char-count: 2340
        dependencies: []
        summary: >
          High-level overview of the IKE layered architecture including
          knowledge graph, reasoning, and API tiers.

assemblies:                        # catalog of assembly documents
  - id: compendium
    file: compendium.adoc
    title: "IKE Compendium"
    description: "Master assembly containing all topics."
    topic-refs:
      - arch-overview
      - arch-coord-versioning
      # ... exhaustive ordered list

  - id: versioning-guide
    file: guides/versioning-guide.adoc
    title: "Versioning Guide"
    description: "Targeted guide for version management."
    topic-refs:
      - arch-coord-versioning
      - arch-version-migration
```

## Field Definitions: Topic Entry

### Required Fields

| Field          | Type       | Description                                                  |
|----------------|------------|--------------------------------------------------------------|
| `id`           | string     | Unique topic identifier. Format: `{domain-prefix}-{slug}`, lowercase kebab-case. Immutable once assigned. |
| `file`         | string     | Relative path from the `src/docs/asciidoc/` root to the `.adoc` file. |
| `title`        | string     | Human-readable title. Should match the level-1 heading in the `.adoc` file. |
| `type`         | enum       | One of: `concept`, `task`, `reference`.                      |
| `keywords`     | string[]   | 3–8 searchable terms. Include synonyms and abbreviations that a searcher might use. Do not repeat words from the title. |
| `status`       | enum       | One of: `draft`, `review`, `published`, `deprecated`.        |
| `summary`      | string     | 1–2 sentences describing the topic's content. Written in indicative mood ("Describes the..." not "This topic describes..."). Must be useful for search — include key terms not covered by `keywords`. |

### Optional Fields

| Field          | Type       | Description                                                  |
|----------------|------------|--------------------------------------------------------------|
| `char-count`   | integer    | Character count of the `.adoc` content (excluding attribute block). Updated on each decomposition pass. Used for granularity validation. |
| `dependencies` | string[]   | List of `topic-id` values that this topic cross-references. Represents "this topic links to" relationships. |
| `supersedes`   | string     | `topic-id` of a deprecated topic that this topic replaces.   |
| `notes`        | string     | Free-text notes for authors and Claude. Use for documenting exceptions (e.g., "Exceeds 5000 chars — indivisible reference table"). |

## Field Definitions: Assembly Entry

| Field          | Type       | Description                                                  |
|----------------|------------|--------------------------------------------------------------|
| `id`           | string     | Unique assembly identifier. Lowercase kebab-case.            |
| `file`         | string     | Relative path to the assembly `.adoc` file.                  |
| `title`        | string     | Human-readable title of the assembled document.              |
| `description`  | string     | Brief description of the assembly's purpose and audience.    |
| `topic-refs`   | string[]   | Ordered list of `topic-id` values included in this assembly. Order reflects document order. |

## Topic ID Construction Rules

1. Format: `{domain-prefix}-{descriptive-slug}`
2. Domain prefix: 2–5 lowercase characters matching a `domains[].id` in the registry.
3. Slug: lowercase kebab-case, 2–5 words, descriptive of content.
4. Total length: aim for under 40 characters.
5. **Immutability**: Once a `topic-id` is assigned and committed, it must not be changed. Other
   topics, assemblies, and external documents may reference it. If a topic's scope changes
   substantially, create a new topic and set `status: deprecated` on the old one with a
   `notes` field pointing to the replacement.

Examples:
- `arch-coord-versioning` — architecture domain, describes coordinate-based versioning
- `term-snomed-concept-model` — terminology domain, SNOMED CT concept model
- `safe-usc-hazard-analysis` — safety domain, unsafe control action hazard analysis
- `ops-maven-release-process` — operations domain, Maven release procedure

## Status Lifecycle

```
draft → review → published
                     ↓
                deprecated
```

- **draft**: Content is being authored or decomposed. May contain TODOs and placeholders.
- **review**: Content is complete and awaiting technical review.
- **published**: Content is reviewed and approved for inclusion in assemblies.
- **deprecated**: Content is superseded or no longer applicable. Retained in the registry for
  reference stability but excluded from new assemblies. Set `supersedes` on the replacement
  topic if one exists.

## Keyword Guidelines

Keywords are the primary mechanism for Claude to locate topics by subject matter. Follow these
rules:

1. **3–8 keywords per topic.** Fewer is too sparse for search; more dilutes relevance.
2. **Include synonyms and abbreviations**: If the topic discusses "description logic," also
   include `DL` and `classifier`. If it covers SNOMED CT, include `SCT`.
3. **Do not repeat title words**: The title is already searchable. Keywords should expand
   coverage.
4. **Prefer specific terms over generic**: `stamp-coordinate` over `coordinate`;
   `el-profile` over `profile`.
5. **Include the names of key standards, systems, or specifications** referenced in the topic.

## Summary Guidelines

Summaries serve double duty as human-readable abstracts and Claude search targets. They should:

1. Be 1–2 sentences, 100–250 characters.
2. Use indicative mood: "Describes the coordinate-based versioning pattern..." not "This topic
   describes..."
3. Include 2–3 key terms not already in `keywords` or `title`.
4. Be specific enough that a reader (or Claude) can determine relevance without opening the
   file.

Bad: "Covers versioning." (too vague)
Good: "Describes the coordinate-based versioning pattern where each component version is
identified by module, path, and temporal coordinates within the STAMP model."

## Maintenance Rules

### When to Update

Update the registry whenever:

- A topic is created, modified, split, merged, or deprecated.
- A topic's status changes.
- An assembly's topic list changes.
- A decomposition session produces new topics.

### Who Updates

- **Claude (chat or Claude Code)**: Always produces registry YAML fragments as part of
  decomposition or topic creation. Fragments are reviewed and merged by the author.
- **Authors**: Responsible for final merge and commit. The registry is a source-controlled
  artifact.

### Validation

The CI build should enforce:

1. Every `.adoc` file under `topics/` has a corresponding registry entry with a matching `id`
   and `file` path.
2. Every registry entry's `file` path resolves to an existing `.adoc` file.
3. `topic-count` matches the actual count of topic entries.
4. All `dependencies` reference valid `topic-id` values.
5. All `topic-refs` in assemblies reference valid `topic-id` values.
6. No duplicate `topic-id` values exist.

A Maven Enforcer rule or a lightweight validation script invoked during `validate` phase can
perform these checks.

## Working with Claude

### Providing Context

At the start of a session involving topic work, upload or paste the `topic-registry.yaml` file
(or the relevant domain section if the full file is too large). This gives Claude the complete
map of existing content.

For a 600-page compendium decomposed into ~300 topics, the registry will be roughly 15–20 KB
of YAML — well within context window limits.

### Requesting Topic Lookup

To find existing content without uploading topic files:

> Which topics cover STAMP coordinates? (Check the registry.)

Claude will search the registry's `title`, `keywords`, and `summary` fields to identify
matching topics and report their `topic-id`, `title`, and `summary`.

### Requesting Registry Updates

After any topic creation or modification:

> Provide the updated registry YAML fragment for the topics we just created.

Claude will produce a YAML block ready for merge into `topic-registry.yaml`.
