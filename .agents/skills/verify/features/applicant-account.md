# Account, profile, and shortlist

Applicants can create an account, save their exam profile, and keep a shortlist across browser sessions.

## Sub-features

- `account-entry` opens login and registration.
- `profile-save` persists exam settings.
- `shortlist-save` adds or removes a program and reloads the shortlist.

## How to get to it (user POV)

- Choose **登录/注册** in the header, or open `/login` or `/register`.
- Choose **我的** or open `/profile`. View **我的备选方案** there or open `/favorites`.
- Add a program to the shortlist from a results card.

## Driving it with Playwright

Run `run.sh drive "$run"` as described in the [verification skill](../SKILL.md). The signed-in browser script uses the student account registered during launch.

1. Click **登录/注册** on the home page and sign in. Expect **退出** in the header.
2. Choose **我的**. Save a score of 330 and **22408** in **考研画像**. Reload and confirm the saved values through `GET /dev-api/app/profile`.
3. Generate and reload [screening results](rule-screening.md). Search from the header while those results are open. An absent keyword shows **暂无匹配结果**. **验证大学** returns both programs.
4. Expand the two directions and add each to **备选**. Open `/favorites` and confirm both through the list endpoint. Select both rows, choose **对比所选**, and check both majors and the score of 330 in the comparison.
5. Open **详情** from a shortlist row. Confirm the **备选专业详情** drawer shows the program.
6. Choose **取消收藏** for both programs and reload. Confirm the list API returns no entries and the comparison is gone.
7. Click **我的** in the header. Expect `/profile`, the **我的** heading, and the saved profile. The applicant identity endpoint is `/dev-api/app/auth/me`.
8. Open [AI 推荐](ai-workspace.md) and run the no-key check.
9. Return to `/favorites`, choose **退出**, and sign in again. Confirm the logout request carries the session and the returning student lands on `/favorites`.
