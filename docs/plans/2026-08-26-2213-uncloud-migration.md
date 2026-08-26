# Uncloud Migration Implementation Plan

> **For agentic workers:** Use executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Replace linkboard's Kamal deployment with Uncloud on the existing cluster (already running readx and ppnardstg), keeping the SQLite database on the server path `/root/linkboard-db`.

**Tech Stack:** Uncloud (`uc` CLI), Docker Buildx, GitHub Actions, mise, Babashka, Clojure/Integrant/Aero, SQLite

---

## Design

### Why

linkboard deploys through Kamal on an old server. The target server already runs Uncloud with two migrated services (`readx-app`, `ppnardstg-app`), so the cluster, Caddy reverse proxy, and SSH access are in place. This mirrors the readx migration (see readx `docs/plans/2026-08-23-0216-uncloud-migration.md` and its completion summary) with one substantive difference: **linkboard's SQLite database is real** — 8 migrations, live data — and must persist.

### Key decisions (settled in discussion)

**Bind mount, not a named volume.** The service mounts `/root/linkboard-db:/app/db`, byte-for-byte what Kamal did. The user has already staged a vacuumed copy of the database at `/root/linkboard-db/linkboard.sqlite` on the Uncloud server (`VACUUM INTO`, so no WAL-staleness concern). A named Docker volume was considered and rejected: for SQLite it improves neither deployments (same files, same WAL locking either way) nor scaling (the file, not the mount, is the single-instance constraint).

**No `config.edn` changes at all.** Prod already uses the `:default` jdbc-url `jdbc:sqlite:db/linkboard.sqlite`, which resolves to the mounted `/app/db/linkboard.sqlite`. Unlike readx there is no CORS/`APP_DOMAIN` config — `APP_DOMAIN` is used only for the Caddy ingress host, so it does not go into the container environment.

**Single instance, hard constraint.** SQLite over a bind mount must never scale past one replica. No `scale` key. Uncloud's rolling deploy briefly runs old and new containers with the file open simultaneously; WAL handles that, and it is the same behavior Kamal had.

**No healthcheck block in compose.** Mirrors readx/ppnardstg. The post-deploy probe is manual, against linkboard's health endpoint, which is **`/up`** (`src/linkboard/routes.clj:37`) — not `/health`.

**Remove the `outdated` CI job.** The user manually removed it from readx (`readx@0358ccb`) after migration because it blocks deploys on unrelated dependency staleness. Mirror that here as part of the workflow changes.

**Drop `ruby` from `.mise.toml`.** Kamal was its only consumer.

**Service name `linkboard-app`**, consistent with `readx-app` / `ppnardstg-app` on the same cluster.

### What changes

| File | Change |
|---|---|
| `compose.yaml` | New. Single `linkboard-app` service with the bind mount. |
| `.kamal/deploy.yml`, `.kamal/secrets` | Deleted. |
| `bb.edn` | Remove the `kamal` task (second-to-last task, before `build`). |
| `.github/workflows/deploy.yaml` | Rewrite the `deploy` job to the Uncloud shape. No Sentry release step exists here — the job ends at the deploy step. |
| `.github/workflows/checks.yaml` | Remove the `outdated` job. |
| `.mise.toml` | Add `uc` to `[tools]` and `[tool_alias]`; remove `ruby`. |
| `README.md` | Replace the Kamal setup/deploy section (`README.md:49-65`) with an Uncloud pointer. |

Nine paths: one created, two deleted, six modified (including `docs/plans/`). No application source changes.

### What goes away

- `.kamal/` — both files.
- The `kamal` task in `bb.edn` (`bb.edn:66-67`).
- `ruby/setup-ruby`, `gem install kamal`, and the `Kamal Release` (`if: cancelled()`) steps from the deploy workflow.
- `ruby = "3.3.0"` from `.mise.toml`.
- The `outdated` job from `checks.yaml`.
- `permissions: packages: write` → `read` (Uncloud builds on the runner and ships over SSH; no GHCR push).
- `--version=${{ github.sha }}` — functionally inert, the app never read it.

### CI configuration (the user sets this, not the executor)

