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

Run doctor. A configured disposable stack and an applicant test account are required for signed-in steps.

- **Account entry.** At `/`, click the button named **登录/注册**. Expect `/login` and the **欢迎回来** heading. Click the tab named **注册** and expect **创建账号**. Open `/register` directly and expect **注册账号**.
- **Save a profile.** Sign in from `/login`, open `/profile`, and click **开始填写** for a new profile. Set **预估总分** to 330 and choose **保存画像**. Reload the page. Confirm the score persists in **考研画像** and the fresh `GET /dev-api/app/profile` response.
- **Add and remove a backup.** Open `/results?keyword=验证大学` with the fixture installed and use the result card's **加入备选** action. Reload `/favorites` and expect **验证大学**. Choose **取消收藏**, then reload and confirm the row is gone and the favorites response omits its ID.

## Gotchas

- The default fixture profile is anonymous. Signed token creation needs a configured signing secret. Never use live accounts for this recipe or store passwords or signed tokens in evidence.
