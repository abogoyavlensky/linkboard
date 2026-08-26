# SQLite Setup Hardening Implementation Plan

> **For agentic workers:** Use executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configure SQLite's per-connection pragmas and the HikariCP pool for what linkboard actually is — a single-node WAL database — and turn on the foreign-key enforcement the schema has always assumed but never had.

**Tech Stack:** Clojure/Integrant/Aero, next.jdbc, hikari-cp 4.0.0, org.xerial/sqlite-jdbc 3.51.2.0, ragtime, SQLite (WAL)

---

## Design

### Why

`src/linkboard/core/db.clj:46` passes only `:jdbc-url` into `cp/make-datasource`, so every hikari-cp default applies. Two of those defaults are wrong for SQLite, and one SQLite default is wrong for this schema.

**The pool is sized for a client/server database.** hikari-cp 4.0.0's defaults are `:minimum-idle 10` and `:maximum-pool-size 10` (verified in `hikari_cp/core.clj` in the 4.0.0 jar). SQLite permits exactly one writer at a time, so ten write-eligible connections buy contention, not throughput. The driver's default `busy_timeout` is 3000 ms (verified via `javap` on `org.sqlite.SQLiteConfig` in sqlite-jdbc 3.51.2.0), so contention surfaces as a 3-second stall and then a `SQLITE_BUSY` exception rather than a clean queue.

**Ten permanently-open connections also suppress checkpointing.** SQLite moves WAL content into the main database file on one of two events: an autocheckpoint when the WAL passes 1000 pages at commit time, or the *last* connection closing. `:minimum-idle 10` means the pool never drops to zero connections while the process lives, so the second path only fires on shutdown. On the old Kamal server this was visible in the file mtimes — `linkboard.sqlite` dated Jul 12 (the last deploy) against a WAL dated Aug 25. Nothing was lost, because the WAL *is* part of the database, but it is the wrong steady state and it is why the migration snapshot had to be taken with `VACUUM INTO`.

**Foreign keys have never been enforced.** SQLite defaults `foreign_keys` to `OFF` for backwards compatibility, and it is a per-connection pragma, so nothing in this project has ever turned it on. Migration `0002` declares two constraints — `board.user_id → user.id ON DELETE CASCADE` and `link.board_id → board.id ON DELETE CASCADE` — and neither has ever fired. The staged production snapshot (`linkboard-2026-08-26.sqlite`) confirms the consequence: **6 orphaned `link` rows** referencing boards that no longer exist. The local dev database has 31.

### Key decisions

**Per-connection pragmas go in the JDBC URL, not in a migration.** Only `journal_mode` is persistent — it lives in the database file header (bytes 18–19, `0202` for WAL), which is why a connection opened with no parameters at all still reports `journal_mode = wal`. `busy_timeout`, `synchronous`, and `foreign_keys` are per-connection and reset to their defaults on every new connection, so a migration structurally cannot set them. The Xerial driver reads all of them from the URL query string; this was verified end-to-end against sqlite-jdbc 3.51.2.0:

```
journal_mode => wal      busy_timeout => 5000    synchronous  => 1
foreign_keys => 1        cache_size   => -64000  temp_store   => 2
```

**Migration `0001` stays exactly as it is.** It is already applied and recorded in ragtime's ledger, and `journal_mode` is persistent, so it is doing no harm. Removing it would mean either editing applied history or adding a no-op migration to undo it — churn for no benefit. Listing `journal_mode=WAL` in the URL as well is idempotent on an existing file and covers the fresh-database case, where the URL applies it on the first connection *before* ragtime runs.

