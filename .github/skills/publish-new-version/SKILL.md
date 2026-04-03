---

name: publish-new-version
description: Automates Maven single-module version bumping following SemVer (major, minor, patch, or custom). Updates the root pom.xml, validates with Maven, and guides through git commit/tag/push workflow. Use when releasing a new version, bumping version numbers, preparing Maven releases, or publishing to Maven Central.
license: Apache-2.0
compatibility: Requires Maven wrapper (./mvnw), git, bash, and a Maven project with a root pom.xml file
metadata:
author: auspis
version: 0.1.0
--------------

# Publish New Version

Automates the complete workflow for publishing a new version of a Maven single-module project following Semantic Versioning (SemVer) conventions.

## Overview

This skill handles the entire version release process for Maven projects:

1. **Detects current version** from root `pom.xml` using Maven's help:evaluate
2. **Prompts for version bump type**: major, minor (default), patch, or custom
3. **Updates root pom.xml** with the target version
4. **Validates** the updated project with `mvn validate`
5. **Generates release notes automatically**: English Markdown notes based on commits since previous tag
6. **Shows a single final approval prompt** after pom + release notes are both ready
7. **Publishes GitHub release conditionally**: auto-publishes with `gh` if installed and authenticated
8. **Includes safety checks**: warns if not on main/master, allows rollback on validation failure

## When to Use This Skill

Use this skill when you need to:

- Release a new version of your Maven project
- Bump the root project version consistently
- Follow SemVer conventions (major.minor.patch)
- Prepare for publishing to Maven Central or other repositories
- Ensure version consistency for single-module Maven projects
- Automate the release workflow (version → validate → commit → tag → push)

## Prerequisites

Before using this skill, ensure:

- **Maven wrapper** (`./mvnw`) exists and is executable in the workspace root
- **Git repository** is initialized and you have commits
- You are on the correct branch (preferably `main` or `master`)
- You have reviewed and finalized all changes for the release

## How It Works

### 1. Version Detection

The skill uses Maven's `help:evaluate` plugin to extract the current version from the root `pom.xml`:

```bash
./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout
```

This ensures accurate version detection from the root project.

### 2. Version Bump Types

Choose from four bump types following Semantic Versioning:

|    Type    | Description                         | Example (from 1.2.3) |
|:----------:|:------------------------------------|:--------------------:|
| **minor**  | Default. Adds new features          |        1.3.0         |
| **major**  | Breaking changes, major release     |        2.0.0         |
| **patch**  | Bug fixes, patches                  |        1.2.4         |
| **custom** | Specify exact version (e.g., 2.5.0) |        2.5.0         |

### 3. Update Process

The skill:

- Updates the root `pom.xml` version using `sed`
- Validates the project with `./mvnw validate`
- **Rolls back changes** if validation fails

### 4. Git Workflow

After successful validation and release-notes generation, the skill prompts once to:

1. **Review changes**: Check with `git status` or `git diff` (root `pom.xml` + release notes)
2. **Stage all files**: `git add .`
3. **Commit**: `git commit -m "Bump version to X.Y.Z"`
4. **Tag**: `git tag vX.Y.Z`
5. **Push**: `git push && git push --tags`

You can cancel at any step to perform manual operations.

### 5. Automatic Release Notes

Before any release commit/tag/push, the script automatically generates English Markdown release notes:

- Uses commit range from previous `v*` tag to current release tag
- Excludes merge commits
- Excludes technical commit `Bump version to X.Y.Z`
- Falls back to last 20 commits if no previous tag exists
- Saves notes to `data/release-notes/RELEASE_NOTES_vX.Y.Z.md`
- Prints notes in terminal before final confirmation
- Includes the notes file in the same release commit (`git add .`)

### 6. Optional GitHub Release Publish

As a final step:

- If `gh` is installed and authenticated, the script runs `gh release create`
- If `gh` is missing or not authenticated, the script prints the exact manual command to run

## Safety Features

