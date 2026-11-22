# Program search and comparison

Applicants can search for programs, open their details, and compare programs using the public endpoints used by the browser.

## Sub-features

- `catalog-search` returns matching programs and an empty result for an absent keyword.
- `catalog-detail` shows a program's school, basic information, and data availability.
- `catalog-compare` returns comparison rows for selected programs.

## How to get to it (user POV)

- Enter a keyword in the header's **搜索院校 / 专业 / 关键词** and press Enter to open `/results?keyword=…`.
- Open **查看详情** on a result. Open comparison from **对比与备选** after selecting programs. The applicant API also accepts direct requests.

## Driving it with curl and Playwright

Run doctor. Use the two seeded program IDs. Detail and compare require at least one admissions cutoff row for each program.

```bash
api=http://127.0.0.1:18082/dev-api/app/programs
curl -fsS -G "$api/search" --data-urlencode 'keyword=验证大学' --data-urlencode 'limit=20'
curl -fsS -G "$api/search" --data-urlencode 'keyword=不存在的验证学校'
curl -fsS "$api/900001/detail?estimatedScore=330"
curl -fsS "$api/compare?programIds=900001,900002&estimatedScore=330"
```

- **Search.** Expect JSON `code: 200`, `total: 2`, and IDs 900001 and 900002 in either order. The absent keyword should return `total: 0` and an empty `items` array. In Playwright, fill the header placeholder with **验证大学**, press Enter, and wait for **验证大学** in the results.
- **Detail.** Expect `data.basic.schoolName` to be **验证大学**. In Playwright, click **查看详情** on the matching card and wait for the school heading in the detail panel.
- **Compare.** Expect `data.estimatedScore` to be 330 and `data.items` to contain both requested IDs. To cover the browser comparison from a signed-in fixture, select the two programs in `/favorites`, choose **对比所选**, and confirm both program names appear in the comparison.

## Gotchas

- The baseline seeds program identities without cutoff rows. Record detail and comparison as blocked when those rows are absent. An empty comparison or a detail error cannot pass the journey.
- The search route has a Redis-backed rate limit. Space out repeat runs.
