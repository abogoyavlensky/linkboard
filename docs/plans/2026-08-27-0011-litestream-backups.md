# Litestream Backups Implementation Plan

> **For agentic workers:** Use executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Continuously replicate `linkboard.sqlite` to Cloudflare R2 with a 7-day point-in-time restore window, via a Litestream sidecar in the existing Uncloud compose file, plus a second, independent daily snapshot layer built on `VACUUM INTO`.

**Tech Stack:** Litestream 0.5.16, Cloudflare R2 (S3-compatible), Uncloud (`uc` CLI), Docker, systemd timers, SQLite

---

## Design

### Why

linkboard's database currently has no backup of any kind. It is a single 700 KB file on a single Hetzner VPS, holding 6 users, 50 boards and 909 links. A disk failure, an accidental `DELETE`, or a bad migration loses all of it with no recovery path. The migration to Uncloud (`docs/plans/2026-08-26-2213-uncloud-migration.md`) put the database on a bind mount at `/root/linkboard-db`, which is exactly the shape a sidecar needs.

### Two layers, deliberately

**Layer 1 — Litestream, continuous.** Tails the WAL and streams it to object storage, giving a recovery point objective measured in seconds and point-in-time restore anywhere in the retention window. This is the layer that survives losing the server.

**Layer 2 — daily `VACUUM INTO` snapshots on the host.** Dated, self-contained database files. This is the layer that survives *Litestream being wrong* — a misconfigured bucket, expired R2 credentials, a silent replication stall. It is an independent mechanism with an independent failure mode, and each output is a plain SQLite file that can be opened directly with no tooling.

The two are not redundant. Litestream's own retention already provides 7-day PITR, so Layer 2 adds no *coverage*; it adds **independence**. A single mechanism that fails silently means no backups at all, and the failure is only discovered at restore time. At 700 KB per snapshot, the cost of the second layer is about 5 MB and one systemd timer.

### Key decisions

**Litestream 0.5.16, pinned.** Current release as of 2026-08-05. The 0.5 line changed the config schema from 0.3 in ways that matter here: `replica` (singular) replaces the deprecated `replicas` array, and retention moved to a global `snapshot.retention` / `snapshot.interval` block. Any 0.3-era config example found online will not work. Pin the exact patch version rather than `latest` — an unattended sidecar silently upgrading its storage format is not a thing to discover during a restore.

**Sidecar container, not host systemd, for Litestream.** Keeps the replication config in git and deploys through the same pipeline as the app. ppnardstg already proves Uncloud handles multi-service compose files with image-based services and volumes, so this is a shape the cluster is known to accept.

**Config baked into a thin image rather than bind-mounted.** Litestream expands `${VAR}` in its config file by default (disabled only by `-no-expand-env`), so the config contains structure but no secrets and is safe to commit. A three-line `Dockerfile` that `FROM`s the official image and `COPY`s the config keeps everything version-controlled. The alternative — bind-mounting a config file placed on the server by hand — creates untracked server state that drifts silently.

**Cloudflare R2.** S3-compatible, zero egress fees, and a few megabytes costs effectively nothing. Critically it is a *different vendor* from Hetzner: a compromised or suspended Hetzner account cannot take out both the server and its backups.

**`sync-interval: 10s` rather than the 1s default.** Litestream only issues writes when the WAL actually changes, so a low-traffic personal app will not hit the ceiling either way — but 1s is the wrong shape for R2's class-A operation pricing if traffic ever grows, and 10s costs at most ten seconds of data. Set it deliberately.

**`busy-timeout: 5s` on the Litestream side.** Litestream's default is 1s. The app's connections use 5000 ms after `docs/plans/2026-08-27-0010-sqlite-setup-hardening.md`; leaving Litestream at 1s means the checkpointer is the first thing to give up under contention. Match them.

**`-restore-if-db-not-exists` deliberately NOT enabled.** It sounds like free disaster recovery, but it conflates "the database is gone" with "the bind mount is not ready yet". A mount that fails or races on boot would present as an empty directory and trigger an unwanted restore over it. Restores here should be a decision, not an accident.

**Layer 2 lives on the host, not in a container.** It needs `sqlite3` and a scheduler, and systemd timers are already the machine's scheduler. Wrapping it in a container to avoid one `apt-get install` would add an image to maintain for no benefit. The scripts are committed under `ops/` so they are version-controlled even though they are installed manually.

