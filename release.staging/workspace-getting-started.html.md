---
date_published: 2026-02-21
date_modified: 2026-02-21
canonical_url: https://github.com/IKE-Network/ike-pipeline/workspace-getting-started.html
---

# Workspace Developer Getting Started

Table of Contents 

- [Prerequisites](#prerequisites)
- [First-Time Workspace Setup](#first-time-workspace-setup) 
  
    - [Clone the workspace repository](#clone-the-workspace-repository)
    - [Initialize components](#initialize-components)
    - [Open in IntelliJ](#open-in-intellij)
- [Daily Workflow](#daily-workflow) 
  
    - [Sync all repositories](#sync-all-repositories)
    - [Check status](#check-status)
    - [Full overview](#full-overview)
- [Starting a Feature](#starting-a-feature) 
  
    - [Create the feature branch](#create-the-feature-branch)
    - [Work and commit](#work-and-commit)
    - [Save a checkpoint](#save-a-checkpoint)
    - [Preview the merge](#preview-the-merge)
    - [Finish the feature](#finish-the-feature)
- [Releasing](#releasing) 
  
    - [Preview the release plan](#preview-the-release-plan)
    - [Execute the release](#execute-the-release)
- [Multi-Machine Development (Syncthing)](#multi-machine-development-syncthing) 
  
    - [Generate ignore patterns](#generate-ignore-patterns)
    - [Resume on another machine](#resume-on-another-machine)
- [Troubleshooting](#troubleshooting) 
  
    - [ws-release fails mid-cascade](#ws-release-fails-mid-cascade)
    - [Merge conflict during feature-finish](#merge-conflict-during-feature-finish)
    - [ike:init on a Syncthing directory](#ikeinit-on-a-syncthing-directory)
    - [Plugin not found: ike:* goals fail](#plugin-not-found-ike-goals-fail)
    - [Build warnings about BOM imports](#build-warnings-about-bom-imports)

This guide walks you through setting up an IKE workspace and working on tinkar/komet components day to day. For conventions and architecture rationale, see the [Workspace Conventions](workspace-conventions.html)[1] reference.

## [Prerequisites](#prerequisites)

Java 25  

Download from [https://jdk.java.net/25/](https://jdk.java.net/25/)[2]. The workspace builds with `--enable-preview` across all modules.

Maven 4.0.0-rc-5 or later  

Download from [https://maven.apache.org/download.cgi](https://maven.apache.org/download.cgi)[3]. All POMs use model version `4.1.0`.

Git  

Any recent version. SSH access to `github.com/ikmdev` and `github.com/IKE-Community` orgs.

Maven settings — `network.ike` plugin group  

Add to `~/.m2/settings.xml` so that `ike:` prefix goals resolve:

```
<settings>
  <pluginGroups>
    <pluginGroup>network.ike</pluginGroup>
  </pluginGroups>
</settings>
```

## [First-Time Workspace Setup](#first-time-workspace-setup)

### [Clone the workspace repository](#clone-the-workspace-repository)

```
git clone git@github.com:IKE-Community/ike-workspace.git
cd ike-workspace
```

The workspace repo contains `pom.xml`, `workspace.yaml`, and file-activated profiles for every component.

### [Initialize components](#initialize-components)

Clone all components declared in `workspace.yaml`:

```
mvn ike:init
```

This clones every component into a subdirectory matching its `name` field in the manifest. Each clone lands on the branch declared in `workspace.yaml` (default: `main`).

For a smaller initial checkout, use a group:

```
mvn ike:init -Dgroup=core
```

This clones only `ike-pipeline` and `tinkar-core` — enough to build the foundation and start working.

### [Open in IntelliJ](#open-in-intellij)

Open the workspace `pom.xml` as a project. File-activated profiles automatically include only the components you have checked out. Missing components are silently skipped — no red underlines, no broken reactor.

## [Daily Workflow](#daily-workflow)

### [Sync all repositories](#sync-all-repositories)

```
mvn ike:pull
```

Runs `git pull --rebase` in every checked-out component.

### [Check status](#check-status)

```
mvn ike:status
```

Shows branch, dirty/clean state, and branch mismatch detection across all checked-out repos.

### [Full overview](#full-overview)

```
mvn ike:dashboard
```

Composite goal: runs `ike:verify` (manifest consistency), `ike:status` (git state), and cascade analysis for dirty components.

## [Starting a Feature](#starting-a-feature)

### [Create the feature branch](#create-the-feature-branch)

```
mvn ike:feature-start -Dfeature=my-feature
```

This creates a `feature/my-feature` branch in every checked-out component and sets branch-qualified POM versions (e.g., `24-my-feature-SNAPSHOT`).

To scope the feature to a group:

```
mvn ike:feature-start -Dfeature=my-feature -Dgroup=core
```

### [Work and commit](#work-and-commit)

Work across repos, commit normally with `git add` / `git commit`. Branch-qualified versions are already set — no manual POM edits needed.

### [Save a checkpoint](#save-a-checkpoint)

```
mvn ike:ws-checkpoint -Dname=progress
```

Records SHAs, versions, and dirty flags for every component into `checkpoints/checkpoint-progress.yaml`. Useful before risky operations or as a team-visible progress marker.

### [Preview the merge](#preview-the-merge)

```
mvn ike:feature-finish -Dfeature=my-feature -DdryRun=true
```

Shows what would happen: which branches merge, version changes, tag names.

### [Finish the feature](#finish-the-feature)

```
mvn ike:feature-finish -Dfeature=my-feature -Dpush=true
```

Merges `feature/my-feature` to `main` with `--no-ff` in every affected component, strips the branch qualifier from POM versions, tags the merge commit, and pushes to origin.

## [Releasing](#releasing)

### [Preview the release plan](#preview-the-release-plan)

```
mvn ike:ws-release -DdryRun=true
```

Output shows which components are dirty, their version transitions, and the topological release order:

```
[INFO] === Workspace Release Plan (DRY RUN) ===
[INFO] Dirty components (topo order):
[INFO]   1. ike-pipeline       24-SNAPSHOT → 24 → 25-SNAPSHOT
[INFO]   2. tinkar-core         1.80.0-SNAPSHOT → 1.80.0 → 1.81.0-SNAPSHOT
[INFO] Cross-reference updates:
[INFO]   tinkar-core: ike-pipeline parent 24-SNAPSHOT → 24
[INFO] === No changes made (dry run) ===
```

### [Execute the release](#execute-the-release)

```
mvn ike:ws-release -Dpush=true
```

For each dirty component in dependency order:

1. Strips `-SNAPSHOT` from the version
2. Builds and verifies
3. Tags the release commit
4. Pushes to origin
5. Bumps to next SNAPSHOT version
6. Updates cross-references in downstream POMs

A pre-release checkpoint is created automatically.

## [Multi-Machine Development (Syncthing)](#multi-machine-development-syncthing)

Syncthing keeps working trees in sync between machines. Git state, build output, and IDE config are per-machine.

### [Generate ignore patterns](#generate-ignore-patterns)

```
mvn ike:stignore
```

Writes `.stignore` files that exclude `target/`, `.git/`, `.idea/`, `.DS_Store`, `.claude/worktrees/`, and `.mvn/local-repo/`.

### [Resume on another machine](#resume-on-another-machine)

Walk away from machine A. Syncthing propagates source files to machine B. On machine B:

```
cd ike-workspace
mvn ike:pull
```

This syncs Git history for all components. `ike:init` is Syncthing-aware: if a directory already exists (synced files, but no `.git`), it runs `git init` + `git reset` instead of `git clone`.

## [Troubleshooting](#troubleshooting)

### [ws-release fails mid-cascade](#ws-release-fails-mid-cascade)

The pre-release checkpoint file records the state of each component before the release started. Re-running `mvn ike:ws-release` skips components that were already tagged and released — it picks up where it left off.

### [Merge conflict during feature-finish](#merge-conflict-during-feature-finish)

Resolve the conflict manually in the affected repository, commit the merge resolution, then re-run:

```
mvn ike:feature-finish -Dfeature=my-feature -Dpush=true
```

The goal detects already-merged components and skips them.

### [ike:init on a Syncthing directory](#ikeinit-on-a-syncthing-directory)

Handled automatically. When a component directory already exists but has no `.git` directory, `ike:init` runs `git init` followed by `git reset` to the manifest branch instead of cloning.

### [Plugin not found: ike:* goals fail](#plugin-not-found-ike-goals-fail)

Verify that `~/.m2/settings.xml` contains `network.ike` in `<pluginGroups>`:

```
<pluginGroups>
  <pluginGroup>network.ike</pluginGroup>
</pluginGroups>
```

Also confirm that `ike-maven-plugin` is declared in the workspace `pom.xml`.

### [Build warnings about BOM imports](#build-warnings-about-bom-imports)

There should be zero warnings about BOM imports. If you see them, check for stale `ike-bom` references in dependent POMs. The BOM is auto-generated from `ike-parent` — manual version references can drift after a release.
