#!/bin/sh
# ----------------------------------------------------------------------------
# Lightweight Maven Wrapper (only-script). Downloads the Maven version declared in
# .mvn/wrapper/maven-wrapper.properties into ~/.m2/wrapper/dists and runs it.
# To replace it with the official Apache wrapper:  mvn -N wrapper:wrapper -Dtype=only-script
# ----------------------------------------------------------------------------
set -e

BASEDIR=$(cd "$(dirname "$0")" && pwd)
PROPS="$BASEDIR/.mvn/wrapper/maven-wrapper.properties"
[ -f "$PROPS" ] || { echo "Missing $PROPS" >&2; exit 1; }

URL=$(sed -n 's/^distributionUrl=//p' "$PROPS" | tr -d '\r')
NAME=$(basename "$URL" | sed 's/-bin\.zip$//')
DIST_HOME="${MAVEN_USER_HOME:-$HOME/.m2}/wrapper/dists/$NAME"

if [ ! -x "$DIST_HOME/bin/mvn" ]; then
  echo "Downloading $URL" >&2
  TMP="$DIST_HOME.tmp.$$"
  mkdir -p "$TMP"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$URL" -o "$TMP/maven.zip"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$URL" -O "$TMP/maven.zip"
  else
    echo "curl or wget is required" >&2; exit 1
  fi
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$TMP/maven.zip" -d "$TMP"
  else
    (cd "$TMP" && jar xf maven.zip)
  fi
  mkdir -p "$(dirname "$DIST_HOME")"
  rm -rf "$DIST_HOME"
  mv "$TMP/$NAME" "$DIST_HOME"
  rm -rf "$TMP"
  chmod +x "$DIST_HOME/bin/mvn"
fi

export MAVEN_PROJECTBASEDIR="$BASEDIR"
exec "$DIST_HOME/bin/mvn" "$@"