| Kind | Name |
|---|---|
| `vars.` | `SERVER_IP`, `APP_DOMAIN` |
| `secrets.` | `SSH_PRIVATE_KEY`, `SESSION_SECRET_KEY`, `SENTRY_DSN`, `OPENROUTER_API_KEY` |

`SERVER_IP` and `APP_DOMAIN` move from `secrets.` to `vars.`. Delete the old secrets afterwards so the workflow cannot silently read an empty `vars.` value. `SERVER_IP` is the **Uncloud** server (same as readx/ppnardstg), not the old Kamal server.

### Cutover (manual, user-driven)

The old Kamal deployment stays on the old server and keeps serving until DNS moves. Consequences:

1. The staged database copy is a **snapshot**: any writes on the old server after the `VACUUM INTO` are lost. The user (sole user of the app) simply avoids adding links between DNS switch and first Uncloud deploy, or re-copies the file right before switching.
2. Order: configure CI vars/secrets → switch DNS to the Uncloud server → merge (deploy fires) → probe `https://<APP_DOMAIN>/up` → later, decommission the old server.
3. No file-contention concern on the new server: nothing there opens `/root/linkboard-db` until the first Uncloud deploy.

### Verification strategy

- `bb fmt-check`, `bb lint`, `bb test` stay green (test profile uses `:memory:`, untouched).
- YAML parse checks on both new/edited workflow files and compose.
- Env contract cross-check: every `printenv` in `compose.yaml` must match the deploy step's `env:` block exactly.
- **Local prod-profile boot test** (the readx lesson: "can't test the deployment" is not "can't test what the deployment changes"): boot the full system under `:prod` from a scratch directory, port overridden, and hit `/up`. This exercises the same code path the container runs — sqlite file creation under `db/`, ragtime migrations, server start. The scratch run creates a fresh db; in prod the mounted real db is opened and migrated identically.
- A green CI deploy confirms the container started and survived the monitoring period; the real verification is the `/up` probe over HTTPS.

### Known caveats — not fixed here

- The `Dockerfile` uses amd64-only binaries (babashka static, `tailwindcss-linux-x64-musl`), so the image cannot be built on an arm64 host; CI (amd64) is fine. Rot risk is lower than readx's (no apk version pins — that was what broke readx's first deploy); the pinned GitHub release assets should be existence-checked with `curl -I` during execution.
- One open uncertainty: uncloud's acceptance of compose short-syntax bind mounts (`/host:/container`). ppnardstg only exercises named volumes. Uncloud parses standard compose, so short syntax should work; if the first deploy rejects it, the long form (`type: bind`) is a two-line fallback, not a design change.

---

## File Structure

**Create:**
- `compose.yaml` — Uncloud service definition: build config, Caddy ingress, three secret-sourced env vars, bind mount.

**Modify:**
- `.github/workflows/deploy.yaml` — single `uc deploy` invocation.
- `.github/workflows/checks.yaml` — drop the `outdated` job.
- `.mise.toml` — add `uc`, drop `ruby`.
- `bb.edn` — drop the `kamal` task.
- `README.md` — deployment section.

**Delete:**
- `.kamal/deploy.yml`
- `.kamal/secrets`

---

## Task 1: Add `compose.yaml`

**Files:**
- Create: `compose.yaml`

- [x] **Step 1: Write `compose.yaml`**
  Use readx's `compose.yaml` as the direct template (same cluster, same shape). Top-level `x-context: personal`. One service, `linkboard-app`:
  - `build:` with `platforms: [linux/amd64]`, `cache_from: [type=gha]`, `cache_to: [type=gha,mode=max]`. No `image:` key.
  - `x-ports: ["${APP_DOMAIN}:80/https"]`.
  - `volumes: ["/root/linkboard-db:/app/db"]` — compose short-syntax bind mount.
  - `environment:` exactly three keys:

    ```yaml
    SESSION_SECRET_KEY: secret://session_secret_key
    SENTRY_DSN: secret://sentry_dsn
    OPENROUTER_API_KEY: secret://openrouter_api_key
    ```

  Top-level `secrets:` block defining the three, each as `x-command: printenv <UPPERCASE_NAME>`.

  Do NOT add: `scale`, `healthcheck`, `APP_DOMAIN` in environment, or a top-level `volumes:` block (bind mounts need no declaration).

