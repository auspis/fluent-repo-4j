**Publish to Maven Central**

As of June 30, 2025, OSSRH (Sonatype Open Source Repository Hosting) has reached end-of-life. This project publishes via the **Central Publishing Portal**: https://central.sonatype.com/.

## Setup

### 1. Generate a Central Portal User Token

1. Log in to https://central.sonatype.com/
2. Go to View Account -> Generate User Token
3. Save token username and password

### 2. Set Repository Secrets in GitHub

Set these secrets in Settings -> Secrets and variables -> Actions:

- `CENTRAL_TOKEN_USERNAME` - user token username
- `CENTRAL_TOKEN_PASSWORD` - user token password
- `GPG_PRIVATE_KEY` (ASCII-armored private key)
- `GPG_PASSPHRASE`

### 3. Generate and Upload GPG Secrets

Use the helper script in this repository:

```bash
grep -qxF "allow-loopback-pinentry" ~/.gnupg/gpg-agent.conf || echo "allow-loopback-pinentry" >> ~/.gnupg/gpg-agent.conf && gpgconf --kill gpg-agent && sleep 1
./data/scripts/generate-gpg-secrets.sh --email ci@t-lab.lan --name "fluent-repo-4j CI" --comment "fluent-repo-4j-ci" --repo auspis/fluent-repo-4j --set-secrets --length 4096
```

## Release Workflow

### Default Behavior: Manual Publishing (Conservative)

By default, the release workflow uploads and validates artifacts without automatically publishing them.

**Option 1: Push a tag (automatic trigger, manual review)**

```bash
git tag v1.0.0
git push origin v1.0.0
```

The workflow will:
1. Build release artifacts
2. Sign them with GPG
3. Upload to Central Publishing Portal
4. Validate the bundle
5. Stop for manual review and publish in the Central Portal UI

### Manual workflow dispatch

**Option 2: Manual dispatch with manual review**

GitHub -> Actions -> Release to Maven Central -> Run workflow:
- releaseTag: vX.Y.Z
- autoPublish: false

Result: upload + validation, then manual publish in Central Portal UI.

**Option 3: Manual dispatch with auto-publish**

GitHub -> Actions -> Release to Maven Central -> Run workflow:
- releaseTag: vX.Y.Z
- autoPublish: true

Result: upload + validation + automatic publish to Maven Central.

## First Release Checklist

1. Confirm `pom.xml` version matches release tag (e.g., `1.0.0` <-> `v1.0.0`)
2. Ensure all four required secrets are present
3. Trigger release via tag or manual dispatch
4. If `autoPublish=false`, publish from https://central.sonatype.com/publishing/deployments
5. Verify artifact is resolvable:

```bash
mvn dependency:get -Dartifact=io.github.auspis:fluent-repo-4j:1.0.0
```

## Notes

- Release workflow file: `.github/workflows/release.yml`
- Release profile in Maven: `-P release`
- Upload endpoint is configured under `distributionManagement` with server id `central`
- `MAVEN_GPG_PASSPHRASE` is injected by the workflow and used by `maven-gpg-plugin`