- ⚠️ **Branch warning**: Alerts if you're not on `main` or `master`
- ✅ **Maven validation**: Runs `./mvnw validate` to check project structure and POM consistency after version changes
- 🔄 **Automatic rollback**: Restores pom.xml files if validation fails using `git restore`
- 🛑 **Single final approval**: Requires explicit approval after both pom update and release-notes preview
- 📋 **Review step**: Allows manual inspection before committing (pom + notes)
- 🧾 **Release notes artifact**: Always generated and saved under `data/release-notes/`
- 🐙 **Graceful GitHub publish fallback**: If `gh` is unavailable, user still gets a ready command

## Usage Example

When an agent invokes this skill:

```
Agent: "I want to publish a new minor version"

Skill Output:
> Current version: 1.2.3
> 
> Select version bump type:
>   1) minor (default)
>   2) major
>   3) patch
>   4) custom
> Enter choice [1]: 1
>
> New version will be: 1.3.0
>
> Proceed to update root pom.xml to version 1.3.0? (y/N): y
>
> Updating root pom.xml...
>   Updated: ./pom.xml
>
> Validating Maven project...
> Maven validation succeeded.
>
> Generated release notes (English Markdown):
>
> # Release Notes - v1.3.0
>
> Release date: 2026-03-14
>
> ## Highlights
>
> - `a1b2c3d` - feat: add support for foo
>
> ## Included Commits
>
> - `a1b2c3d` - feat: add support for foo
> - `d4e5f6g` - fix: correct bar rendering
>
> ## Full Changelog
>
> - https://github.com/auspis/fluent-repo-4j/compare/v1.2.3...v1.3.0
>
> Release notes saved to: data/release-notes/RELEASE_NOTES_v1.3.0.md
>
> Please review the changes now (e.g. git status, git diff).
> This includes root pom.xml and release notes.
> Proceed with git add/commit/tag/push? (y/N): y
>
> Publishing GitHub release with gh...
> GitHub release published for v1.3.0.
>
> Done, published version 1.3.0. Please verify the release on GitHub.
```

## Error Handling

If validation fails:

```
Maven validation failed. Please check for version mismatches or errors.
Restoring pom.xml changes...
Restored ./pom.xml using git restore.
...
```

All changes are automatically reverted to prevent broken state.

If `gh` is not available or not authenticated:

```
GitHub CLI (gh) not found. You can publish manually with:
  gh release create v1.3.0 --title "v1.3.0" --notes-file "data/release-notes/RELEASE_NOTES_v1.3.0.md"
```

## Script Location

The implementation is in [`scripts/publish-new-version.sh`](scripts/publish-new-version.sh).

## Next Steps After Publishing

After successful version publishing:

1. **Review committed notes**: Check `data/release-notes/RELEASE_NOTES_vX.Y.Z.md` in the release commit/tag
2. **Confirm GitHub release**: If `gh` succeeded, verify the created release page
3. **Deploy to Maven Central**: If configured, deploy artifacts (see repository docs)
4. **Update documentation**: Changelog, README badges, etc.
5. **Announce release**: Notify users, update release channels

## Troubleshooting

**Problem**: "Maven wrapper (./mvnw) not found or not executable"
- **Solution**: Ensure you're in the workspace root and `./mvnw` exists with execute permissions

**Problem**: "Maven validation failed"
- **Solution**: Check for version mismatches in dependency declarations or parent POM references

**Problem**: "You are on branch 'feature-xyz'"
- **Solution**: Switch to `main` or `master` branch before releasing, or proceed with caution

**Problem**: Git push fails (authentication)
- **Solution**: The skill doesn't handle git push failures automatically. Configure git credentials or SSH keys before running

**Problem**: `gh` command not found
- **Solution**: Install GitHub CLI or run the printed manual command from a machine where `gh` is available

**Problem**: `gh` is installed but release publish failed
- **Solution**: Run `gh auth login` and retry with the printed `gh release create ... --notes-file ...` command

## Related Documentation

For more details on the project release process, see:
- [`data/wiki/PUBLISH_TO_MAVEN_CENTRAL.md`](../../../data/wiki/PUBLISH_TO_MAVEN_CENTRAL.md) - Publishing workflow
