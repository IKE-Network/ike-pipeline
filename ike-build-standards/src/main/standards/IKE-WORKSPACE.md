# IKE Workspace Conventions

## What is an IKE Workspace?

An IKE Workspace is a multi-repository development environment managed
through `workspace.yaml` — a YAML manifest that declares all components,
their inter-repository dependencies, groups, and component types.

Workspace operations are implemented as Maven plugin goals in
`ike-maven-plugin`, invokable via the `ike:` prefix (requires
`network.ike` in `~/.m2/settings.xml` `<pluginGroups>`).

## Prerequisites

### Maven Settings

Add `network.ike` to `<pluginGroups>` in `~/.m2/settings.xml`:

```xml
<settings>
  <pluginGroups>
    <pluginGroup>network.ike</pluginGroup>
  </pluginGroups>
</settings>
```

### Workspace POM

The workspace root must contain a `pom.xml` that declares `ike-maven-plugin`:

```xml
<project xmlns="http://maven.apache.org/POM/4.1.0" ...>
    <modelVersion>4.1.0</modelVersion>
    <groupId>network.ike</groupId>
    <artifactId>ike-workspace</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>

    <properties>
        <ike-maven-plugin.version>24-SNAPSHOT</ike-maven-plugin.version>
    </properties>

    <build>
        <plugins>
            <plugin>
                <groupId>network.ike</groupId>
                <artifactId>ike-maven-plugin</artifactId>
                <version>${ike-maven-plugin.version}</version>
            </plugin>
        </plugins>
    </build>

    <!-- File-activated profiles for partial checkout -->
    <profiles>
        <profile>
            <id>component-name</id>
            <activation>
                <file><exists>${project.basedir}/component-name/pom.xml</exists></file>
            </activation>
            <subprojects>
                <subproject>component-name</subproject>
            </subprojects>
        </profile>
        <!-- Repeat for each component -->
    </profiles>
</project>
```

File-activated profiles enable incremental IntelliJ builds: only checked-out
components participate in the reactor. Missing components are silently skipped.

## workspace.yaml Manifest

The manifest lives at the workspace root alongside `pom.xml`:

```yaml
schema-version: "1.0"
generated: 2026-02-25

defaults:
  branch: main

component-types:
  infrastructure:
    description: "Build tooling, parent POMs"
    build-command: "mvn clean install"
    checkpoint-mechanism: git-tag
  software:
    description: "Java libraries and applications"
    build-command: "mvn clean install"
    checkpoint-mechanism: git-tag
  document:
    description: "AsciiDoc topic libraries and assemblies"
    build-command: "mvn clean verify"
    checkpoint-mechanism: git-tag

components:
  - name: ike-pipeline
    type: infrastructure
    repo: git@github.com:IKE-Community/ike-pipeline.git
    version: "24-SNAPSHOT"
    depends-on: []

  - name: tinkar-core
    type: software
    repo: git@github.com:ikmdev/tinkar-core.git
    version: "1.80.0-SNAPSHOT"
    depends-on:
      - component: ike-pipeline
        relationship: build

groups:
  core: [ike-pipeline, tinkar-core]
  all: [ike-pipeline, tinkar-core, ...]
```

### Manifest Fields

| Field | Required | Description |
|-------|----------|-------------|
| `schema-version` | Yes | Schema version for forward compatibility |
| `defaults.branch` | Yes | Default branch for all components |
| `component-types` | Yes | Named types with build commands and checkpoint mechanisms |
| `components` | Yes | List of repositories in the workspace |
| `groups` | No | Named subsets for partial operations |

### Component Fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Unique identifier (matches directory name) |
| `type` | Yes | References a `component-types` key |
| `repo` | Yes | Git clone URL |
| `branch` | No | Override default branch |
| `version` | No | Current version string (null/`~` for unversioned) |
| `group-id` | No | Maven groupId for version updates |
| `depends-on` | No | List of dependency declarations |

### Dependency Relationships

```yaml
depends-on:
  - component: ike-pipeline
    relationship: build      # needs compiled artifacts
  - component: tinkar-core
    relationship: content    # references architecture/concepts
  - component: ike-pipeline
    relationship: tooling    # uses CLI tools or plugins
```

Relationship types matter for cascade analysis: `build` dependencies require
rebuild; `content` dependencies may require only review.

## Goal Reference

### Workspace Goals

