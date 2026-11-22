const assert = require('node:assert/strict')
const { readFile, writeFile } = require('node:fs/promises')
const { join } = require('node:path')
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright-core')

const [base, evidence] = process.argv.slice(2)
const workspacePath = '/postgrad/workspace'
const schoolPath = '/postgrad/basic/school'
const categories = ['A 完整', 'B 基本完整', 'C 缺关键项', 'D 严重缺失']

async function verify() {
  const accounts = await readFile(process.env.POSTGRAD_TEST_ACCOUNTS, 'utf8')
  const row = accounts.split('\n').find(line => line.startsWith('| Admin |'))
  assert.ok(row, 'Expected an Admin row in POSTGRAD_TEST_ACCOUNTS')
  const [, , account, password] = row.split('|').map(cell => cell.trim())
  const browser = await chromium.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome' })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } })
    page.setDefaultTimeout(20000)
    await page.addInitScript(() => {
      const fillText = CanvasRenderingContext2D.prototype.fillText
      window.qualityChartText = new Set()
      CanvasRenderingContext2D.prototype.fillText = function (value, ...args) {
        if (this.canvas.closest('.viz-grid .viz-panel:first-child')) {
          window.qualityChartText.add(String(value))
        }
        return fillText.call(this, value, ...args)
      }
    })

    async function api(path, method = 'GET') {
      const response = await page.waitForResponse(res => new URL(res.url()).pathname === '/dev-api' + path &&
        res.request().method() === method)
      const body = await response.json()
      console.log(`${method} ${path} HTTP ${response.status()} JSON ${body.code}`)
      assert.equal(response.status(), 200)
      assert.equal(body.code, 200, path + ' did not succeed')
      return body
    }

    async function screen(name) {
      await page.screenshot({ path: join(evidence, name + '.png') })
      await writeFile(join(evidence, name + '.aria.txt'), await page.locator('body').ariaSnapshot())
    }

    await Promise.all([
      api('/captchaImage').then(body => assert.equal(body.captchaEnabled, false)),
      page.goto(base + '/login?redirect=' + encodeURIComponent(workspacePath)),
    ])
    await page.getByPlaceholder('账号').fill(account)
    await page.getByPlaceholder('密码').fill(password)
    const [, stats, schools] = await Promise.all([
      api('/login', 'POST'),
      api('/postgrad/workspace/stats'),
      api('/postgrad/workspace/schools'),
      page.getByRole('button', { name: /登\s*录/ }).click(),
    ])
    await page.waitForURL(url => url.pathname === workspacePath)
    await page.getByRole('heading', { name: '学校数据工作台', exact: true }).waitFor()
    const schoolCount = page.locator('.kpi-item').filter({ hasText: '学校数' }).locator('.kpi-item__value')
    const schoolPanel = page.locator('.school-panel')
    const listCount = schoolPanel.locator('.panel-heading > div > span')
    await schoolPanel.locator('.school-list .el-loading-mask').waitFor({ state: 'hidden' })
    await page.waitForFunction(labels => labels.every(label =>
      [...window.qualityChartText].some(value => value.includes(label))), categories)
    const chart = await page.evaluate(() => [...window.qualityChartText])
    const browserCounts = `学校数 ${await schoolCount.innerText()} | 学校列表 ${await listCount.innerText()}`
    const browserSchools = await schoolPanel.locator('.school-item strong').allTextContents()
    console.log(browserCounts)
    console.log('Pie chart canvas text ' + JSON.stringify(chart))
    await screen('admin-workspace')

    const schoolButton = schoolPanel.locator('button').filter({ hasText: '学校管理' })
    await schoolButton.click()
    await page.waitForURL(url => url.pathname === '/404' || url.pathname === schoolPath)
    const target = new URL(page.url())
    console.log('学校管理 → ' + target.pathname + target.search)
    assert.equal(target.pathname + target.search, schoolPath)
    const [catalog] = await Promise.all([api('/postgrad/school/list'), page.reload()])
    await page.getByRole('cell', { name: '验证大学', exact: true }).waitFor()
    await screen('admin-school-catalog')
    assert.deepEqual(catalog.rows.map(({ id, name }) => ({ id, name })), [{ id: 900001, name: '验证大学' }])
    assert.equal(stats.data.schoolCount, catalog.total)
    assert.equal(stats.data.programCount, 2)
    assert.deepEqual(schools.data.map(({ id, name }) => ({ id, name })), catalog.rows.map(({ id, name }) => ({ id, name })))
    assert.equal(browserCounts, '学校数 1 | 学校列表 1 所')
    assert.deepEqual(browserSchools, ['验证大学'])
    assert.ok(chart.every(label => !label.includes('undefined%')))
    console.log('PASS workspace and school catalog both show 验证大学 (900001)')
  } catch (error) {
    throw new Error(error.message.replaceAll(account, '[redacted]').replaceAll(password, '[redacted]'))
  } finally {
    await browser.close()
  }
}

verify().catch(error => { console.error(error.message); process.exitCode = 1 })