- [x] **Step 2: Verify it parses and has the right shape**
  Run: `python3 -c "import yaml; d=yaml.safe_load(open('compose.yaml')); assert list(d['services'])==['linkboard-app']; s=d['services']['linkboard-app']; assert s['volumes']==['/root/linkboard-db:/app/db']; assert set(d['secrets'])=={'session_secret_key','sentry_dsn','openrouter_api_key'}; assert 'scale' not in s and 'healthcheck' not in s; print('ok')"`
  Expected: `ok`

- [x] **Step 3: Confirm the env keys match what the app reads**
  Run: `grep -n "#env" resources/config.edn`
  Expected: only `SENTRY_DSN`, `SESSION_SECRET_KEY`, `OPENROUTER_API_KEY` — all three in `compose.yaml`, nothing else needed.

- [x] **Step 4: Commit**
  `git commit -m "Add uncloud compose config"`

---

## Task 2: Update `.mise.toml`

**Files:**
- Modify: `.mise.toml`

- [x] **Step 1: Add `uc`, remove `ruby`**
  Under `[tools]`: add `uc = "0.20.0"` (the version readx/ppnardstg pin) and delete the `ruby = "3.3.0"` line. Under `[tool_alias]`: add `uc = "github:psviderski/uncloud"`.

- [x] **Step 2: Verify mise resolves it**
  Run: `cd /Users/andrew/Projects/linkboard && mise install uc && mise exec -- uc version | grep Version`
  Expected: `Version:     0.20.0`

- [x] **Step 3: Commit**
  `git commit -m "Add uc CLI to mise tools, drop ruby"`

---

## Task 3: Rewrite the workflows

**Files:**
- Modify: `.github/workflows/deploy.yaml`, `.github/workflows/checks.yaml`

- [x] **Step 1: Rewrite the `deploy` job**
  Use readx's final `.github/workflows/deploy.yaml` as the template. Keep the trigger and `checks` job. In `deploy`:
  - `permissions: packages: read`.
  - Steps: `actions/checkout@v7`, `webfactory/ssh-agent@v0.10.0` (with `SSH_PRIVATE_KEY`), `jdx/mise-action@v4` with `install_args: "uc"`, `docker/setup-buildx-action@v4.2.0`, `crazy-max/ghaction-github-runtime@v4` — keeping readx's two explanatory comments on the buildx and github-runtime steps.
  - Delete ruby/gem/kamal steps including the trailing `Kamal Release` step.
  - Deploy step:

    ```yaml
    env:
      APP_DOMAIN: ${{ vars.APP_DOMAIN }}
      SESSION_SECRET_KEY: ${{ secrets.SESSION_SECRET_KEY }}
      SENTRY_DSN: ${{ secrets.SENTRY_DSN }}
      OPENROUTER_API_KEY: ${{ secrets.OPENROUTER_API_KEY }}
    run: uc --context personal --connect root@${{ vars.SERVER_IP }} deploy -f compose.yaml --yes linkboard-app
    ```

    Note `APP_DOMAIN` is in `env:` for the `${APP_DOMAIN}` interpolation in `x-ports`, even though it is not passed into the container.
  - There is no Sentry release step in this project; the job ends here.

- [x] **Step 2: Remove the `outdated` job from `checks.yaml`**
  Delete the whole `outdated:` job block (mirrors readx@0358ccb). Leave `lint-fmt` and `tests` untouched.

- [x] **Step 3: Verify both parse and nothing lingers**
  Run: `python3 -c "import yaml; [yaml.safe_load(open(f)) for f in ['.github/workflows/deploy.yaml','.github/workflows/checks.yaml']]; print('ok')" && grep -n -iE "kamal|ruby|packages: write|outdated" .github/workflows/deploy.yaml .github/workflows/checks.yaml`
  Expected: `ok`, then no grep matches.

