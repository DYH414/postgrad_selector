# AI workspace and reports

Applicants can generate a draft, chat about candidates, and reopen a saved report.

## Sub-features

- `ai-entry` routes anonymous users to sign-in before opening the workspace.
- `ai-draft` generates a draft and restores it after reload.
- `ai-report` opens a generated report from history.

## How to get to it (user POV)

- Choose **AI 推荐** in the header or open `/ai-recommend`.
- Open **AI 推荐记录** within `/profile` or go to `/ai-history`.
- Open a report from history or follow its `/ai-report-v2/<id>` URL. Links under `/ai-report/<id>` redirect there.

## Driving it with Playwright

Run doctor. For generation use a configured disposable stack, a signed-in applicant with a complete profile, and admissions data that match the profile.

- **Anonymous entry.** At `/`, click the button named **AI 推荐**, then click **去登录/注册** in **需要登录**. Expect `/login?redirect=/ai-recommend`.
- **Without provider credentials.** Use the scripted student journey. Open **AI 推荐**, wait for **AI 助手**, and click **生成新的候选草稿**. Expect JSON code 503 and **AI 服务未配置，请联系管理员**. Send a chat message and expect the same message as a stream error. The workspace stays usable after both actions.
- **Draft and chat.** At `/ai-recommend`, click **生成新的候选草稿**. Capture the start response and event stream from `POST /dev-api/app/ai-recommend-v2/draft/generate/start` and `GET /dev-api/app/ai-recommend-v2/draft/generate/stream`. Expect candidate cards and a completed generation. Reload and confirm that `GET /dev-api/app/ai-recommend-v2/draft` restores the same candidates. Enter a question about one candidate in **输入消息，回车发送**, click **发送**, and capture the streamed response alongside the rendered answer.
- **Report.** Click **生成报告** from the draft. From `/ai-history` and the report card under `/profile`, open the same report. Confirm the report ID and candidate content agree across both entry points and reload.

## Gotchas

- The isolated launch provides an authenticated student session. Provider generation, chat, reports, and RAG retrieval require a configured provider and an initialized RAG index.