### Retention

| Setting | Value | Meaning |
|---|---|---|
| `snapshot.interval` | `24h` | A full snapshot daily. |
| `snapshot.retention` | `168h` | 7 days of snapshots retained; restore to any point within them. |
| Layer 2 local dailies | 7 files | Pruned by age, see the caveat below. |

Litestream's default `snapshot.retention` is `24h`, which is far too short — it must be set explicitly.

> ⚠️ **The one destructive operation in this plan.** Layer 2's rotation deletes snapshot files older than 7 days via `find ... -mtime +7 -delete`. It is scoped with `-maxdepth 1`, an anchored filename glob, and a dedicated directory that contains nothing else. Task 7 runs it in `-print` mode first so the exact file list is visible before deletion is ever enabled. If unbounded growth is preferable, the prune line can simply be omitted — 700 KB/day is roughly 250 MB/year.

### What changes

| File | Change |
|---|---|
| `litestream/litestream.yml` | New. Replication config, secrets via `${VAR}` expansion. |
| `litestream/Dockerfile` | New. Official image + baked config. |
| `compose.yaml` | Add the `linkboard-litestream` service and four secrets. |
| `.github/workflows/deploy.yaml` | Supply R2 env vars; deploy both services. |
| `ops/linkboard-snapshot.sh` | New. Layer 2 snapshot script. |
| `ops/linkboard-snapshot.service` | New. systemd unit. |
| `ops/linkboard-snapshot.timer` | New. Daily timer. |
| `README.md` | Document the backup setup and the restore procedure. |

### Open uncertainties — resolve during execution, not now

- **Does `uc deploy` handle two services in one invocation?** The current command names the service explicitly (`... --yes linkboard-app`). Dropping the name should deploy everything in the file. If Uncloud rejects that, the fallback is two sequential invocations — a workflow change, not a design change.
- **R2 addressing style.** Litestream exposes `force-path-style` for S3-compatible services. R2 supports virtual-hosted style, so start without it; add it only if the first replication fails with a bucket-addressing error.
- **Litestream metadata files.** Litestream 0.5 keeps its own state alongside the database. Expect additional files or a directory to appear in `/root/linkboard-db`. This is fine — the app opens one specific path — but Layer 2 writes to a *separate* directory so its glob can never pick them up.

### Verification strategy

Backups that have never been restored are not backups. This plan is not complete until Task 6 has restored from R2 and compared row counts against the live database. Everything before that is setup; Task 6 is the actual deliverable.

---

## File Structure

**Create:**
- `litestream/litestream.yml`
- `litestream/Dockerfile`
- `ops/linkboard-snapshot.sh`
- `ops/linkboard-snapshot.service`
- `ops/linkboard-snapshot.timer`

**Modify:**
- `compose.yaml`
- `.github/workflows/deploy.yaml`
- `README.md`

**Delete:** none.

---

## Task 0: R2 setup (the user does this, not the executor)

**Files:** none.

- [ ] **Step 1: Create the bucket and credentials**
  In the Cloudflare dashboard: create an R2 bucket (suggested name `linkboard-backups`), then create an R2 API token scoped to **Object Read & Write** on that bucket only — not an account-wide token.

  Collect four values:

  | Value | Shape |
  |---|---|
  | Bucket name | `linkboard-backups` |
  | Endpoint | `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` |
  | Access key id | from the API token |
  | Secret access key | from the API token, shown once |

- [ ] **Step 2: Add them to GitHub**
  Repository **secrets**: `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET`, `R2_ENDPOINT`.

  All four go in `secrets:` rather than `vars:` — the endpoint embeds the Cloudflare account id, and the bucket name is not worth leaking separately.

---

## Task 1: Litestream config and image

**Files:**
- Create: `litestream/litestream.yml`, `litestream/Dockerfile`

- [ ] **Step 1: Write `litestream/litestream.yml`**
  ```yaml
  snapshot:
    interval: 24h
    retention: 168h

  dbs:
    - path: /data/linkboard.sqlite
      busy-timeout: 5s
      replica:
        type: s3
        bucket: ${R2_BUCKET}
        path: linkboard
        endpoint: ${R2_ENDPOINT}
        region: auto
        sync-interval: 10s
  ```

  `region: auto` is what R2 expects. Credentials are deliberately absent — Litestream reads `LITESTREAM_ACCESS_KEY_ID` and `LITESTREAM_SECRET_ACCESS_KEY` from the environment, so no secret ever appears in a committed file.

