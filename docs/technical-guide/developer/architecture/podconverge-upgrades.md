# PodConverge upgrades

Each Penpot upgrade must record the exact upstream commit SHA and use a dedicated integration branch. Classify every fork change as a product contract, integration contract, infrastructure adaptation, or temporary compatibility change so reviewers can distinguish durable PodConverge behavior from upgrade scaffolding.

Release-gating CI must not use a moving DevEnv tag. Update the pinned DevEnv digest only at a reviewed upgrade checkpoint. Repository-managed tools, including Playwright, must run from the installed dependency graph so their versions remain aligned with the lockfile.

PodConverge-specific tests should protect product and integration behavior rather than disposable upstream internals. Database migrations, immutable release artifacts, production-like staging, and a tested rollback path are mandatory release gates; these gates must be implemented and verified rather than assumed.

Production must deploy prebuilt immutable images, not build release images during deployment. Deployment must not depend on pulling changes into the currently dirty production checkout.

This CI work item does not authorize merging PR #15 or deploying it to production.