- [x] **Step 4: Cross-check the env contract**
  Every `printenv <VAR>` in `compose.yaml` plus `${APP_DOMAIN}` from `x-ports` must appear in the deploy step's `env:`, and nothing extra. Verify with a small Python script parsing both files (as done in readx), not by eye.
  Expected: demanded == supplied == {APP_DOMAIN, SESSION_SECRET_KEY, SENTRY_DSN, OPENROUTER_API_KEY}.

- [x] **Step 5: Commit**
  `git commit -m "Deploy with uncloud instead of kamal"`

---

## Task 4: Remove Kamal

**Files:**
- Delete: `.kamal/deploy.yml`, `.kamal/secrets`
- Modify: `bb.edn`, `README.md`

- [x] **Step 1: Delete the Kamal directory**
  Run: `git rm -r .kamal`

- [x] **Step 2: Remove the `kamal` task from `bb.edn`**
  The task at `bb.edn:66-67` sits between `fetch-assets` and `build` (not last, unlike readx), so no closing-brace juggling — delete the two lines and the blank line.

- [x] **Step 3: Verify `bb.edn` parses and the task is gone**
  Run: `bb tasks`
  Expected: task list prints; `kamal` absent.

- [x] **Step 4: Update the README deployment section**
  `README.md:49-65` documents ruby/kamal install, `bb kamal setup`, `bb kamal deploy`. Replace with a short Uncloud paragraph: pushes to `master` run `.github/workflows/deploy.yaml`, which builds on the runner and deploys `compose.yaml` to the Uncloud cluster; link `https://uncloud.run/docs`. Mention the database lives on the server at `/root/linkboard-db` (bind-mounted to `/app/db`).

- [x] **Step 5: Verify no Kamal references remain**
  Run: `git grep -n -i "kamal" -- . ':!docs/plans'`
  Expected: no matches.

- [x] **Step 6: Run the check suite**
  Run: `bb fmt-check && bb lint && bb test`
  Expected: all PASS. (Do not use `bb check` — it runs `fmt`/`outdated` which modify files.)

- [x] **Step 7: Commit**
  `git commit -m "Remove kamal deployment config"`

---

## Task 5: Local prod-boot verification

**Files:** none modified (scratch-directory test only).

- [x] **Step 1: Boot the system under the prod profile**
  From a scratch directory containing nothing (fresh `db/` will be created by sqlite), with the project classpath (`clj -Spath` from the project dir, relative entries absolutized), run a script that: reads the `:prod` config, overrides the server port to a free high port (prod binds 80), `ig/init`s the system, requests `/up` over localhost, halts.
  Environment: `SESSION_SECRET_KEY=<dummy>` (required in prod); leave `SENTRY_DSN`/`OPENROUTER_API_KEY` unset — both default to nil via `#or`.
  Expected: all 8 ragtime migrations apply to the fresh db, system starts, `/up` responds successfully, clean halt.

- [x] **Step 2: Check Dockerfile external URLs for rot**
  Run `curl -fsSI` against: the babashka install script URL, the bb `1.12.194` linux-amd64-static release asset, and the tailwindcss `v4.0.3` `tailwindcss-linux-x64-musl` release asset (all referenced in `Dockerfile`).
  Expected: HTTP 200 for all three. (This is the readx lesson — its first deploy died on a rotted apk pin. linkboard has no apk pins, but the release-asset URLs are the equivalent risk.)

- [x] **Step 3: No commit** — nothing changed.

---

## Task 6: Final review

**Files:** none modified.

- [x] **Step 1: Read the complete diff**
  Run: `git diff master...HEAD --stat`
  Expected: `compose.yaml` and the plan added; `.kamal/*` deleted; `bb.edn`, `.mise.toml`, `.gitignore`, both workflow files, `README.md` modified. No changes under `src/`; `resources/config.edn` untouched; `Dockerfile` untouched.

