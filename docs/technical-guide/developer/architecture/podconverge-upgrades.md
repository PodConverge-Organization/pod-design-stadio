# PodConverge upgrades

Each Penpot upgrade records the exact upstream commit SHA and uses a dedicated integration branch. Fork changes are classified as product contract, integration contract, infrastructure adaptation, or temporary compatibility change so reviewers can separate durable PodConverge behavior from upgrade scaffolding.

## CI and DevEnv provenance

Release-gating CI must not depend on a moving DevEnv tag. Repository-managed tools, including Playwright, must come from the repository dependency graph so tool versions remain aligned with the lockfile.

PodConverge's DevEnv publisher is `.github/workflows/publish-podconverge-devenv.yml`. It publishes the CI infrastructure package `ghcr.io/podconverge-organization/pod-design-stadio-devenv` with the source SHA as the image tag. Release-gating consumers use the immutable digest, not `latest` or the mutable SHA tag alone.

GHCR public-visibility handling remains an explicit operator gate after publication. Do not document a current 2.17.1 GHCR image as published, public, or consumed until repository or runtime evidence proves it. The DevEnv image is CI infrastructure, not a production application release artifact.

## Product and release gates

PodConverge-specific tests protect durable product and integration behavior. Database migrations require production-like validation before production. Releases require immutable application artifacts, production-like staging, and rollback verification as mandatory gates.

Production deploys prebuilt reviewed images rather than building release artifacts from a dirty production checkout.

## Workspace options contract

PodConverge intentionally exposes only the Design tab in the workspace right-hand options sidebar. The Prototype tab and interaction controls are not part of the user-facing options sidebar contract.

This is a UI product boundary only. Underlying upstream Penpot prototype and interactions data structures remain globally compatible outside this sidebar boundary.

Future Penpot upgrades must preserve the focused PodConverge workspace options regression test so the Prototype sidebar tab does not reappear unnoticed.

## Plugin navigation trust boundary

Production Design Studio navigation messages may originate only from `https://plugin.podconverge.com` or `https://plugin-develop.podconverge.com`. The developer plugin remains developer-only and manually installed.

The developer plugin must not bypass the connected `plugin-modal` iframe identity checks, destination-origin validation, or destination-path validation.

## Design Studio session recovery

Design Studio uses `PENPOT_DESIGN_STUDIO_RECOVERY_URI` as runtime frontend configuration for protected-route session recovery. The expected production recovery endpoint is `https://app.podconverge.com/auth/design-studio/recover`. This value must flow through runtime config instead of being compiled into ClojureScript application logic.

Protected route families are workspace, dashboard, and settings. Anonymous `/view` routes do not trigger recovery.

When recovery is allowed, `returnTo` preserves the exact Design Studio location, including query parameters and hash fragments. The Pod frontend bridge owns Pod login, Design Studio session reissue, and final redirect.

Recovery is bounded by a one-attempt-per-tab `sessionStorage` guard. Missing or invalid recovery config fails closed locally.

Only the canonical backend authentication-required marker triggers protected-route session recovery:

```clojure
{:type :authentication :code :authentication-required}
```

Local access-denied errors and other authentication-like errors remain in the local exception flow.

## Production custom-image boundary

`docker/images/docker-compose.override.yaml` is the repository-owned custom image selector for PodConverge production composition. For the current fork contract it resolves at least:

```text
penpot-frontend -> sajjadardekani/penpot-frontend:custom
penpot-backend  -> sajjadardekani/penpot-backend:custom
```

The backend custom image is required because PodConverge owns backend session-cookie behavior that is not present in an unmodified upstream backend artifact.

The moving `:custom` deployment alias is not artifact provenance. Release, stage, and production approval must record and verify the exact immutable image ID or digest that the alias resolves to.
