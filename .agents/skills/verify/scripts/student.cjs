#!/usr/bin/env node
const assert = require('node:assert/strict')
const { writeFile } = require('node:fs/promises')
const { join } = require('node:path')
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright-core')

const evidence = process.argv[2]
const { readStudent } = require('./student-account.cjs')
const { checkCompareHeaders } = require('./compare-headers.cjs')
const { repeatCheckedChange, readShortlistSelection, checkShortlistPair } = require('./shortlist-selection.cjs')
const base = process.env.POSTGRAD_VERIFY_URL || 'http://127.0.0.1:18082'
const programs = ['验证计算机科学', '验证计算机技术']
const ids = [900001, 900002]
const compareHeaders = programs.map(name => ['验证大学', '验证计算机学院', name, '985 211 双一流'])
const profilePath = '/app/profile'

async function verify() {
  const { account, password } = await readStudent()
  const browser = await chromium.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome' })
  const context = await browser.newContext({ viewport: { width: 1440, height: 1100 } })
  const errors = []

  async function openTab() {
    const tab = await context.newPage()
    tab.setDefaultTimeout(12000)
    tab.on('pageerror', error => errors.push(error.message))
    return tab
  }

  let page = await openTab()

  const heading = name => page.getByRole('heading', { name, exact: true })
  const button = name => page.getByRole('button', { name, exact: true })
  const nav = name => page.getByRole('navigation').getByRole('button', { name, exact: true })

  async function api(label, method, path, action, expectedCode = 200) {
    const [response] = await Promise.all([
      page.waitForResponse(res => res.request().method() === method &&
        new URL(res.url()).pathname === '/dev-api' + path, { timeout: 12000 }),
      action(),
    ])
    const body = response.headers()['content-type']?.includes('application/json') ? await response.json() : {}
    const evidenceBody = { ...body }
    delete evidenceBody.token
    if (path.endsWith('/generate/start')) {
      delete evidenceBody.data?.streamToken
    }
    await writeFile(join(evidence, label + '.json'), JSON.stringify({
      method, path: new URL(response.url()).pathname + new URL(response.url()).search,
      status: response.status(), body: evidenceBody,
    }, null, 2) + '\n')
    console.log(`${label} ${method} ${path} HTTP ${response.status()} JSON ${body.code}`)
    assert.equal(response.status(), 200)
    assert.equal(body.code, expectedCode, body.msg)
    return body
  }

  async function screen(label) {
    await page.screenshot({ path: join(evidence, label + '.png') })
    await writeFile(join(evidence, label + '.aria.txt'), await page.locator('body').ariaSnapshot())
  }

  async function checkRoute(path) {
    await page.waitForURL(url => url.pathname === path)
  }

  async function signIn(label) {
    await heading('欢迎回来').waitFor()
    try {
      await page.getByPlaceholder('请输入手机号或邮箱').fill(account)
      await page.getByPlaceholder('请输入密码').fill(password)
      await api(label, 'POST', '/app/auth/login', () => button('登录').click())
    } catch (error) {
      throw new Error(error.message.replaceAll(account, '[redacted]').replaceAll(password, '[redacted]'))
    }
  }

  try {
    await page.goto(base + '/')
    await button('登录/注册').click()
    await signIn('student-login')
    await button('退出').waitFor()
    await nav('我的').click()
    await checkRoute('/profile')
    await heading('我的').waitFor()
    await page.getByRole('button', { name: /^(填写画像|编辑画像)$/ }).click()
    await page.getByRole('spinbutton').fill('330')
    await page.getByText('22408（数二 + 英二）', { exact: true }).click()
    await api('profile-save', 'POST', profilePath, () => button('保存画像').click())
    const profile = await api('profile-read', 'GET', profilePath, () => page.reload())
    assert.equal(profile.data.estimatedScore, 330)
    assert.equal(profile.data.examCombo, '22408')
    await page.getByText('330 分', { exact: true }).waitFor()
    await screen('student-profile-saved')

    const options = await api('student-options', 'GET', '/app/recommendation/options', () =>
      page.getByRole('link', { name: '首页', exact: true }).click())
    const score = page.getByPlaceholder('请输入你的预计初试总分')
    await page.waitForFunction(expected =>
      document.querySelector('input[placeholder="请输入你的预计初试总分"]')?.value === expected,
    String(options.data.defaultProfile.estimatedScore))
    await score.fill('330')
    await page.getByRole('button', { name: /^22408/ }).click()
    await page.locator('.recommend-panel .el-select').click()
    await page.getByRole('option', { name: '验证省' }).click()
    await score.click()
    await button('均分+10').click()
    await screen('student-form')
    const generation = await api('student-generate', 'POST', '/app/recommendation/generate', () =>
      button('开始筛选').click())
    await checkRoute('/results')
    await heading('筛选结果').waitFor()
    const resultPath = '/app/recommendation/result/' + generation.data.recommendationId
    assert.equal(new URL(page.url()).searchParams.get('id'), String(generation.data.recommendationId))
    await button('共 2 个符合方向').click()
    for (const name of programs) await page.locator('.direction-row').filter({ hasText: name }).waitFor()
    await api('student-result-reload', 'GET', resultPath, () => page.reload())
    await button('共 2 个符合方向').click()
    for (const name of programs) await page.locator('.direction-row').filter({ hasText: name }).waitFor()
    await screen('student-results')

    const search = page.getByPlaceholder('搜索院校 / 专业 / 关键词')
    await search.fill('不存在的验证学校')
    const noMatches = await api('student-catalog-empty', 'GET', '/app/programs/search', () => search.press('Enter'))
    assert.deepEqual(noMatches.data.items, [])
    await page.getByText('暂无匹配结果', { exact: true }).waitFor()
    await screen('student-catalog-empty')
    await page.getByPlaceholder('学校/专业/学院名称').fill(programs[0])
    await page.getByPlaceholder('请输入分数').fill('330')
    await page.locator('.filter-sidebar .el-select').first().click()
    await page.getByRole('option', { name: /^22408/ }).click()
    const fromCatalog = await api('student-catalog-refine', 'POST', '/app/recommendation/generate', () =>
      button('应用筛选').click())
    await page.waitForURL(url => url.searchParams.get('id') === String(fromCatalog.data.recommendationId))
    assert.equal(new URL(page.url()).searchParams.has('keyword'), false)
    await page.locator('.school-card').filter({ hasText: programs[0] }).waitFor()
    await search.fill('验证大学')
    const catalog = await api('student-catalog', 'GET', '/app/programs/search', () => search.press('Enter'))
    assert.deepEqual(catalog.data.items.map(item => item.programId).sort(), ids)
    await button('共 2 个符合方向').click()
    for (const [index, name] of programs.entries()) {
      const card = page.locator('.direction-row').filter({ hasText: name })
      await card.waitFor()
      if (index === 0) {
        await api('student-detail', 'GET', `/app/programs/${ids[index]}/detail`, () =>
          card.getByRole('button', { name: '详情', exact: true }).click())
        const drawer = page.getByRole('dialog', { name: '院校专业详情' })
        await drawer.getByText(name, { exact: false }).waitFor()
        await screen('student-detail')
        await page.keyboard.press('Escape')
        await drawer.waitFor({ state: 'hidden' })
      }
      await api('student-add-' + ids[index], 'POST', '/app/favorites/' + ids[index], () =>
        card.getByRole('button', { name: '加入备选', exact: true }).last().click())
    }
    await screen('student-catalog')

    await page.close()
    page = await openTab()
    await page.goto(base + '/')
    await nav('我的').click()
    await checkRoute('/profile')
    await heading('我的').waitFor()
    await page.getByText('330 分', { exact: true }).waitFor()
    const profileShortlistTab = () => page.getByRole('tab', { name: '备选学校', exact: true }).click()
    const profileShortlist = await api('student-profile-shortlist', 'GET', '/app/favorites', profileShortlistTab)
    assert.deepEqual(profileShortlist.data.map(item => item.programId).sort(), ids)
    const profileCompareButton = button('对比所选')
    const shortlistCheckboxes = programs.map(name =>
      page.locator('.shortlist-card').filter({ hasText: name }).getByRole('checkbox'))
    assert.equal(await profileCompareButton.isDisabled(), true)
    await shortlistCheckboxes[0].locator('..').click()
    assert.equal(await profileCompareButton.isDisabled(), true)
    await shortlistCheckboxes[1].press('Space')
    checkShortlistPair(await readShortlistSelection(page))
    for (const checkbox of shortlistCheckboxes) {
      await checkbox.press('Space')
      await repeatCheckedChange(checkbox)
    }
    checkShortlistPair(await readShortlistSelection(page))
    const profileComparison = await api('student-profile-compare', 'GET', '/app/programs/compare', () =>
      profileCompareButton.click())
    await checkRoute('/results')
    const table = page.locator('.compare-table')
    const compareRow = name => table.getByRole('row').filter({ has: page.getByRole('rowheader', { name, exact: true }) })
    const cutoffRow = compareRow('复试线')
    await cutoffRow.getByRole('cell', { name: '310', exact: true }).waitFor()
    await checkCompareHeaders(table, compareHeaders)
    const comparedPrograms = await compareRow('专业').getByRole('cell').allTextContents()
    const cutoffs = await cutoffRow.getByRole('cell').allTextContents()
    const gaps = await compareRow('与最低录取分差距').getByRole('cell').allTextContents()
    await screen('student-profile-compare')
    console.log(`我的 → 备选学校 → 对比所选 | ${comparedPrograms.join(' / ')} | 复试线 ${cutoffs.join(' / ')} | 与最低录取分差距 ${gaps.join(' / ')} | 预估分 ${profileComparison.data.estimatedScore}`)
    assert.deepEqual(profileComparison.data.items.map(item => item.programId).sort(), ids)
    assert.deepEqual(comparedPrograms, programs)
    assert.deepEqual(cutoffs, ['310', '315'])
    assert.deepEqual(gaps, ['+15', '+10'])
    assert.equal(profileComparison.data.estimatedScore, 330)
    await button('清空对比').click()

    const saved = await api('student-shortlist', 'GET', '/app/favorites', () => page.goto(base + '/favorites'))
    assert.deepEqual(saved.data.map(item => item.programId).sort(), ids)
    const favoriteRow = name => page.getByRole('row').filter({ hasText: name })
    for (const name of programs) await favoriteRow(name).locator('label.el-checkbox').click()
    const comparison = await api('student-compare', 'GET', '/app/programs/compare', () =>
      button('对比所选').click())
    assert.deepEqual(comparison.data.items.map(item => item.programId).sort(), ids)
    await heading('院校对比').waitFor()
    for (const name of programs) await page.locator('.compare-table tbody').getByText(name, { exact: true }).waitFor()
    await checkCompareHeaders(page.locator('.compare-table'), compareHeaders)
    assert.equal(comparison.data.estimatedScore, 330)
    await screen('student-compare')

    await api('student-shortlist-detail', 'GET', '/app/programs/900001/detail', () =>
      favoriteRow(programs[0]).getByRole('button', { name: '详情', exact: true }).click())
    const shortlistDetail = page.getByRole('dialog', { name: '备选专业详情' })
    await shortlistDetail.getByText(programs[0], { exact: false }).waitFor()
    await screen('student-shortlist-detail')
    await page.keyboard.press('Escape')
    await shortlistDetail.waitFor({ state: 'hidden' })

    for (const [index, name] of programs.entries()) {
      await api('student-remove-' + ids[index], 'DELETE', '/app/favorites/' + ids[index], () =>
        page.getByRole('row').filter({ hasText: name }).getByRole('button', { name: '取消收藏' }).click())
    }
    const empty = await api('student-shortlist-reload', 'GET', '/app/favorites', () => page.reload())
    assert.deepEqual(empty.data, [])
    assert.equal(await heading('院校对比').count(), 0)
    await page.getByText(/暂无备选，去推荐结果页/).waitFor()
    await screen('student-shortlist-empty')

    await api('student-mine', 'GET', profilePath, () => nav('我的').click())
    await checkRoute('/profile')
    await heading('我的').waitFor()
    await page.getByText('330 分', { exact: true }).waitFor()
    await screen('student-mine')
    const emptyProfileShortlist = await api('student-profile-shortlist-empty', 'GET', '/app/favorites', profileShortlistTab)
    assert.deepEqual(emptyProfileShortlist.data, [])
    await heading('暂无备选方案').waitFor()
    assert.equal(await profileCompareButton.isDisabled(), true)
    await screen('student-profile-shortlist-empty')
    console.log('我的 → 备选学校 | 暂无备选方案 | 备选总数 0')

    await api('student-ai-workspace', 'GET', '/app/ai-recommend-v2/draft', () => nav('AI 推荐').click())
    await checkRoute('/ai-recommend')
    await heading('AI 助手').waitFor()
    const generateDraft = button('生成新的候选草稿')
    const unavailable = await api('student-ai-start', 'POST', '/app/ai-recommend-v2/draft/generate/start', () =>
      generateDraft.click(), 503)
    assert.equal(unavailable.msg, 'AI 服务未配置，请联系管理员')
    const warning = page.getByText(unavailable.msg, { exact: true })
    await warning.waitFor()
    console.log('AI feedback ' + await warning.innerText())
    await heading('AI 助手').waitFor()
    await generateDraft.waitFor()
    assert.equal(await generateDraft.isEnabled(), true)
    await screen('student-ai-without-provider')

    await page.getByPlaceholder('输入消息，回车发送').fill('验证 AI 服务状态')
    const [chatResponse] = await Promise.all([
      page.waitForResponse(res => new URL(res.url()).pathname === '/dev-api/app/ai-recommend-v2/chat/send'),
      button('发送').click(),
    ])
    assert.equal(chatResponse.status(), 200)
    const stream = await chatResponse.text()
    assert.match(stream, /event:error/)
    assert.ok(stream.includes(unavailable.msg))
    await writeFile(join(evidence, 'student-ai-chat.sse.txt'), 'HTTP 200\n' + stream)
    await heading('AI 助手').waitFor()
    await button('发送').waitFor()
    await page.goto(base + '/favorites')
    await heading('我的备选').waitFor()
    const [logout] = await Promise.all([
      page.waitForResponse(res => new URL(res.url()).pathname === '/dev-api/app/auth/logout'),
      button('退出').click(),
    ])
    assert.equal(logout.status(), 200)
    assert.equal((await logout.json()).code, 200)
    assert.ok((await logout.request().allHeaders()).authorization, 'Logout must send the session for server-side invalidation')
    console.log('student-logout POST /app/auth/logout HTTP 200 JSON 200 (session sent)')
    await checkRoute('/login')
    assert.equal(new URL(page.url()).searchParams.get('redirect'), '/favorites')
    await signIn('student-return-login')
    await checkRoute('/favorites')
    await heading('我的备选').waitFor()
    await screen('student-return-favorites')

    assert.deepEqual(errors, [])
    console.log('PASS 登录 → 筛选结果 → 搜索 → 对比 → 备选添加和移除 → 我的 /profile → AI 助手 → 退出并返回备选')
  } catch (error) {
    if (new URL(page.url()).pathname !== '/login') await screen('student-failure')
    throw error
  } finally {
    await browser.close()
  }
}

verify().catch(error => {
  console.error(error)
  process.exitCode = 1
})
