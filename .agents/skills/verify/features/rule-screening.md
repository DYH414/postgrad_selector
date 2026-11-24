# Rule screening

Applicants choose an estimated score, exam combination, region, and score range before generating a list of candidate programs.

## Sub-features

- `screening-form` loads the defaults and allows changes on the form.
- `screening-login` prompts anonymous users before generation.
- `screening-results` generates and reloads results for a signed-in applicant.

## How to get to it (user POV)

- Open `/` via **首页** or navigate to `/recommend`.
- Choose **开始筛选** to go to **筛选结果** after successful generation. The header also opens `/results`.

## Driving it with Playwright

Run doctor and install Playwright as described in the [verification skill](../SKILL.md).

- **Anonymous form and login prompt.** From the repository root run `.agents/skills/verify/scripts/screening.cjs "$run" > "$run/evidence/screening.log" 2>&1`. It opens both routes, waits for options from the live API, enters 330, and selects **22408**, **验证省**, and **均分+10**. Expect those values in the form and summary. It clicks **开始筛选** and expects **需要登录**. On `/` it dismisses the prompt and checks that the score remains 330. On `/recommend` it follows the login link and checks that the redirect points back to `/recommend`.
- **Signed-in results.** Run the signed-in student journey with the fixture account. Fill the form with 330, **22408**, **验证省**, and **均分+10**. Click **开始筛选**. Expect `POST /dev-api/app/recommendation/generate` to return JSON `code: 200`, both fixture programs, and a recommendation ID. Reload `/results?id=<recommendationId>`. Confirm the same candidate and a successful `GET /dev-api/app/recommendation/result/<recommendationId>`.

For a fresh run, confirm that the anonymous actions sent no generation request and created no recommendation row.

```bash
mariadb --no-defaults --socket="$run/state/mysql.sock" --batch --skip-column-names \
  postgrad_selector -e 'SELECT COUNT(*) FROM recommendation_log' > "$run/evidence/recommendation-log-count.txt"
test "$(cat "$run/evidence/recommendation-log-count.txt")" = 0
```

## Gotchas

- Launch supplies a signing secret, a student account, and both cutoff and average admitted scores. Anonymous and signed-in browser checks use separate contexts.
- The backend supplies its defaults asynchronously. Wait for 300 before changing the score.
- The score-range button opens a tooltip. Select the region first so the tooltip cannot cover its field.
