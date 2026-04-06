---
date_published: 2026-04-05
date_modified: 2026-04-05
canonical_url: https://github.com/IKE-Network/ike-pipeline/release-notes.html
---

# Release Notes

## [ike-tooling v67](#ike-tooling-v67)

### [Internal](#internal)

- Publish Maven sites to GitHub Pages at ike.network ([#60](https://github.com/IKE-Network/ike-issues/issues/60)[1])

## [ike-pipeline v51](#ike-pipeline-v51)

### [Enhancements](#enhancements)

- ws: goals should produce a cumulative markdown report with optional browser open ([#52](https://github.com/IKE-Network/ike-issues/issues/52)[2])

### [Internal](#internal_2)

- Update architecture documentation for workspace plugin split ([#59](https://github.com/IKE-Network/ike-issues/issues/59)[3])
- Workspace POM generation should derive tooling version from ike-parent ([#58](https://github.com/IKE-Network/ike-issues/issues/58)[4])
- Update ike-pipeline ike-tooling.version to v66 ([#57](https://github.com/IKE-Network/ike-issues/issues/57)[5])
- Add parent version alignment to ws:verify and ws:align ([#56](https://github.com/IKE-Network/ike-issues/issues/56)[6])
- Move ike-workspace-maven-plugin to ike-pipeline reactor ([#55](https://github.com/IKE-Network/ike-issues/issues/55)[7])
- Update ike-pipeline to align with ike-tooling v66 and release v51 ([#53](https://github.com/IKE-Network/ike-issues/issues/53)[8])

## [ike-tooling v66](#ike-tooling-v66)

### [Internal](#internal_3)

- Extract ReleaseSupport and ReleaseNotesSupport to ike-workspace-model ([#54](https://github.com/IKE-Network/ike-issues/issues/54)[9])

## [ike-tooling v64](#ike-tooling-v64)

### [Fixes](#fixes)

- Fix release notes 404: generate XHTML for maven-site-plugin ([#39](https://github.com/IKE-Network/ike-issues/issues/39)[10])

### [Enhancements](#enhancements_2)

- Dynamic workspace name in all mojo output headers ([#40](https://github.com/IKE-Network/ike-issues/issues/40)[11])

## [ike-tooling v63](#ike-tooling-v63)

### [Fixes](#fixes_2)

- ws:add: derive version from POM and write to workspace.yaml ([#37](https://github.com/IKE-Network/ike-issues/issues/37)[12])

### [Enhancements](#enhancements_3)

- ws:feature-start: POM fallback when workspace.yaml has no version ([#38](https://github.com/IKE-Network/ike-issues/issues/38)[13])
- Generate full release history on site from all milestones ([#35](https://github.com/IKE-Network/ike-issues/issues/35)[14])

### [Internal](#internal_4)

- Retroactively create milestones for v58-v62 releases ([#36](https://github.com/IKE-Network/ike-issues/issues/36)[15])

## [ike-tooling v62](#ike-tooling-v62)

### [Fixes](#fixes_3)

- ws:feature-start: workspace repo push should be non-fatal when no remote exists ([#33](https://github.com/IKE-Network/ike-issues/issues/33)[16])

### [Enhancements](#enhancements_4)

- Graceful remote handling across all push-capable goals ([#34](https://github.com/IKE-Network/ike-issues/issues/34)[17])

### [Internal](#internal_5)

- Standards: document idempotency as a design principle for workspace goals ([#32](https://github.com/IKE-Network/ike-issues/issues/32)[18])

## [ike-tooling v61](#ike-tooling-v61)

### [Fixes](#fixes_4)

- ws:add: resolve Maven property references in dependency groupId/artifactId ([#31](https://github.com/IKE-Network/ike-issues/issues/31)[19])
- ws:add should reject duplicate component names ([#28](https://github.com/IKE-Network/ike-issues/issues/28)[20])

### [Enhancements](#enhancements_5)

- ws:add: consider shallow clone option for faster workspace setup ([#29](https://github.com/IKE-Network/ike-issues/issues/29)[21])

## [ike-tooling v60](#ike-tooling-v60)

### [Internal](#internal_6)

- PublishedArtifactSet: replace regex POM parsing with javax.xml DOM parser ([#27](https://github.com/IKE-Network/ike-issues/issues/27)[22])

## [ike-tooling v59](#ike-tooling-v59)

### [Fixes](#fixes_5)

- ws:create should use workspace name as POM <name>, not generic default ([#21](https://github.com/IKE-Network/ike-issues/issues/21)[23])

### [Enhancements](#enhancements_6)

- ws:graph should show full transitive dependency tree, not just direct edges ([#24](https://github.com/IKE-Network/ike-issues/issues/24)[24])
- Rename VCS bridge to subproject git state; clarify verify output ([#23](https://github.com/IKE-Network/ike-issues/issues/23)[25])
- ws:add should report detailed dependency artifacts, versions, and alignment status ([#22](https://github.com/IKE-Network/ike-issues/issues/22)[26])

## [ike-tooling v58](#ike-tooling-v58)

### [Fixes](#fixes_6)

- ws:create should warn or fail if workspace directory already exists ([#20](https://github.com/IKE-Network/ike-issues/issues/20)[27])

### [Enhancements](#enhancements_7)

- Use gh CLI for authenticated GitHub API calls instead of raw HttpClient ([#19](https://github.com/IKE-Network/ike-issues/issues/19)[28])
- Integrate release notes into site build ([#18](https://github.com/IKE-Network/ike-issues/issues/18)[29])

## [ike-tooling v57](#ike-tooling-v57)

### [Enhancements](#enhancements_8)

- ws:add: derive depends-on from POM analysis instead of manual -DdependsOn ([#17](https://github.com/IKE-Network/ike-issues/issues/17)[30])
- Implement ws:release-notes goal ([#16](https://github.com/IKE-Network/ike-issues/issues/16)[31])

### [Internal](#internal_7)

- Add issue templates and README to ike-issues ([#15](https://github.com/IKE-Network/ike-issues/issues/15)[32])
- Create IKE-RELEASE.md release standards ([#14](https://github.com/IKE-Network/ike-issues/issues/14)[33])
- Add bootstrap checklist to IKE-WORKSPACE.md prerequisites ([#13](https://github.com/IKE-Network/ike-issues/issues/13)[34])
- ws:create bootstrap: settings.xml requires pluginGroups for ws: prefix ([#12](https://github.com/IKE-Network/ike-issues/issues/12)[35])