**`maximum-pool-size 1`.** This matches the engine rather than compromising with it — there was never any write parallelism to lose, only contention. It is safe here because every database call is a one-shot `jdbc/execute!` against the datasource: `grep` for `with-transaction`, `get-connection`, and `with-open` across `src/` returns nothing, and there are no background threads or futures touching the database (the only `thread` reference is Sentry's uncaught-exception handler). Connections are therefore held for the duration of a query and no longer — never across the `clj-http` calls in `board/fetch.clj` or the OpenRouter request.

**`foreign_keys=ON` is sequenced separately, after a data cleanup.** SQLite validates foreign keys at write time; it does not retroactively reject rows that already violate a constraint. So enabling the pragma will not error on boot or corrupt anything. But it does change behavior in two ways worth doing deliberately: deletes will start cascading (correctly), and any *future* write referencing a missing parent will fail instead of silently succeeding. Cleaning the 6 known orphans first means the pragma goes live against a database that is actually consistent. Task 3 does the cleanup, Task 4 flips the pragma.

**Orphans are repaired by setting `board_id = NULL`, not by deleting the rows.** The `link` table already declares `board_id INTEGER NULL`, and 135 links in production are legitimately unfiled. Nulling an orphan turns an invisible broken row into a visible unfiled link the user can re-file; deleting it destroys a link they saved. Non-destructive is both safer and more correct here.

**No `cache_size` or `temp_store` tuning.** The database is 696 KB — smaller than SQLite's default 2 MB page cache, so it is already fully resident. Adding a 64 MB native-memory cache per container, on a 4 GB server shared with `readx-app`, `ppnardstg-app`, and a Postgres instance, would be cargo-cult. Worth revisiting only if the database passes roughly 10 MB.

### What changes

| File | Change |
|---|---|
| `resources/config.edn` | Pragmas appended to the `:default` and `:test` `:jdbc-url` values. |
| `src/linkboard/core/db.clj` | Pool sizing merged into the options passed to `cp/make-datasource`. |

Two files, both small. No schema change, no new migration, no dependency change.

### What does NOT change

- `resources/migrations/0001.up.sql` — stays.
- The `:prod` deployment shape. `:default` serves both dev and prod (there is no `:prod` entry for `:jdbc-url`), so one edit covers both.
- `compose.yaml`, the Dockerfile, and the workflows.

### Known caveats — not fixed here

- **Nothing in `src/` uses `with-transaction`.** Multi-statement operations — creating a board and its first link, say — are not atomic, and a crash between statements leaves partial state. This is a real correctness gap, but it is a code-structure question rather than a SQLite-configuration one, and mixing it in would make this diff much harder to reason about.
- **HikariCP's `connection-timeout` stays at its 30-second default.** With a pool of 1, a request that somehow held the connection longer than that would fail its neighbours. Given that no code path holds a connection across I/O, this is theoretical; left alone deliberately rather than tuned blind.
- **The orphan cleanup is a point-in-time fix.** Until `foreign_keys=ON` ships in Task 4, new orphans can still be created by deleting a board. Tasks 3 and 4 should land in the same deploy window.

### Verification strategy

- `bb fmt-check`, `bb lint`, `bb test` stay green. The test profile gets `foreign_keys=ON` too, so the suite becomes the regression gate for FK violations rather than silently tolerating them.
- Fresh-database migration under enforcement is already known to work: applying all 8 migrations to an empty file with `PRAGMA foreign_keys=ON` yields 2 users, 32 boards, 157 links and an empty `foreign_key_check`. Migration `0004`'s `user_id = 2` references the user that migration `0003` inserts, so the seed data is internally consistent.
- Pragmas are asserted at runtime against a real pooled connection, not read off the URL string.
- The production repair is verified against a **copy** of the snapshot before it is run against the live database.

---

## File Structure

**Modify:**
- `resources/config.edn` — `:jdbc-url` pragma query strings.
- `src/linkboard/core/db.clj` — `:maximum-pool-size` / `:minimum-idle`.

**Create:** none. **Delete:** none.

---

## Task 1: Pragmas and pool sizing (excluding foreign keys)

**Files:**
- Modify: `resources/config.edn`
- Modify: `src/linkboard/core/db.clj`

- [ ] **Step 1: Add pragmas to the `:jdbc-url` values**
  In `resources/config.edn`, the `:linkboard.core.db/db` component's `:jdbc-url` `#profile` map has `:default` and `:test` keys. Append a query string to `:default` only in this task — `foreign_keys` is deliberately held back to Task 4:

  ```clojure
  :jdbc-url #profile {:default "jdbc:sqlite:db/linkboard.sqlite?journal_mode=WAL&busy_timeout=5000&synchronous=NORMAL"
                      :test "jdbc:sqlite::memory:"}
  ```

  Keep the existing indentation — the `#profile` map values align under the opening brace.

- [ ] **Step 2: Set the pool size**
  In `src/linkboard/core/db.clj`, the `::db` `init-key` currently reads `(cp/make-datasource options)`. Merge pool sizing *underneath* the caller's options so config keeps the last word:

  ```clojure
  (let [datasource (cp/make-datasource (merge {:maximum-pool-size 1
                                               :minimum-idle 1}
                                              options))]
  ```

  Do not touch the `ragtime-repl/migrate` call or `halt-key!`.

- [ ] **Step 3: Confirm the pragmas actually reach a pooled connection**
  Reading the URL back proves nothing — assert against a live connection from the pool. Run:

  ```
  clj -M -e '(require (quote [linkboard.core.db :as db]) (quote [next.jdbc :as jdbc]) (quote [integrant.core :as ig]))
             (let [ds (ig/init-key :linkboard.core.db/db {:jdbc-url "jdbc:sqlite:db/linkboard.sqlite?journal_mode=WAL&busy_timeout=5000&synchronous=NORMAL"})]
               (doseq [p ["journal_mode" "busy_timeout" "synchronous"]]
                 (println p (val (first (jdbc/execute-one! ds [(str "PRAGMA " p)])))))
               (ig/halt-key! :linkboard.core.db/db ds))'
  ```

  Expected: `journal_mode wal`, `busy_timeout 5000`, `synchronous 1`.

  Use no alias — `:dev` puts `dev/user.clj` on the path, which requires `eftest.runner` and fails to load.

- [ ] **Step 4: Run the check suite**
  Run: `bb fmt-check && bb lint && bb test`
  Expected: all PASS. Etaoin browser tests may error if Chrome is unavailable on this host — that is environmental and CI is the gate (see the uncloud plan's completion notes).

- [ ] **Step 5: Commit**
  `git commit -m "Configure SQLite pragmas and size the pool for single-writer access"`

---

## Task 2: Confirm the pool change under concurrent load

**Files:** none modified.

- [ ] **Step 1: Establish that concurrent writes no longer contend**
  Write a scratch script that initialises the `::db` component against a scratch database, then fires ~50 concurrent inserts across `pmap` or a fixed thread pool, and reports failures.
  Expected: zero `SQLITE_BUSY` / `database is locked` exceptions, and a row count exactly equal to the number of inserts attempted.

  The point is not throughput — it is that a pool of 1 queues rather than throwing. If this test is run against the *old* pool of 10 for comparison, expect it to be flaky rather than reliably failing; contention is timing-dependent, which is exactly what makes it a bad default.

- [ ] **Step 2: No commit** — nothing changed.

---

## Task 3: Repair orphaned rows in the production database

**Files:** none in the repo. This task operates on the live database on the Uncloud server.

> This is the one task that mutates production data. It is non-destructive by construction — it sets `board_id` to `NULL` and deletes nothing — but it must still be rehearsed on a copy first.

- [ ] **Step 1: Take a fresh snapshot before touching anything**
  On the Uncloud server:

  ```
  sqlite3 /root/linkboard-db/linkboard.sqlite \
    "PRAGMA busy_timeout=10000; VACUUM INTO '/root/linkboard-pre-fk-$(date +%F).sqlite';"
  ```

  The `busy_timeout` matters: the `sqlite3` CLI defaults it to `0`, so `VACUUM INTO` against a live database fails immediately the moment the app holds a write lock.

  Expected: a new file, roughly 700 KB. Verify with `PRAGMA integrity_check` (expect `ok`) before continuing.

- [ ] **Step 2: Enumerate the orphans**
  Run against the snapshot, not the live file:

  ```
  sqlite3 -header -column /root/linkboard-pre-fk-$(date +%F).sqlite \
    "SELECT id, board_id, user_id, substr(title,1,40) AS title, date(created_at) AS created
       FROM link
      WHERE board_id IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM board b WHERE b.id = link.board_id);"
  ```

  Expected: 6 rows as of the 2026-08-26 snapshot. If the count differs, stop and report — the live database has drifted since, and the number should be understood before proceeding.

  Note the distinction from `PRAGMA foreign_key_check`, whose fourth output column is the constraint *index* (`0`), not a `board_id` value. Use the query above to see the real ids.

- [ ] **Step 3: Rehearse the repair on the snapshot**
  ```
  sqlite3 /root/linkboard-pre-fk-$(date +%F).sqlite \
    "UPDATE link SET board_id = NULL
      WHERE board_id IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM board b WHERE b.id = link.board_id);
     PRAGMA foreign_key_check;"
  ```

  Expected: the `UPDATE` reports the same number of rows as Step 2, and `foreign_key_check` returns nothing. Confirm the total link count is unchanged — nothing should be deleted.

- [ ] **Step 4: Apply to the live database**
  Same `UPDATE` against `/root/linkboard-db/linkboard.sqlite`, with `PRAGMA busy_timeout=10000;` prefixed. Then re-run `PRAGMA foreign_key_check` against the live file.
  Expected: empty output.

- [ ] **Step 5: Confirm in the app**
  Load the app and check that the previously-orphaned links now appear as unfiled. They were invisible or inconsistently joined before; they should now be re-fileable.

- [ ] **Step 6: No commit** — no repository changes.

---

## Task 4: Enable foreign-key enforcement

**Files:**
- Modify: `resources/config.edn`

> Do not start this task until Task 3 reports an empty `foreign_key_check` against the live database.

- [ ] **Step 1: Add `foreign_keys=ON` to both profiles**
  ```clojure
  :jdbc-url #profile {:default "jdbc:sqlite:db/linkboard.sqlite?journal_mode=WAL&busy_timeout=5000&synchronous=NORMAL&foreign_keys=ON"
                      :test "jdbc:sqlite::memory:?foreign_keys=ON"}
  ```

  The `:test` entry is not optional. In-memory databases cannot use WAL — `journal_mode` there is always `memory`, so that pragma is correctly absent — but `foreign_keys` applies normally, and without it the suite happily inserts orphan rows and the gap reappears silently.

- [ ] **Step 2: Verify enforcement on a pooled connection**
  Re-run the Step 3 probe from Task 1 with `foreign_keys` added to the list.
  Expected: `foreign_keys 1`.

- [ ] **Step 3: Verify a fresh database still migrates under enforcement**
  Apply all 8 migrations to an empty scratch file with `PRAGMA foreign_keys=ON`, then check the result.
  Expected: 2 users (ids 1 and 2), 32 boards, 157 links, and an empty `foreign_key_check`. Migration `0004` seeds boards with `user_id = 2`, which is the user migration `0003` creates — the seed data is consistent, and this step exists to keep it that way.

- [ ] **Step 4: Verify cascade deletes now fire**
  In a scratch database: create a user, a board, and two links on that board; delete the board; confirm the links are gone.
  Expected: 0 links remaining. Before this change the links would have survived as orphans — that is the bug this task closes.

- [ ] **Step 5: Run the check suite**
  Run: `bb fmt-check && bb lint && bb test`
  Expected: all PASS. A new failure here is a genuine finding — it means a test was relying on unenforced referential integrity.

- [ ] **Step 6: Commit**
  `git commit -m "Enforce SQLite foreign keys in all profiles"`

---

## Task 5: Final review

**Files:** none modified.

- [ ] **Step 1: Read the complete diff**
  Run: `git diff master...HEAD`
  Expected: exactly two source files changed (`resources/config.edn`, `src/linkboard/core/db.clj`) plus this plan. No migration added or edited. No dependency changes.

- [ ] **Step 2: Confirm the checkpoint behaviour changed**
  After the deploy, note the mtime of `/root/linkboard-db/linkboard.sqlite` and compare it a day later.
  Expected: it advances rather than freezing at the deploy timestamp. With `minimum-idle 1` the pool still holds a connection open, so the close-time checkpoint remains deploy-scoped; the WAL autocheckpoint at 1000 pages is what should now show up. If the Litestream plan lands first, Litestream takes over checkpointing entirely and this check is moot — note which is the case rather than treating either as a failure.

- [ ] **Step 3: Report to the user**
  Summarise: what shipped, the orphan count actually found and repaired in production, and the two caveats left open (no `with-transaction` anywhere; `connection-timeout` untouched).

---

## Related

- `docs/plans/2026-08-26-2213-uncloud-migration.md` — established the `/root/linkboard-db` bind mount this plan's Task 3 operates on.
- `docs/plans/2026-08-27-0011-litestream-backups.md` — depends on this plan only loosely, but should land *after* Task 3, so that the first replicated snapshot is of a referentially-clean database.
