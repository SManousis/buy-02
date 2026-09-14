#!/usr/bin/env sh
set -eu

image_tag="${1:?usage: deploy-staging.sh IMAGE_TAG}"
project_name="buy01-staging"
state_dir=".jenkins-state"
state_file="${state_dir}/staging-last-successful-tag"
compose_files="-f docker-compose.yml -f docker-compose.ci.yml"
services="mongo kafka discovery-service user-service product-service media-service api-gateway frontend"

: "${JWT_SECRET:?JWT_SECRET must be supplied by Jenkins Credentials}"

mkdir -p "$state_dir"
previous_tag=""
if [ -f "$state_file" ]; then
    previous_tag="$(cat "$state_file")"
fi

compose() {
    tag="$1"
    shift
    IMAGE_TAG="$tag" docker compose --project-name "$project_name" $compose_files "$@"
}

container_status() {
    container_id="$1"
    docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id"
}

wait_for_services() {
    tag="$1"
    attempts=60

    while [ "$attempts" -gt 0 ]; do
        all_healthy=true

        for service in $services; do
            container_id="$(compose "$tag" ps --quiet "$service")"
            if [ -z "$container_id" ]; then
                echo "Service ${service} has no running container."
                all_healthy=false
                continue
            fi

            status="$(container_status "$container_id")"
            case "$status" in
                healthy|running)
                    ;;
                unhealthy|exited|dead)
                    echo "Service ${service} entered terminal state: ${status}."
                    return 1
                    ;;
                *)
                    all_healthy=false
                    ;;
            esac
        done

        if [ "$all_healthy" = true ]; then
            return 0
        fi

        attempts=$((attempts - 1))
        sleep 5
    done

    echo "Timed out waiting for staging services to become healthy."
    return 1
}

show_diagnostics() {
    tag="$1"
    compose "$tag" ps || true
    compose "$tag" logs --tail 100 || true
}

rollback() {
    if [ -z "$previous_tag" ]; then
        echo "No last successful staging tag exists; stopping the failed first deployment."
        compose "$image_tag" down || true
        return 1
    fi

    echo "Rolling staging back from ${image_tag} to ${previous_tag}."
    compose "$previous_tag" up --detach --no-build --remove-orphans

    if wait_for_services "$previous_tag"; then
        echo "Rollback succeeded; staging is healthy on ${previous_tag}."
        return 0
    fi

    echo "Rollback failed; staging did not recover on ${previous_tag}."
    show_diagnostics "$previous_tag"
    return 1
}

echo "Validating staging configuration for image tag ${image_tag}."
compose "$image_tag" config --quiet

echo "Building immutable staging images for ${image_tag}."
compose "$image_tag" build

echo "Deploying ${image_tag} to local staging."
compose "$image_tag" up --detach --remove-orphans

if [ "${FORCE_DEPLOYMENT_FAILURE:-false}" = "true" ]; then
    echo "AUDIT: forcing the new frontend container to stop so rollback can be verified."
    frontend_id="$(compose "$image_tag" ps --quiet frontend)"
    if [ -z "$frontend_id" ]; then
        echo "AUDIT: frontend container was not created."
    else
        docker stop "$frontend_id"
    fi
fi

if ! wait_for_services "$image_tag"; then
    show_diagnostics "$image_tag"
    if rollback; then
        echo "Deployment failed, but automatic rollback succeeded."
    else
        echo "Deployment and automatic rollback both failed."
    fi
    exit 1
fi

if ! curl --fail --silent --show-error --max-time 15 http://docker:4200/ >/dev/null; then
    echo "Frontend smoke test failed for ${image_tag}."
    show_diagnostics "$image_tag"
    if rollback; then
        echo "Smoke test failed, but automatic rollback succeeded."
    else
        echo "Smoke test and automatic rollback both failed."
    fi
    exit 1
fi

printf '%s\n' "$image_tag" > "$state_file"
echo "Staging deployment ${image_tag} is healthy and passed its smoke test."
compose "$image_tag" ps
