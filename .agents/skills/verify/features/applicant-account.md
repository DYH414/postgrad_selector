# Account, profile, and shortlist

Applicants can create an account, save their exam profile, and keep a shortlist across browser sessions.

## Sub-features

- `account-entry` opens login and registration.
- `profile-save` persists exam settings.
- `shortlist-save` adds or removes a program and reloads the shortlist.
- `shortlist-compare` compares two shortlisted programs using the saved profile score.

## How to get to it (user POV)

- Choose **登录/注册** in the header, or open `/login` or `/register`.
- Choose **我的** or open `/profile`. Select **备选学校** to view **我的备选方案**. The separate shortlist is at `/favorites`.
- Add a program to the shortlist from a results card.

## Driving it with Playwright

Run `run.sh drive "$run"` as described in the [verification skill](../SKILL.md). The signed-in browser script uses the student account registered during launch.

1. Click **登录/注册** on the home page and sign in. Expect **退出** in the header.
2. Choose **我的**. Save a score of 330 and **22408** in **考研画像**. Reload and confirm the saved values through `GET /dev-api/app/profile`.
3. Generate and reload [screening results](rule-screening.md). Search from the header while those results are open. An absent keyword shows **暂无匹配结果**. **验证大学** returns both programs.
4. Expand the two directions and add **验证计算机科学** and **验证计算机技术** to **备选**. Open the home page in a new tab so the comparison has no screening result cached by the old tab.
5. Choose **我的**, then **备选学校**. Select both programs and choose **对比所选**. Confirm the comparison shows both names, cutoff scores 310 and 315, and gaps of +15 and +10 against the saved score of 330.
6. Clear the comparison, then open `/favorites` and confirm both programs through the list endpoint. Select both rows and verify **对比所选** from this page too. Open **详情** from a shortlist row and confirm the **备选专业详情** drawer shows the program.
7. Choose **取消收藏** for both programs and reload. Confirm the list API returns no entries and the comparison is gone. Return to **我的**, check the saved profile, and confirm **备选学校** shows **暂无备选方案**.
8. Open [AI 推荐](ai-workspace.md) and run the no-key check.
9. Return to `/favorites`, choose **退出**, and sign in again. Confirm the logout request carries the session and the returning student lands on `/favorites`.
