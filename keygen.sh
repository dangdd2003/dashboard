#!/usr/bin/env bash
# Generates the two RSA key pairs used for the nested (signed, then encrypted) JWTs:
#
#   sign-private.pem / sign-public.pem  - signing pair    (dashboard-security signs, dashboard-user verifies)
#   enc-private.pem  / enc-public.pem   - encryption pair (dashboard-security encrypts, dashboard-user decrypts)
#
# dashboard-security only needs sign-private.pem + enc-public.pem,
# dashboard-user only needs sign-public.pem + enc-private.pem.
#
# Existing keys are kept (regenerating them invalidates every issued token).
# Pass --force to regenerate both pairs anyway.
#
# Usage: ./keygen.sh [--force]
# The target directory defaults to ./jwt next to this script and can be overridden with JWT_KEY_DIR.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KEY_DIR="${JWT_KEY_DIR:-$SCRIPT_DIR/jwt}"
KEY_BITS=2048
FORCE=false

for arg in "$@"; do
  case "$arg" in
    --force|-f) FORCE=true ;;
    -h|--help)
      sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg (usage: $0 [--force])" >&2
      exit 1
      ;;
  esac
done

command -v openssl >/dev/null 2>&1 || { echo "openssl is required" >&2; exit 1; }

TMP_FILES=()
cleanup() { [[ ${#TMP_FILES[@]} -eq 0 ]] || rm -f "${TMP_FILES[@]}"; }
trap cleanup EXIT

mkdir -p "$KEY_DIR"
# Only the owner may list/traverse the key directory on the host.
chmod 700 "$KEY_DIR"

# generate_pair <prefix>: writes <prefix>-private.pem (PKCS#8) and <prefix>-public.pem
generate_pair() {
  local prefix="$1"
  local private="$KEY_DIR/$prefix-private.pem"
  local public="$KEY_DIR/$prefix-public.pem"

  if [[ "$FORCE" != true && -s "$private" && -s "$public" ]]; then
    echo "Keeping existing $prefix key pair in $KEY_DIR"
    return
  fi

  local tmp_private tmp_public
  tmp_private="$(mktemp "$KEY_DIR/.$prefix-private.XXXXXX")"
  tmp_public="$(mktemp "$KEY_DIR/.$prefix-public.XXXXXX")"
  TMP_FILES+=("$tmp_private" "$tmp_public")
  # genpkey writes an unencrypted PKCS#8 private key ("BEGIN PRIVATE KEY")
  openssl genpkey -algorithm RSA -pkeyopt "rsa_keygen_bits:$KEY_BITS" -out "$tmp_private" 2>/dev/null
  openssl pkey -in "$tmp_private" -pubout -out "$tmp_public"

  # The containers run as uid 185 and read the keys through single-file read-only bind mounts,
  # so the files must be world-readable; the 0700 directory above keeps them private on the host.
  chmod 644 "$tmp_private" "$tmp_public"
  mv -f "$tmp_private" "$private"
  mv -f "$tmp_public" "$public"
  echo "Generated $prefix key pair in $KEY_DIR"
}

generate_pair sign
generate_pair enc