| Goal | Description |
|------|-------------|
| `ike:verify` | Validate manifest consistency (deps exist, no cycles, groups resolve, types defined) |
| `ike:status` | Git status across all repos (branch, dirty/clean, branch mismatch detection) |
| `ike:cascade` | Show downstream impact of a change (`-Dcomponent=<name>` required) |
| `ike:graph` | Print dependency graph (text or `-Dformat=dot` for Graphviz DOT) |
| `ike:init` | Clone/initialize repos from manifest (Syncthing-aware) |
| `ike:pull` | `git pull --rebase` across repos |
| `ike:stignore` | Generate `.stignore` files for Syncthing |
| `ike:dashboard` | Composite: verify + status + cascade-from-dirty |

### Gitflow Goals

| Goal | Description |
|------|-------------|
| `ike:feature-start` | Create `feature/<name>` branch with branch-qualified version |
| `ike:feature-finish` | Merge feature branch to main with `--no-ff`, strip qualifier, tag |
| `ike:ws-checkpoint` | Record multi-repo checkpoint YAML (SHAs, versions, dirty flags) |

### Common Options

| Option | Applicable Goals | Description |
|--------|------------------|-------------|
| `-Dworkspace.manifest=<path>` | All workspace goals | Path to workspace.yaml (auto-detected by searching upward) |
| `-Dgroup=<name>` | status, init, pull, feature-start, feature-finish | Restrict to named group |
| `-Dcomponent=<name>` | cascade | Component to analyze (required) |
| `-Dformat=dot` | graph | Graphviz DOT output |
| `-Dfeature=<name>` | feature-start, feature-finish | Feature name (branch: `feature/<name>`) |
| `-DskipVersion=true` | feature-start | Skip POM version qualification |
| `-DtargetBranch=<name>` | feature-finish | Merge target (default: `main`) |
| `-Dpush=true` | feature-finish, ws-checkpoint | Push to origin |
| `-Dtag=true` | ws-checkpoint | Tag each component |
| `-DdryRun=true` | feature-start, feature-finish | Show plan without executing |
| `-Dname=<name>` | ws-checkpoint | Checkpoint name (required) |

## Version Convention

Feature branches use branch-qualified versions:

```
<base-version>-<safe-branch-name>-SNAPSHOT
```

The main branch uses the unqualified version:

```
<base-version>-SNAPSHOT
```

`ike:feature-start` sets this automatically by updating all POM files in the
reactor. When creating files or modifying POMs in a workspace, respect the
branch-qualified version already set.

Safe branch name: replace `/` with `-` in the Git branch name.

## Maven 4 Project-Local Repository

Each workspace isolates installed artifacts via `.mvn/maven.properties`:

```
maven.repo.local.path.installed=${session.rootDirectory}/.mvn/local-repo
```

Do not modify this configuration. Do not reference artifacts from
other workspaces' local repositories.

## Syncthing

Working trees are synced between machines via Syncthing.
Use `ike:stignore` to generate deterministic `.stignore` files that exclude:

- `**/target`
- `**/.git`
- `**/.idea`
- `**/.DS_Store`
- `**/.claude/worktrees`
- `**/.mvn/local-repo`

Each machine has independent Git state, build output, and IDE config.
`ike:init` is Syncthing-aware: when a directory already exists (synced by
Syncthing but not yet a git repo), it runs `git init` + `git reset` instead
of `git clone`.

## Partial Checkout

File-activated profiles in the workspace POM enable partial checkout:
only cloned components participate in the reactor. This supports:

- **Incremental IntelliJ builds**: Open the workspace POM; only checked-out
  modules appear in the project tree.
- **Selective `mvn -pl -am`**: Build a specific component and its
  dependencies within the workspace.
- **New developer onboarding**: Clone workspace, run `ike:init -Dgroup=core`,
  build immediately with a minimal set.

## Checkpoint Files

`ike:ws-checkpoint` records per-component state to
`checkpoints/checkpoint-<name>.yaml`:

```yaml
checkpoint:
  name: "release-1.0"
  created: "2026-03-20T17:00:00Z"
  components:
    ike-pipeline:
      sha: "a1b2c3d..."
      short-sha: "a1b2c3d"
      branch: "main"
      type: infrastructure
      version: "24-SNAPSHOT"
      dirty: false
```

Checkpoint files are committed to the workspace repository.
Optional tagging (`-Dtag=true`) creates `checkpoint/<name>/<component>`
tags in each component's repo.

## Key Rules

- Never use `${revision}` for version indirection. Versions are literal in POMs.
- All reactor modules share a unified version.
- The version in the root POM is the single source of truth.
- Branch-qualified versions are set once at feature-start and committed.
- Workspace manifest (`workspace.yaml`) is the inter-repository dependency graph.
- The aggregator POM and the manifest are complementary: POM drives `mvn`,
  YAML drives `ike:` workspace goals.
