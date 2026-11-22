---
name: verify
description: Drive the PostgradSelector applicant web UI and HTTP API on Linux. Use to verify rule screening, program search and comparison, account and shortlist journeys, and AI workspace entry points.
---

# Verify PostgradSelector

## Launch

From the repository root, build the Java 17 backend and install the Vue 3 applicant UI dependencies. Use JDK 17 or later, Maven 3.9+, and Node.js 18+. Install MariaDB server and client, Redis, Python 3, curl, and iproute2. The Linux recipe uses MariaDB's `/usr/sbin/mariadbd`.

```bash
mvn -B package -DskipTests
npm --prefix user-ui ci
verify=.agents/skills/verify/scripts/run.sh
run=$(mktemp -d /tmp/postgrad-verify.XXXXXXXX)
"$verify" launch "$run"
```

If the Maven mirror in `pom.xml` is unreachable, run with `-s` and a local Maven settings file that mirrors all repositories to Maven Central.

Launch imports the four bootstrap scripts in order into a new database. It adds the synthetic school **验证大学** in **验证省** and two programs. It starts MariaDB on 13306, Redis on 16379, the API on 18080, and the applicant UI on 18082. All listeners bind to loopback. Launch refuses occupied ports. The developer server on 8082 can run alongside it.

Wait for `READY http://127.0.0.1:18082`. Readiness requires the API startup runner to complete and the UI proxy to return the seeded region. The API uses lazy initialization for the anonymous journeys and receives an empty JWT signing configuration. Authenticated journeys and live AI/RAG require their own configured test environment.

## Doctor

```bash
"$verify" doctor "$run"
```

Doctor checks that the recorded processes own all four ports, the JAR hash matches launch, Redis answers, and the applicant proxy returns the fixture and exam combinations in a successful options response. Drive the instance only after doctor prints `READY`.

## Drive

Choose a recipe in the [feature map](features/README.md). For the scripted browser recipe, install Playwright Core outside the checkout and point it at a local Chromium binary.

```bash
browser_tools=$(mktemp -d /tmp/postgrad-browser.XXXXXXXX)
npm install --prefix "$browser_tools" --no-save --no-package-lock playwright-core@1.65.1
export PLAYWRIGHT_MODULE="$browser_tools/node_modules/playwright-core"
export CHROME_BIN=/usr/bin/google-chrome
```

The [screening form](features/rule-screening.md) recipe exercises both browser routes. The HTTP paths in the map are the same API paths the applicant UI calls through `/dev-api`.

## Evidence

Keep command output and exit status, browser screenshots and accessibility snapshots, and HTTP status and response bodies under `"$run/evidence"`. Record the feature ID, entry point, action, and resulting state. Capture successful writes through a fresh read. Report a missing precondition with the attempted entry point and leave the journey unverified. Use the real UI and API. The SQL fixture supplies data through the database.

## Cleanup

Run cleanup after success or failure. Check that the artifacts still exist.

```bash
"$verify" cleanup "$run"
find "$run/evidence" -maxdepth 1 -type f -print
```

Cleanup sends TERM to the recorded processes after checking their start times. It removes `"$run/state"` and preserves `"$run/evidence"`. The optional `"$browser_tools"` directory contains only Playwright dependencies and can be deleted after the browser closes.

## Helpers

- `scripts/run.sh {launch|doctor|cleanup} "$run"` manages the isolated stack.
- `scripts/screening.cjs "$run"` drives the browser and saves evidence. See the screening recipe for the transcript command.
- Launch runs `scripts/vite.mjs` against `user-ui` and the run's cache directory. Launch imports `scripts/fixtures.sql` after the repository schema scripts.
