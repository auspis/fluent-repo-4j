#!/usr/bin/env bash

# Skill: Publish New Version
# Updates root pom.xml with a new version (SemVer), validates with Maven,
# and suggests next steps (commit, tag, push).
# English only. Supports major, minor (default), patch bumps, and custom versions.

set -e

WORKSPACE_ROOT="${1:-.}"
cd "$WORKSPACE_ROOT" || exit 1

# Colors for output
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

RELEASE_NOTES_DIR="data/release-notes"

# Parse version string and return major.minor.patch
parse_version() {
  local version="$1"
  if ! [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "ERROR: Invalid version format: $version" >&2
    exit 1
  fi
  echo "$version"
}

# Extract current version from root pom.xml using Maven's help:evaluate
get_current_version() {
  local root_pom="$1"
  if [[ ! -f "$root_pom" ]]; then
    echo "ERROR: Root pom.xml not found at $root_pom" >&2
    exit 1
  fi

  if [[ ! -x "./mvnw" ]]; then
    echo "ERROR: Maven wrapper (./mvnw) not found or not executable in workspace root" >&2
    exit 1
  fi

  local version
  version=$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null || true)

  if [[ -z "$version" ]]; then
    echo "ERROR: Unable to determine project.version from Maven" >&2
    exit 1
  fi

  echo "$version"
}

# Compute next version based on bump type or custom override
compute_next_version() {
  local current="$1"
  local bump="$2"
  local custom="$3"

  if [[ -n "$custom" ]]; then
    parse_version "$custom"
    return 0
  fi

  local major minor patch
  major=$(echo "$current" | cut -d. -f1)
  minor=$(echo "$current" | cut -d. -f2)
  patch=$(echo "$current" | cut -d. -f3)

  case "$bump" in
    major)
      echo "$((major + 1)).0.0"
      ;;
    minor)
      echo "$major.$((minor + 1)).0"
      ;;
    patch)
      echo "$major.$minor.$((patch + 1))"
      ;;
    *)
      echo "ERROR: Unknown bump type: $bump" >&2
      exit 1
      ;;
  esac
}

# Update <version>...</version> in a pom.xml file
update_pom_version() {
  local pom_file="$1"
  local new_version="$2"
  sed -E -i.bak "s/<version>[0-9]+\.[0-9]+\.[0-9]+<\/version>/<version>${new_version}<\/version>/" "$pom_file"
  rm -f "$pom_file.bak"
}

# Get current git branch
get_current_branch() {
  git rev-parse --abbrev-ref HEAD 2>/dev/null || echo ""
}

# Validate Maven project
validate_maven() {
  ./mvnw validate -q
}

restore_pom_files() {
  local pom_list="$1"

  if [[ -z "$pom_list" ]]; then
    echo -e "${YELLOW}No pom.xml files to restore.${NC}"
    return 0
  fi

  local restore_failed=0

  while IFS= read -r pom; do
    # Skip empty lines
    if [[ -z "$pom" ]]; then
      continue
    fi

    if git restore -q -- "$pom" 2>/dev/null; then
      echo -e "${GREEN}Restored ${pom} using git restore.${NC}"
    else
      echo -e "${YELLOW}git restore failed for ${pom}, falling back to git checkout...${NC}"
      if git checkout -- "$pom"; then
        echo -e "${GREEN}Restored ${pom} using git checkout.${NC}"
      else
        echo -e "${RED}Failed to restore ${pom}.${NC}" >&2
        restore_failed=1
      fi
    fi
  done <<< "$pom_list"

  return "$restore_failed"
}

run_git_steps() {
  local new_version="$1"
  local tag="v$new_version"
  git add .
  git commit -m "Bump version to $new_version"
  git tag "$tag"
  git push && git push --tags
}

get_previous_release_tag() {
  local described_tag

  # Prefer the most recent reachable v* tag from HEAD
  if described_tag=$(git describe --tags --match 'v*' --abbrev=0 2>/dev/null); then
    echo "$described_tag"
    return 0
  fi

  # Fallback: highest v* tag that is merged into HEAD (if any)
  git tag -l 'v*' --merged HEAD --sort=-version:refname | head -n 1
}

