#!/usr/bin/env bash
# Builds the image as <artifactId>:<version> read from pom.xml  ->  bedrock-mcp-springboot:0.1.0
# Usage: ./scripts/docker-build.sh [--multiarch] [--push <registry>]
set -euo pipefail
cd "$(dirname "$0")/.."

# First <artifactId>/<version> AFTER the </parent> block = this project's coordinates
NAME=$(awk '/<\/parent>/{p=1;next} p && /<artifactId>/{gsub(/.*<artifactId>|<\/artifactId>.*/,"");print;exit}' pom.xml)
VERSION=$(awk '/<\/parent>/{p=1;next} p && /<version>/{gsub(/.*<version>|<\/version>.*/,"");print;exit}' pom.xml)
IMAGE="${NAME}:${VERSION}"

if [[ "${1:-}" == "--multiarch" ]]; then
  # amd64 for laptops/CI, arm64 for Fargate Graviton (cheaper)
  REGISTRY="${3:-}"
  TARGET="${REGISTRY:+$REGISTRY/}${IMAGE}"
  docker buildx build --platform linux/amd64,linux/arm64 --build-arg APP_VERSION="$VERSION" \
    -t "$TARGET" ${REGISTRY:+--push} .
else
  docker build --build-arg APP_VERSION="$VERSION" -t "$IMAGE" -t "${NAME}:latest" .
fi
echo "Built ${IMAGE}"
