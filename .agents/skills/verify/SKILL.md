---
name: verify
description: Drive PostgradSelector on Linux. Verify anonymous screening, the signed-in student journey, the admin school workspace, and the AI workspace without a provider key.
---

# Verify PostgradSelector

## Launch

Install JDK 17 or later, Maven 3.9+, Node.js 18+, MariaDB server and client, Redis, Python 3 with `bcrypt`, curl, iproute2, and Chromium. The Linux recipe uses `/usr/sbin/mariadbd`.

Use an absolute path to a private Markdown credentials file outside the checkout, such as `/tmp/codex-postgrad/test-accounts.md`. Give the file mode `0600`. Add a `Student` row with a disposable email and a password of at least six characters. Add an `Admin` row with a disposable username and password. Put the role, account, and password in the first three cells. Omit pipe characters in the values.

From the repository root, run the following commands.

```bash
mvn -B package -DskipTests
npm --prefix user-ui ci
npm --prefix ruoyi-ui ci
export POSTGRAD_TEST_ACCOUNTS=/tmp/codex-postgrad/test-accounts.md
verify=.agents/skills/verify/scripts/run.sh
run=$(mktemp -d /tmp/postgrad-verify.XXXXXXXX)
"$verify" launch "$run"
```

If the Maven mirror in `pom.xml` is unreachable, pass `-s` with local Maven settings that mirror all repositories to Maven Central.

Launch imports the four bootstrap scripts into a disposable database. It seeds **验证大学** in **验证省** with two 22408 programs and 2025 admissions scores. It registers the student through the applicant API, creates a read-only school viewer, and disables CAPTCHA in the disposable database. It generates a signing secret for the local API process. It starts MariaDB on 13306, Redis on 16379, the API on 18080, the admin UI on 18081, and the applicant UI on 18082. The listeners bind to loopback and can run alongside the 8080–8082 developer stack.

## Doctor

```bash
"$verify" doctor "$run"
```

Wait for `READY` before driving the browser. Doctor checks listener ownership, the backend JAR checksum, Redis, and the fixture options through the UI proxy. It also signs in, checks the authenticated applicant identity, and signs out. The transcript records HTTP and JSON status codes.

## Drive

Install Playwright Core outside the checkout and point it at local Chromium.

```bash
browser_tools=$(mktemp -d /tmp/postgrad-browser.XXXXXXXX)
npm install --prefix "$browser_tools" --no-save --no-package-lock playwright-core@1.65.1
export PLAYWRIGHT_MODULE="$browser_tools/node_modules/playwright-core"
export CHROME_BIN=/usr/bin/google-chrome
"$verify" drive "$run"
```

Drive runs doctor, anonymous screening, the signed-in [student journey](features/applicant-account.md), and the [admin school workspace](features/admin-workspace.md). The student saves a profile, screens and searches for programs, opens program details, and compares both shortlisted programs through **我的** and `/favorites`. The script verifies the shortlist is empty after removal, checks the AI feedback without a provider, and verifies the return to the requested page after signing in again. The admin check reads the workspace counts and chart labels, then follows **学校管理** and checks the same school in the catalog.

Use the [feature map](features/README.md) for individual browser and HTTP checks. The applicant UI calls the API through `/dev-api`.

## Evidence

Find the doctor, screening, student, and admin transcripts in `"$run/evidence"`. The browser scripts save screenshots, accessibility snapshots, and HTTP statuses. The admin transcript includes the text drawn on the pie chart. Write checks use a fresh read after the UI action. Keep passwords, signed session tokens, and stream tokens out of evidence.

## Cleanup

Run cleanup after success or failure. Check that the evidence remains.

```bash
"$verify" cleanup "$run"
find "$run/evidence" -maxdepth 1 -type f -print
```

Cleanup checks process start times before sending TERM. It removes `"$run/state"` and preserves `"$run/evidence"`. The optional `"$browser_tools"` directory contains Playwright dependencies.
