#!/bin/sh
# Seeds Floci (local AWS emulator) with the resources the app expects.
# Runs inside the amazon/aws-cli container (see docker-compose.yml). Idempotent.
set -eu

SECRET_ID="${APP_SECRET_ID:-bedrock-mcp-springboot/local/app}"

echo "Waiting for Floci at ${AWS_ENDPOINT_URL} ..."
i=0
until aws secretsmanager list-secrets >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then
    echo "Floci did not become ready in time" >&2
    exit 1
  fi
  sleep 1
done

if aws secretsmanager describe-secret --secret-id "$SECRET_ID" >/dev/null 2>&1; then
  echo "Secret $SECRET_ID already exists"
else
  aws secretsmanager create-secret \
    --name "$SECRET_ID" \
    --description "Local application secrets for bedrock-mcp-springboot" \
    --secret-string '{"backend-api-key":"local-dev-api-key"}' >/dev/null
  echo "Created secret $SECRET_ID"
fi

# Example of a non-secret parameter (SSM Parameter Store) for feature flags/config
aws ssm put-parameter --name "/bedrock-mcp-springboot/local/feature/create-order-enabled" \
  --type String --value "true" --overwrite >/dev/null 2>&1 || true

echo "Floci init done"
