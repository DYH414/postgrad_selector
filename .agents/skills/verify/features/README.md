# PostgradSelector verification map

Use this map for user-visible journeys. Start with [launch and doctor](../SKILL.md).

## Baseline preconditions

- Applicant UI at `http://127.0.0.1:18082`, with its API on 18080.
- Disposable MariaDB and Redis instances owned by the same run.
- Synthetic school **验证大学**, region **验证省**, and programs 900001 and 900002.
- A fresh browser context for anonymous journeys.

## Driving conventions

- Use the exact visible Chinese labels for Playwright locators. For HTTP recipes, send requests through `http://127.0.0.1:18082/dev-api`.
- Verify both HTTP status and JSON `code`. The API can return HTTP 200 for an application error.
- Report each entry point you drove. Use a fresh profile or a disposable account with the required permissions for authenticated flows.

## Evidence

Record the entry point and the response or screen it produces in the run's evidence directory. For blocked journeys, record the missing precondition with the action attempted.

## Features

- [Rule screening](rule-screening.md) covers the form, login prompt, and saved results.
- [Program search and comparison](program-catalog.md) covers public search, detail, and comparison.
- [Account, profile, and shortlist](applicant-account.md) covers applicant entry points and persistence.
- [AI workspace and reports](ai-workspace.md) covers draft, chat, and saved reports.
