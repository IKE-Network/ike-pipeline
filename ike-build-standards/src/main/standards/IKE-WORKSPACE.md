# IKE Workspace Conventions

## What is an IKE Workspace?

An IKE Workspace is a full Git clone in a conventionally named directory
under `~/Projects/ike/`. Each active branch gets its own workspace.
Use the `ike-workspace` script to create and manage workspaces.

## Directory Convention

    ~/Projects/ike/<safe-branch-name>/

Safe branch name: replace `/` with `-` in the Git branch name.

## Version Convention

Feature branches use branch-qualified versions:

    <base-version>-<safe-branch-name>-SNAPSHOT

The main branch uses the unqualified version:

    <base-version>-SNAPSHOT

The `ike-workspace` script sets this automatically via `mvn versions:set`.
When creating files or modifying POMs in a workspace, respect the
branch-qualified version already set.

## Maven 4 Project-Local Repository

Each workspace isolates installed artifacts via `.mvn/maven.properties`:

    maven.repo.local.path.installed=${session.rootDirectory}/.mvn/local-repo

Do not modify this configuration. Do not reference artifacts from
other workspaces' local repositories.

## Syncthing

Working trees are synced between machines via Syncthing.
`.git/`, `target/`, `.mvn/local-repo/`, and `.idea/` are excluded.
Each machine has independent Git state, build output, and IDE config.

## Key Rules

- Never use `${revision}` for version indirection. Versions are literal in POMs.
- All reactor modules share a unified version.
- The version in `ike-parent/pom.xml` is the single source of truth.
- Branch-qualified versions are set once at workspace creation and committed.
- Use `ike-workspace --from-here` to create sub-feature branches.