- [ ] **Step 2: Write `litestream/Dockerfile`**
  ```dockerfile
  FROM litestream/litestream:0.5.16
  COPY litestream/litestream.yml /etc/litestream.yml
  CMD ["replicate"]
  ```

  `/etc/litestream.yml` is the default config path, so no `-config` flag is needed. The build context is the repo root (set in `compose.yaml`), which is why the `COPY` source is path-qualified.

- [ ] **Step 3: Verify the image's entrypoint accepts a bare `replicate`**
  Run: `docker run --rm litestream/litestream:0.5.16 version`
  Expected: prints `0.5.16`. This confirms the entrypoint is the `litestream` binary and that a bare subcommand as `CMD` is correct. If the image instead expects a full command line, adjust `CMD` to `["litestream", "replicate"]`.

- [ ] **Step 4: Verify the config parses**
  Build the image locally and run Litestream against the config with dummy env values, expecting a config-validation pass rather than a successful replication:
  `docker build -f litestream/Dockerfile -t linkboard-litestream:test . && docker run --rm -e R2_BUCKET=x -e R2_ENDPOINT=https://example.com linkboard-litestream:test replicate -once`
  Expected: it fails on credentials or a missing database — **not** on a YAML or unknown-key error. An "unknown field" error means the config is 0.3-shaped and must be corrected.

- [ ] **Step 5: Commit**
  `git commit -m "Add litestream replication config and image"`

---

## Task 2: Add the sidecar to compose

**Files:**
- Modify: `compose.yaml`

- [ ] **Step 1: Add the service**
  Alongside `linkboard-app`, keeping the existing file's style:

  ```yaml
    linkboard-litestream:
      build:
        context: .
        dockerfile: litestream/Dockerfile
        platforms:
          - linux/amd64
      volumes:
        - /root/linkboard-db:/data
      environment:
        LITESTREAM_ACCESS_KEY_ID: secret://r2_access_key_id
        LITESTREAM_SECRET_ACCESS_KEY: secret://r2_secret_access_key
        R2_BUCKET: secret://r2_bucket
        R2_ENDPOINT: secret://r2_endpoint
  ```

  Same host path as the app, mounted at `/data` instead of `/app/db` — both containers open the same file, which is the standard sidecar arrangement and is safe under WAL.

  Do **not** add `cache_from`/`cache_to` here. The app service uses the GitHub Actions cache; a second service sharing that cache scope risks cross-contaminating layers, and this image is three lines.

  Do **not** add `x-ports` — the sidecar serves no traffic. Do **not** add `scale`.

- [ ] **Step 2: Add the four secrets**
  Extend the top-level `secrets:` block:

  ```yaml
    r2_access_key_id:
      x-command: printenv R2_ACCESS_KEY_ID
    r2_secret_access_key:
      x-command: printenv R2_SECRET_ACCESS_KEY
    r2_bucket:
      x-command: printenv R2_BUCKET
    r2_endpoint:
      x-command: printenv R2_ENDPOINT
  ```

- [ ] **Step 3: Verify shape programmatically**
  Run a Python `yaml.safe_load` assertion checking: both services present; the sidecar's volume is exactly `/root/linkboard-db:/data`; the secrets set contains all seven names; the sidecar has no `x-ports` and no `scale`.
  Expected: `ok`.

- [ ] **Step 4: Commit**
  `git commit -m "Add litestream sidecar to compose"`

---

## Task 3: Wire the deploy workflow

**Files:**
- Modify: `.github/workflows/deploy.yaml`

- [ ] **Step 1: Add the R2 env vars to the deploy step**
  Extend the existing `env:` block with `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET`, `R2_ENDPOINT`, each from `${{ secrets.* }}`.

- [ ] **Step 2: Deploy both services**
  Drop the trailing service name so the whole file deploys:
  `run: uc --context personal --connect root@${{ vars.SERVER_IP }} deploy -f compose.yaml --yes`

  If Uncloud rejects the no-service form, use two sequential invocations naming `linkboard-app` and `linkboard-litestream`.

