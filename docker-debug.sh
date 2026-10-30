#!/bin/sh
set -eu

cd -- "$(dirname -- "$0")"

IMAGE=agent-orchestrator-mcp:local
CONTAINER=agent-orchestrator-mcp-debug
HOST_PORT=${HOST_PORT:-9999}

if ! docker info >/dev/null 2>&1; then
    echo 'Docker is unavailable. Start Docker and try again.' >&2
    exit 1
fi

# Remove the previous debug container before removing its image.
if docker container inspect "$CONTAINER" >/dev/null 2>&1; then
    docker container rm --force "$CONTAINER"
fi

if docker image inspect "$IMAGE" >/dev/null 2>&1; then
    docker image rm "$IMAGE"
fi

docker build --tag "$IMAGE" .

# Run attached so application logs and Ctrl+C are available.
exec docker run --rm --init \
    --name "$CONTAINER" \
    --publish "$HOST_PORT:9999" \
    --env SERVER_PORT=9999 \
    "$IMAGE" "$@"
