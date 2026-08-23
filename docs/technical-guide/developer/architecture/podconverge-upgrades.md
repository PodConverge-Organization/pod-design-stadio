# PodConverge upgrades

Each Penpot upgrade must record the exact upstream commit SHA and use a dedicated integration branch. Classify every fork change as a product contract, integration contract, infrastructure adaptation, or temporary compatibility change so reviewers can distinguish durable PodConverge behavior from upgrade scaffolding.

Release-gating CI must not use a moving DevEnv tag. Update the pinned DevEnv digest only at a reviewed upgrade checkpoint. Repository-managed tools, including Playwright, must run from the installed dependency graph so their versions remain aligned with the lockfile.

## Publishing the CI DevEnv image

PodConverge CI DevEnv images are built from the fork's reviewed `docker/devenv/Dockerfile` by `.github/workflows/publish-podconverge-devenv.yml`. The workflow publishes to `ghcr.io/podconverge-organization/pod-design-stadio-devenv` with the source commit SHA as its tag. Release-gating CI must consume the resulting immutable digest, never the SHA tag or `latest`.

GitHub defaults a newly published GHCR container package to private. After the first successful publication, an administrator must verify the package and intentionally make it **Public** before CI is repinned, allowing CI job containers to pull it anonymously without duplicated registry credentials. If organization policy prevents publication or public visibility, stop and resolve that policy explicitly; do not add a PAT workaround.

This DevEnv package is CI infrastructure only, not a production application release artifact. Publication and public visibility must not be documented as complete until runtime evidence confirms them.

PodConverge-specific tests should protect product and integration behavior rather than disposable upstream internals. Database migrations, immutable release artifacts, production-like staging, and a tested rollback path are mandatory release gates; these gates must be implemented and verified rather than assumed.

Production Design Studio navigation messages may originate only from the exact production plugin origin `https://plugin.podconverge.com` or the exact developer plugin origin `https://plugin-develop.podconverge.com`. The developer plugin is developer-only, manually installed, and is not the default plugin. Sender trust does not bypass the connected `plugin-modal` iframe identity checks or the destination origin and path allowlists.

## Design Studio session recovery

Design Studio uses `PENPOT_DESIGN_STUDIO_RECOVERY_URI` as runtime frontend configuration for protected-route session recovery. The expected production endpoint is `https://app.podconverge.com/auth/design-studio/recover`, but this value must flow through `/js/config.js` and must not be compiled or hardcoded into ClojureScript application logic.

The protected route families are workspace, dashboard, and settings routes, matched by route identity rather than browser URL substring checks. Anonymous `/view` and `/view/:file-id` routes remain compatible with share-link viewing and must not trigger automatic recovery.

When recovery is allowed, Design Studio redirects to the configured Pod frontend bridge with `returnTo` set to the exact current Design Studio `window.location.href`, including query parameters and hash fragments. The Pod frontend bridge owns Pod login, Design Studio session reissue, and final redirect back to the exact validated `returnTo`; Design Studio must not duplicate backend reissue logic.

Recovery is bounded by a one-attempt-per-tab `sessionStorage` guard. The guard is written synchronously immediately before the external redirect, cleared after a later authenticated profile result, and prevents repeated redirects while the Design Studio session remains invalid. Missing or invalid recovery configuration fails closed to a local authentication/recovery error state without external redirect.

Production must deploy prebuilt immutable images, not build release images during deployment. Deployment must not depend on pulling changes into the currently dirty production checkout.

This CI work item does not authorize merging PR #15 or deploying it to production.
