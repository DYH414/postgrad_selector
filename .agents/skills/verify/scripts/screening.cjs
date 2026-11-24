#!/usr/bin/env node
const assert = require('node:assert/strict')
const { writeFile } = require('node:fs/promises')
const { join, resolve } = require('node:path')
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright-core')

const run = process.argv[2]
assert.match(run || '', /^\/tmp\/postgrad-verify\.[a-z0-9]{8}$/i)
const evidence = resolve(run, 'evidence')
const base = 'http://127.0.0.1:18082'

async function verify() {
  const browser = await chromium.launch({
    executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome',
  })
  try {
    for (const [route, label] of [['/', 'home'], ['/recommend', 'recommend']]) {
      const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } })
      const generationRequests = []
      page.on('request', request => {
        if (new URL(request.url()).pathname.endsWith('/app/recommendation/generate')) {
          generationRequests.push(request.url())
        }
      })

      const optionsRequest = page.waitForResponse(response =>
        new URL(response.url()).pathname === '/dev-api/app/recommendation/options')
      const document = await page.goto(base + route)
      const optionsResponse = await optionsRequest
      const options = await optionsResponse.json()
      await writeFile(join(evidence, label + '-options.json'), JSON.stringify({
        url: optionsResponse.url(), method: optionsResponse.request().method(),
        status: optionsResponse.status(), body: options,
      }, null, 2) + '\n')
      assert.equal(document.status(), 200)
      assert.equal(optionsResponse.status(), 200)
      assert.equal(options.code, 200)
      assert.deepEqual(options.data.regions, ['验证省'])

      const score = page.getByPlaceholder('请输入你的预计初试总分')
      const exam = page.getByRole('button', { name: /^22408/ })
      const range = page.getByRole('button', { name: '均分+10', exact: true })
      const region = page.locator('.recommend-panel .el-select')
      const submit = page.getByRole('button', { name: '开始筛选' })
      const metrics = page.locator('.hero-metrics')
      const heading = page.getByRole('heading', { name: /数据，快速圈定目标院校/ })
      await heading.waitFor()
      await page.waitForFunction(() =>
        document.querySelector('input[placeholder="请输入你的预计初试总分"]').value === '300')
      await score.fill('330')
      await exam.click()
      await region.click()
      await page.getByRole('option', { name: '验证省' }).click()
      await score.click()
      await range.click()
      await heading.hover()
      const summary = await metrics.innerText()
      for (const value of ['330', '22408', '+10']) assert.ok(summary.includes(value), summary)
      assert.match(await page.locator('.recommend-panel').innerText(), /验证省/)
      await page.screenshot({ path: join(evidence, label + '-filters.png') })
      await writeFile(join(evidence, label + '-filters.aria.txt'), await page.locator('main').ariaSnapshot())

      await submit.click()
      const prompt = page.getByRole('dialog', { name: '需要登录' })
      await prompt.waitFor()
      await writeFile(join(evidence, label + '-login-gate.aria.txt'), await prompt.ariaSnapshot())
      await page.screenshot({ path: join(evidence, label + '-login-gate.png') })
      if (route === '/') {
        await prompt.getByRole('button', { name: '暂不登录' }).click()
        await prompt.waitFor({ state: 'hidden' })
        assert.equal(new URL(page.url()).pathname, route)
        assert.equal(await score.inputValue(), '330')
      } else {
        await prompt.getByRole('button', { name: '去登录/注册' }).click()
        await page.getByRole('heading', { name: '欢迎回来' }).waitFor()
        const loginUrl = new URL(page.url())
        assert.equal(loginUrl.pathname, '/login')
        assert.equal(loginUrl.searchParams.get('redirect'), route)
        await writeFile(join(evidence, label + '-login.aria.txt'), await page.locator('body').ariaSnapshot())
      }
      assert.deepEqual(generationRequests, [])
      console.log(`PASS ${route} → GET options 200 → 330 / 22408 / +10 / 验证省 → ${page.url()} (no generation request)`)
      await page.close()
    }
  } finally {
    await browser.close()
  }
}

verify().catch(error => {
  console.error(error)
  process.exitCode = 1
})