build_compare_url() {
  local previous_tag="$1"
  local new_tag="$2"
  local remote_url
  remote_url=$(git config --get remote.origin.url 2>/dev/null || true)

  if [[ -z "$remote_url" || -z "$previous_tag" ]]; then
    echo ""
    return 0
  fi

  local owner_repo=""
  # Support common GitHub remote URL formats, with or without `.git`:
  # - git@github.com:owner/repo[.git]
  # - ssh://git@github.com/owner/repo[.git]
  # - https://github.com/owner/repo[.git]
  # - git://github.com/owner/repo[.git]
  if [[ "$remote_url" =~ ^git@github.com:([^/]+/[^/]+)(\.git)?$ ]]; then
    owner_repo="${BASH_REMATCH[1]}"
  elif [[ "$remote_url" =~ ^ssh://git@github.com/([^/]+/[^/]+)(\.git)?$ ]]; then
    owner_repo="${BASH_REMATCH[1]}"
  elif [[ "$remote_url" =~ ^https://github.com/([^/]+/[^/]+)(\.git)?$ ]]; then
    owner_repo="${BASH_REMATCH[1]}"
  elif [[ "$remote_url" =~ ^git://github.com/([^/]+/[^/]+)(\.git)?$ ]]; then
    owner_repo="${BASH_REMATCH[1]}"
  fi

  if [[ -z "$owner_repo" ]]; then
    echo ""
    return 0
  fi

  echo "https://github.com/${owner_repo}/compare/${previous_tag}...${new_tag}"
}

generate_release_notes() {
  local previous_tag="$1"
  local new_version="$2"
  local end_ref="${3:-HEAD}"
  local new_tag="v${new_version}"

  local commit_args=()
  if [[ -n "$previous_tag" ]]; then
    commit_args+=("${previous_tag}..${end_ref}")
  else
    commit_args+=("-n" "20" "${end_ref}")
  fi

  local release_date
  release_date=$(date '+%Y-%m-%d')
  local compare_url
  compare_url=$(build_compare_url "$previous_tag" "$new_tag")

  local commits
  commits=$(git log --no-merges --pretty=format:'%h%x09%s' "${commit_args[@]}" | awk -F '\t' -v bump="Bump version to ${new_version}" '$2 != bump { printf("- `%s` - %s\n", $1, $2) }')

  {
    echo "# Release Notes - ${new_tag}"
    echo ""
    echo "Release date: ${release_date}"
    echo ""
    echo "## Highlights"
    echo ""
    if [[ -n "$commits" ]]; then
      echo "$commits" | head -n 3
    else
      echo "- No functional changes found in this release window."
    fi
    echo ""
    echo "## Included Commits"
    echo ""
    if [[ -n "$commits" ]]; then
      echo "$commits"
    else
      echo "- No commits to display."
    fi
    echo ""
    echo "## Full Changelog"
    echo ""
    if [[ -n "$compare_url" ]]; then
      echo "- ${compare_url}"
    else
      echo "- GitHub compare URL not available (missing previous tag or unrecognized remote URL)."
    fi
  }
}

save_release_notes() {
  local new_version="$1"
  local content="$2"
  mkdir -p "$RELEASE_NOTES_DIR"
  local output_file="${RELEASE_NOTES_DIR}/RELEASE_NOTES_v${new_version}.md"
  printf '%s\n' "$content" > "$output_file"
  echo "$output_file"
}

publish_release_with_gh_if_available() {
  local new_version="$1"
  local notes_file="$2"
  local tag="v${new_version}"

  if ! command -v gh >/dev/null 2>&1; then
    echo -e "${YELLOW}GitHub CLI (gh) not found. You can publish manually with:${NC}"
    echo "  gh release create ${tag} --title \"${tag}\" --notes-file \"${notes_file}\""
    return 0
  fi

  if ! gh auth status >/dev/null 2>&1; then
    echo -e "${YELLOW}GitHub CLI is installed but not authenticated.${NC}"
    echo "Run: gh auth login"
    echo "Then publish with: gh release create ${tag} --title \"${tag}\" --notes-file \"${notes_file}\""
    return 0
  fi

  echo ""
  echo "Publishing GitHub release with gh..."
  if gh release create "$tag" --title "$tag" --notes-file "$notes_file"; then
    echo -e "${GREEN}GitHub release published for ${tag}.${NC}"
  else
    echo -e "${YELLOW}Failed to publish GitHub release automatically.${NC}"
    echo "You can retry manually with: gh release create ${tag} --title \"${tag}\" --notes-file \"${notes_file}\""
  fi
}

# Main logic
main() {
  local root_pom="pom.xml"
  local current_version
  current_version=$(get_current_version "$root_pom")

  echo "Current version: $current_version"
  echo ""
  echo "Select version bump type:"
  echo "  1) minor (default)"
  echo "  2) major"
  echo "  3) patch"
  echo "  4) custom"
  read -p "Enter choice [1]: " choice
  choice=${choice:-1}

  local bump="minor"
  local custom=""

  case "$choice" in
    1) bump="minor" ;;
    2) bump="major" ;;
    3) bump="patch" ;;
    4)
      read -p "Enter custom version (e.g. 2.0.0): " custom
      custom=$(parse_version "$custom")
      ;;
    *)
      echo -e "${RED}Invalid choice. Aborting.${NC}"
      exit 1
      ;;
  esac

  local new_version
  new_version=$(compute_next_version "$current_version" "$bump" "$custom")
  local previous_tag
  previous_tag=$(get_previous_release_tag)

  echo ""
  echo "New version will be: $new_version"
  echo ""

  # Warn if not on main/master
  local branch
  branch=$(get_current_branch)
  if [[ "$branch" != "main" && "$branch" != "master" ]]; then
    echo -e "${YELLOW}WARNING: You are on branch '$branch'. It is recommended to run this skill on 'main' or 'master'.${NC}"
    echo ""
  fi

  # Confirm
  read -p "Proceed to update root pom.xml to version $new_version? (y/N): " confirm
  if ! [[ "$confirm" =~ ^[Yy]([Ee][Ss])?$ ]]; then
    echo "Aborted by user."
    exit 0
  fi

  # Update root pom.xml
  echo ""
  echo "Updating root pom.xml..."
  local pom_files
  pom_files="$root_pom"
  while IFS= read -r pom; do
    if [[ -z "$pom" ]]; then
      continue
    fi
    update_pom_version "$pom" "$new_version"
    echo "  Updated: $pom"
  done <<< "$pom_files"

  # Validate Maven
  echo ""
  echo "Validating Maven project..."
  if ! validate_maven; then
    echo -e "${RED}Maven validation failed. Please check for version mismatches or errors.${NC}"
    echo "Restoring pom.xml changes..."
    restore_pom_files "$pom_files"
    exit 1
  fi
  echo -e "${GREEN}Maven validation succeeded.${NC}"

  local release_notes
  release_notes=$(generate_release_notes "$previous_tag" "$new_version" "HEAD")
  local release_notes_file
  release_notes_file=$(save_release_notes "$new_version" "$release_notes")

  echo ""
  echo "Generated release notes (English Markdown):"
  echo ""
  echo "$release_notes"
  echo ""
  echo "Release notes saved to: $release_notes_file"

  echo ""
  echo "Please review the changes now (e.g. git status, git diff)."
  echo "This includes root pom.xml and release notes."
  read -p "Proceed with git add/commit/tag/push? (y/N): " proceed
  if ! [[ "$proceed" =~ ^[Yy]([Ee][Ss])?$ ]]; then
    echo "Cancelled by user. Version and release notes changes preserved."
    echo "You can commit them manually or run 'git restore -- pom.xml' and 'rm -f ${release_notes_file}' to revert."
    exit 0
  fi

  run_git_steps "$new_version"

  publish_release_with_gh_if_available "$new_version" "$release_notes_file"

  echo ""
  echo "Done, published version $new_version. Please verify the release on GitHub: https://github.com/auspis/fluent-repo-4j/actions"
}

main "$@"