- [x] **Step 2: Report to the user what they must configure**
  Before merging, in order:
  1. Add repository **variables** `SERVER_IP` (the Uncloud server) and `APP_DOMAIN`.
  2. Confirm **secrets** `SSH_PRIVATE_KEY`, `SESSION_SECRET_KEY`, `SENTRY_DSN`, `OPENROUTER_API_KEY`.
  3. Delete the now-unused `SERVER_IP` and `APP_DOMAIN` **secrets**.
  4. Confirm no links were added in the old app since the `VACUUM INTO` snapshot; if any were — or if unsure — re-copy the file (`VACUUM INTO` again) to the Uncloud server before switching. Stopping writes *now* does not recover writes made since the snapshot.
  5. Switch DNS for the linkboard domain to the Uncloud server.
  6. Merge — the deploy fires.

  After the deploy goes green:

  7. Probe: `curl -fsS https://<APP_DOMAIN>/up` — the real verification; green CI only proves the container didn't immediately exit.
  8. If it fails: `uc --context personal --connect root@<SERVER_IP> logs linkboard-app`.
  9. Verify data is present (open the app, check boards/links).
  10. Later: decommission the old Kamal server entirely.

---

## Completion Summary

**Status: complete.** Implemented 2026-08-26 on `uncloud-migration`:

| Commit | Task |
|---|---|
| `4d0bbb7` | Add uncloud compose config |
| `6a6e9a0` | Add uc CLI to mise tools, drop ruby |
| `e6dc3d5` | Deploy with uncloud instead of kamal (+ remove `outdated` job from checks.yaml) |
| `be2ce20` | Remove kamal deployment config |

No changes under `src/`; `resources/config.edn` and `Dockerfile` untouched.

### Verification performed

- `bb fmt-check`, `bb lint` pass. `bb test`: 42 assertions, 0 failures; 3 errors, all etaoin browser tests unable to launch Chrome on this arm64 host — environmental (README notes tests need Chrome; CI's ubuntu runner has it and is the gate).
- `bb tasks` parses with `kamal` gone; no Kamal references remain outside `docs/plans`.
- Both workflow files and `compose.yaml` parse; compose shape asserted programmatically (single service, bind mount, three secrets, no scale/healthcheck).
- Env contract cross-checked programmatically: compose demands exactly `APP_DOMAIN` (x-ports), `SESSION_SECRET_KEY`, `SENTRY_DSN`, `OPENROUTER_API_KEY`; the deploy step supplies exactly those.
- Dockerfile rot check: bb install script, bb `1.12.194` amd64 static asset, tailwind `v4.0.3` x64-musl asset all return 200; both Docker base image tags still exist on the registry.
- **Prod-boot test, fresh db:** booting from a directory with an empty `db/` (simulating a fresh bind mount) applies all 8 migrations, `/up` returns 200, clean halt. Booting *without* `db/` fails with `SQLITE_CANTOPEN` — confirming the bind mount is load-bearing (Docker creates the `/app/db` mount point; sqlite-jdbc will not create the directory itself).
- **Prod-boot test, real snapshot:** booting against a copy of `linkboard-2026-08-26.sqlite` (the staged server snapshot) re-applies nothing (ragtime no-ops), `/up` returns 200 — the closest local simulation of the first production boot.

### Deviations

> Deviation (process): as in the readx migration, one codex review pass over the whole change instead of per-task reviews; the plan-document codex review ran in parallel with execution since the design was already user-approved point-by-point in discussion.

> Deviation (Task 4): also updated `PROJECT_SUMMARY.md` (three Kamal references) — not listed in the plan's file inventory, but leaving a doc contradicting the deploy setup would be worse.

> Deviation (Task 5): added the two prod-boot runs (fresh db and real snapshot) beyond the single boot the plan specified, after the fresh-`db/`-absent run surfaced `SQLITE_CANTOPEN` and made the mount-simulation distinction worth pinning down. Also verified base-image tags, not just release-asset URLs.

> Plan fix from codex review: Task 6 step 4 rewritten — "stop adding links now" does not recover writes made since the snapshot; the step now requires confirming no post-snapshot writes or re-copying.

### What the plan could have specified better

The prod-boot step should have specified *how* to simulate the container filesystem: the empty-`db/` distinction (mount point present vs absent) changes the outcome from `SQLITE_CANTOPEN` to success, and the plan didn't anticipate it. It also missed the real-snapshot boot, which turned out to be the most valuable check.
