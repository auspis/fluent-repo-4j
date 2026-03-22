#!/usr/bin/env bash
# generate-gpg-secrets.sh
# Genera una chiave GPG batch per CI, esporta la chiave privata (ASCII-armored)
# e stampa il valore della chiave e la passphrase.
#
# Uso:
#   ./generate-gpg-secrets.sh --name "CI Signing" --email ci@example.com \
#       [--comment "fluent-sql-4j-ci"] [--expiry 1y] [--length 4096] \
#       [--repo owner/repo] [--set-secrets]
#
# Se --repo e --set-secrets sono passati, lo script proverà a impostare secrets
# GPG_PRIVATE_KEY e GPG_PASSPHRASE nel repo (richiede gh CLI autenticato).
#
# #### example ####
# ./generate-gpg-secrets.sh --email ci@t-lab.lan --name "fluent-sql-4j CI" --comment "fluent-sql-4j-ci" --expiry 1y
#
# Per impostare i secrets direttamente su GitHub (richiede gh CLI autenticato e permessi repo:admin): 
# grep -qxF "allow-loopback-pinentry" ~/.gnupg/gpg-agent.conf || echo "allow-loopback-pinentry" >> ~/.gnupg/gpg-agent.conf && gpgconf --kill gpg-agent && sleep 1
# ./data/scripts/generate-gpg-secrets.sh --email ci@t-lab.lan --repo auspis/fluent-sql-4j --set-secrets --length 4096 2>&1 | tee /tmp/gpg-generate.log


set -euo pipefail

# Defaults
NAME="CI Signing"
EMAIL=""
COMMENT="fluent-sql-4j-ci"
EXPIRY="1y"
KEY_LENGTH="4096"
TMPDIR=$(mktemp -d)
GENCONF="$TMPDIR/gpg-gen.conf"
PRIVATE_KEY_FILE="$TMPDIR/private.key"
SET_SECRETS=false
GITHUB_REPO=""

usage() {
  cat <<EOF
Usage: $0 --email you@example.com [options]

Options:
  --name "Name"           : UID name (default: "CI Signing")
  --email "email"         : UID email (required)
  --comment "comment"     : UID comment (default: "fluent-sql-4j-ci")
  --expiry 1y             : key expiry (default: 1y)
  --length 4096           : RSA key length (default: 4096)
  --repo owner/repo       : GitHub repo to set secrets (optional)
  --set-secrets           : set GitHub secrets (requires gh CLI and --repo)
  -h|--help               : show this help
EOF
  exit 1
}

# Parse args
while [[ $# -gt 0 ]]; do
  case "$1" in
    --name) NAME="$2"; shift 2;;
    --email) EMAIL="$2"; shift 2;;
    --comment) COMMENT="$2"; shift 2;;
    --expiry) EXPIRY="$2"; shift 2;;
    --length) KEY_LENGTH="$2"; shift 2;;
    --repo) GITHUB_REPO="$2"; shift 2;;
    --set-secrets) SET_SECRETS=true; shift 1;;
    -h|--help) usage;;
    *) echo "Unknown arg: $1"; usage;;
  esac
done

if [[ -z "$EMAIL" ]]; then
  echo "ERROR: --email is required"
  usage
fi

# Check dependencies
command -v gpg >/dev/null 2>&1 || { echo "gpg not found. Install gnupg."; exit 2; }
command -v openssl >/dev/null 2>&1 || { echo "openssl not found. Install openssl."; exit 2; }
if $SET_SECRETS; then
  command -v gh >/dev/null 2>&1 || { echo "gh CLI not found but --set-secrets requested. Install gh or omit --set-secrets."; exit 2; }
  if [[ -z "$GITHUB_REPO" ]]; then
    echo "--set-secrets requires --repo owner/repo"; exit 2
  fi
fi

# Generate a strong random passphrase
PASSPHRASE=$(openssl rand -base64 32)

# Prepare GPG batch file for key generation
cat > "$GENCONF" <<EOF
%echo Generating a GPG key for CI
Key-Type: RSA
Key-Length: $KEY_LENGTH
Subkey-Type: RSA
Subkey-Length: $KEY_LENGTH
Name-Real: $NAME
Name-Comment: $COMMENT
Name-Email: $EMAIL
Expire-Date: $EXPIRY
Passphrase: $PASSPHRASE
%commit
%echo done
EOF

# Generate key in batch
echo "Generating GPG key (this may take a while)..."
gpg --batch --generate-key "$GENCONF"

# Find fingerprint of generated key (first fpr matching the email)
FP=$(gpg --with-colons --list-secret-keys "$EMAIL" 2>/dev/null | awk -F: '/^fpr:/ {print $10; exit}')
if [[ -z "$FP" ]]; then
  echo "ERROR: could not find generated key fingerprint"; exit 3
fi

# Export private key (ASCII armored) using loopback pinentry (non-interactive)
# Write passphrase to a restricted temp file and use --passphrase-file with
# --pinentry-mode loopback. If that fails we fall back to interactive export.
PASSFILE="$TMPDIR/pass.ph"
printf '%s' "$PASSPHRASE" > "$PASSFILE"
chmod 600 "$PASSFILE"
if ! gpg --batch --pinentry-mode loopback --passphrase-file "$PASSFILE" \
    --armor --export-secret-keys "$FP" > "$PRIVATE_KEY_FILE" 2>/tmp/gpg-export.err; then
  echo "Loopback export failed (see /tmp/gpg-export.err). Trying interactive export..."
  if ! gpg --armor --export-secret-keys "$FP" > "$PRIVATE_KEY_FILE" 2>>/tmp/gpg-export.err; then
    echo "ERROR: failed to export secret key. See /tmp/gpg-export.err for details." >&2
    rm -f "$PASSFILE"
    exit 4
  fi
fi
# Remove passphrase file securely if possible
if command -v shred >/dev/null 2>&1; then
  shred -u "$PASSFILE" 2>/dev/null || rm -f "$PASSFILE"
else
  rm -f "$PASSFILE"
fi
rm -f /tmp/gpg-export.err || true

# Print results (for copying into GitHub secrets)
echo "=============================================="
echo "GPG key generated for: $NAME <$EMAIL>"
echo "Fingerprint: $FP"
echo
echo "----- BEGIN GPG PRIVATE KEY (ASCII-ARMORED) -----"
cat "$PRIVATE_KEY_FILE"
echo "-----  END GPG PRIVATE KEY (ASCII-ARMORED)  -----"
echo
echo "GPG_PASSPHRASE (copy this safely):"
echo "$PASSPHRASE"
echo "=============================================="

# Optionally set GitHub secrets using gh CLI
if $SET_SECRETS; then
  echo "Setting GitHub secrets in repository: $GITHUB_REPO"
  # set private key (read file contents into --body; --body-file is not a valid flag)
  gh secret set GPG_PRIVATE_KEY --repo "$GITHUB_REPO" --body "$(cat "$PRIVATE_KEY_FILE")"
  # set passphrase
  gh secret set GPG_PASSPHRASE --repo "$GITHUB_REPO" --body "$PASSPHRASE"
  echo "Secrets GPG_PRIVATE_KEY and GPG_PASSPHRASE set in $GITHUB_REPO"
fi

# Cleanup (optionally keep ephemeral files for inspection)
# rm -rf "$TMPDIR"
echo "Temporary files are in: $TMPDIR"
echo "If you don't need them, remove them: rm -rf $TMPDIR"
