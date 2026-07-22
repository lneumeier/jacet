#!/usr/bin/env bash
#
# Jacet installer for Linux and macOS.
#
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/lneumeier/jacet/main/install.sh | bash
#   curl -fsSL https://raw.githubusercontent.com/lneumeier/jacet/main/install.sh | bash -s -- v0.1.0
#
# Environment variables:
#   JACET_INSTALL_DIR  Install directory (default: $HOME/.jacet/bin)

set -euo pipefail

REPO="lneumeier/jacet"
INSTALL_DIR="${JACET_INSTALL_DIR:-$HOME/.jacet/bin}"
VERSION="${1:-latest}"

err() { echo "error: $*" >&2; exit 1; }

case "$(uname -s)" in
  Linux*)  os=linux ;;
  Darwin*) os=macos ;;
  *)       err "unsupported OS: $(uname -s)" ;;
esac

case "$(uname -m)" in
  x86_64|amd64)  arch=amd64 ;;
  arm64|aarch64) arch=arm64 ;;
  *)             err "unsupported architecture: $(uname -m)" ;;
esac

# Intel macOS binaries are no longer published.
[ "${os}-${arch}" != "macos-amd64" ] || err "unsupported platform: macOS x86_64 (Intel)"

asset="jacet-${os}-${arch}"

if [ "$VERSION" = "latest" ]; then
  # Anchor to the "tag_name" key shape so unrelated JSON fields can't match;
  # leave the tag value itself permissive (starts with 'v') so non-semver
  # conventions still resolve.
  release_json=$(curl -fsSL "https://api.github.com/repos/${REPO}/releases/latest") \
    || err "failed to query GitHub API for latest release"
  VERSION=$(printf '%s' "$release_json" \
    | grep -m1 -oE '"tag_name":[[:space:]]*"v[^"]+"' \
    | sed -E 's/.*"(v[^"]+)".*/\1/')
  [ -n "$VERSION" ] || err "failed to parse tag_name from latest release"
fi

# Defense-in-depth: only accept tag shapes we produce.
case "$VERSION" in
  v[0-9]*) ;;
  *) err "unexpected release tag shape: $VERSION" ;;
esac

base="https://github.com/${REPO}/releases/download/${VERSION}"

echo "Installing jacet ${VERSION} (${os}-${arch}) to ${INSTALL_DIR}"
mkdir -p "$INSTALL_DIR"

# Temp file inside $INSTALL_DIR so the final `mv` is a same-volume rename (atomic)
# rather than a cross-filesystem copy that can leave a half-written binary on crash.
tmp=$(mktemp "${INSTALL_DIR}/.jacet.XXXXXXXX") \
  || err "failed to create temp file in ${INSTALL_DIR}"
trap 'rm -f "$tmp"' EXIT

curl -fSL --progress-bar -o "$tmp" "${base}/${asset}" \
  || err "download failed: ${base}/${asset}"

# Split curl and awk: piping hides curl's failure under pipefail with no context.
checksum_raw=$(curl -fsSL "${base}/${asset}.sha256") \
  || err "failed to fetch checksum: ${base}/${asset}.sha256"
expected=$(printf '%s' "$checksum_raw" | awk '{print $1}')
[ -n "$expected" ] || err "empty checksum file"

if command -v sha256sum >/dev/null; then
  actual=$(sha256sum "$tmp" | awk '{print $1}')
else
  actual=$(shasum -a 256 "$tmp" | awk '{print $1}')
fi

[ "$expected" = "$actual" ] \
  || err "checksum mismatch: expected $expected, got $actual"

chmod +x "$tmp"
mv "$tmp" "$INSTALL_DIR/jacet"
trap - EXIT

echo "Installed: $INSTALL_DIR/jacet"

case ":$PATH:" in
  *":$INSTALL_DIR:"*) ;;
  *)
    echo
    echo "Note: $INSTALL_DIR is not in your PATH."
    echo "Add this line to your shell profile (~/.bashrc, ~/.zshrc, ~/.profile):"
    echo "  export PATH=\"$INSTALL_DIR:\$PATH\""
    ;;
esac