- [ ] **Step 3: Cross-check the env contract**
  Every `printenv <VAR>` in `compose.yaml`, plus `${APP_DOMAIN}` from `x-ports`, must appear in the deploy step's `env:` — and nothing extra. Verify with a script parsing both files, as the uncloud migration did, not by eye.
  Expected: demanded == supplied == {`APP_DOMAIN`, `SESSION_SECRET_KEY`, `SENTRY_DSN`, `OPENROUTER_API_KEY`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET`, `R2_ENDPOINT`}.

- [ ] **Step 4: Verify the workflow parses**
  Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/deploy.yaml')); print('ok')"`
  Expected: `ok`.

- [ ] **Step 5: Commit**
  `git commit -m "Deploy litestream sidecar with R2 credentials"`

---

## Task 4: First deploy and replication check

**Files:** none modified.

- [ ] **Step 1: Merge and let the deploy run**
  Confirm Task 0's four secrets exist first — a missing one surfaces as an empty `printenv` and an authentication failure rather than an obvious error.

- [ ] **Step 2: Confirm the sidecar is running**
  `uc --context personal --connect root@<SERVER_IP> ls`
  Expected: both `linkboard-app` and `linkboard-litestream` present and running.

- [ ] **Step 3: Read the sidecar logs**
  `uc --context personal --connect root@<SERVER_IP> logs linkboard-litestream`
  Expected: an initial snapshot followed by periodic sync messages. Authentication errors, `NoSuchBucket`, or bucket-addressing errors point at Task 0's values; a bucket-addressing error specifically is the signal to add `force-path-style: true` to the replica config.

- [ ] **Step 4: Confirm objects exist in R2**
  Check the bucket in the Cloudflare dashboard, or with any S3 client against the endpoint.
  Expected: objects under the `linkboard/` prefix, with a recent timestamp.

- [ ] **Step 5: Confirm replication tracks a real write**
  Add a link in the app, wait past the sync interval, and confirm new objects appear.
  Expected: fresh objects within roughly 10–20 seconds.

---

## Task 5: Document the restore procedure

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Write the backup section**
  Cover: what Litestream replicates and where; the 7-day window; that Layer 2 dailies live in `/root/linkboard-backups`; and the exact restore command, including the point-in-time form.

  The restore command runs from the sidecar image so no local Litestream install is needed:

  ```
  docker run --rm \
    -e LITESTREAM_ACCESS_KEY_ID -e LITESTREAM_SECRET_ACCESS_KEY \
    -e R2_BUCKET -e R2_ENDPOINT \
    -v /root/restore:/out \
    <litestream-image> restore -o /out/linkboard.sqlite /data/linkboard.sqlite
  ```

  Note explicitly that a restore must **never** be written over the live database while the app is running — restore to a scratch path, verify it, stop the app, then swap.

- [ ] **Step 2: Commit**
  `git commit -m "Document backup and restore procedure"`

---

## Task 6: Restore drill — the actual deliverable

**Files:** none modified.

> Everything before this task is setup. Until a restore has been performed and verified, the backup status is unknown.

- [ ] **Step 1: Restore to a scratch path**
  Restore the latest replica state to `/root/restore-drill.sqlite` on the server, using the command documented in Task 5. Do not touch `/root/linkboard-db`.

- [ ] **Step 2: Verify integrity**
  `sqlite3 /root/restore-drill.sqlite "PRAGMA integrity_check; PRAGMA foreign_key_check;"`
  Expected: `ok`, and no foreign-key output (assuming the SQLite hardening plan's Task 3 has run).

- [ ] **Step 3: Compare against the live database**
  Row counts for `user`, `board`, and `link` in the restored copy versus the live file.
  Expected: identical, or the restored copy trailing by at most the last sync interval if a write landed mid-drill.

- [ ] **Step 4: Verify point-in-time restore**
  Restore again with a timestamp targeting a few hours ago, to a different scratch path.
  Expected: it succeeds and yields a plausible earlier state. This is the capability the whole plan exists for — confirm it works rather than assuming it does.

- [ ] **Step 5: Record the result**
  Note the drill date and outcome in this plan's completion summary. Schedule the next drill roughly quarterly.

---

## Task 7: Layer 2 — daily `VACUUM INTO` snapshots

**Files:**
- Create: `ops/linkboard-snapshot.sh`, `ops/linkboard-snapshot.service`, `ops/linkboard-snapshot.timer`

- [ ] **Step 1: Write the snapshot script**
  ```sh
  #!/bin/sh
  set -eu

  SRC=/root/linkboard-db/linkboard.sqlite
  DEST_DIR=/root/linkboard-backups
  STAMP=$(date +%F)
  TMP="$DEST_DIR/.tmp-$$.sqlite"

  mkdir -p "$DEST_DIR"

  # busy_timeout is essential: the sqlite3 CLI defaults it to 0, so VACUUM INTO
  # fails instantly if the app holds a write lock.
  sqlite3 "$SRC" "PRAGMA busy_timeout=10000; VACUUM INTO '$TMP';"

  # Refuse to keep a snapshot that does not verify.
  sqlite3 "$TMP" "PRAGMA integrity_check;" | grep -qx ok

  # mv -f rather than deleting an existing same-day file; VACUUM INTO refuses
  # to write to a path that already exists, hence the temp-then-move.
  mv -f "$TMP" "$DEST_DIR/linkboard-$STAMP.sqlite"
  ```

  The prune line is added separately in Step 4, after its dry run.

- [ ] **Step 2: Write the systemd unit and timer**
  `ops/linkboard-snapshot.service` — `Type=oneshot`, `ExecStart=/usr/local/bin/linkboard-snapshot.sh`.
  `ops/linkboard-snapshot.timer` — `OnCalendar=daily`, `Persistent=true` so a snapshot missed while the machine was down runs on next boot, and `WantedBy=timers.target`.

- [ ] **Step 3: Install and run once on the server**
  Ensure `sqlite3` is present (`apt-get install -y sqlite3`) and confirm its version is at least 3.27 — `VACUUM INTO` does not exist before that. Copy the script to `/usr/local/bin/`, `chmod +x`, install both unit files, `systemctl daemon-reload`, then `systemctl start linkboard-snapshot.service`.
  Expected: one dated file in `/root/linkboard-backups`, roughly 700 KB, passing `integrity_check`.

- [ ] **Step 4: Dry-run the prune, then enable it**
  > This is the destructive step flagged in the Design section. Run it in `-print` mode first and read the output.

  ```
  find /root/linkboard-backups -maxdepth 1 -name 'linkboard-*.sqlite' -mtime +7 -print
  ```

  Expected on a fresh install: **no output** — nothing is 7 days old yet. If anything is listed, confirm each file is genuinely expendable before continuing.

  Only then append the same command with `-delete` in place of `-print` to the end of the script.

- [ ] **Step 5: Enable the timer**
  `systemctl enable --now linkboard-snapshot.timer`, then `systemctl list-timers linkboard-snapshot`.
  Expected: a next-run time roughly 24 hours out.

- [ ] **Step 6: Confirm Litestream ignores the snapshot directory**
  Check the sidecar logs after the first snapshot run.
  Expected: no new errors. Layer 2 writes to `/root/linkboard-backups`, which is outside the `/data` mount, so Litestream should never see those files.

- [ ] **Step 7: Commit**
  `git commit -m "Add daily sqlite snapshot timer"`

---

## Task 8: Final review

**Files:** none modified.

- [ ] **Step 1: Read the complete diff**
  Run: `git diff master...HEAD --stat`
  Expected: five files created (`litestream/` × 2, `ops/` × 3), three modified (`compose.yaml`, `.github/workflows/deploy.yaml`, `README.md`), plus this plan. No changes under `src/` or `resources/`.

- [ ] **Step 2: Confirm both layers are live**
  - Litestream: recent objects in R2, sidecar running, no errors in logs.
  - Layer 2: at least one dated file, timer enabled and scheduled.
  - Task 6's restore drill passed.

- [ ] **Step 3: Report to the user**
  Cover: what is backed up and where, the restore command, the retention window, the date of the restore drill, and anything deferred (`force-path-style`, the two-invocation deploy fallback, whether the prune line was enabled or omitted).

---

## Related

- `docs/plans/2026-08-26-2213-uncloud-migration.md` — established the `/root/linkboard-db` bind mount the sidecar shares.
- `docs/plans/2026-08-27-0010-sqlite-setup-hardening.md` — should land first, so the first replicated snapshot is of a referentially-clean database. Its `busy_timeout=5000` is what Litestream's `busy-timeout: 5s` is matched to.
