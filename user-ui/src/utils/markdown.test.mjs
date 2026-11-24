import assert from 'node:assert/strict'
import { test } from 'node:test'
import { getMessageEvidence, renderMarkdown } from './markdown.js'

test('knowledge-document filenames render as text', () => {
  const filename = '02-考研国家线与复试.md'
  assert.equal(renderMarkdown(filename), `<p>${filename}</p>\n`)
})

test('explicit URLs still render as links', () => {
  const url = 'https://example.org/docs/reference.md'
  assert.equal(renderMarkdown(url), `<p><a href="${url}">${url}</a></p>\n`)
  assert.equal(renderMarkdown(`[招生简章](${url})`), `<p><a href="${url}">招生简章</a></p>\n`)
})

test('citation metadata links filename mentions and deduplicates sources', () => {
  const filename = '02-考研国家线与复试.md'
  const url = 'https://example.org/official'
  const metadataJson = JSON.stringify({ ragCitations: [
    { sourceName: filename, title: '复试', sourceUrl: url },
    { sourceName: filename, sourceUrl: url }
  ] })
  const sources = getMessageEvidence({ metadataJson })
  assert.deepEqual(sources, [{ name: filename, url }])
  assert.equal(renderMarkdown(filename), `<p>${filename}</p>\n`)
  assert.equal(renderMarkdown(filename, sources), `<p><a href="${url}" target="_blank" rel="noreferrer">${filename}</a></p>\n`)
  assert.equal(renderMarkdown(`**${filename}** [${filename}](https://other.org) \`${filename}\``, sources),
    `<p><strong><a href="${url}" target="_blank" rel="noreferrer">${filename}</a></strong> <a href="${url}" target="_blank" rel="noreferrer">${filename}</a> <a href="${url}" target="_blank" rel="noreferrer"><code>${filename}</code></a></p>\n`)
  assert.equal(renderMarkdown(`other-${filename}`, sources), `<p>other-${filename}</p>\n`)
})

test('missing and unsafe citation URLs stay unlinked', () => {
  const sources = getMessageEvidence({ metadataJson: JSON.stringify({ ragCitations: [
    { sourceName: 'one.md' }, { sourceName: 'two.md', sourceUrl: 'javascript:alert(1)' }
  ] }) })
  assert.deepEqual(sources, [{ name: 'one.md', url: '' }, { name: 'two.md', url: '' }])
  assert.equal(renderMarkdown('one.md two.md', sources), '<p>one.md two.md</p>\n')
  assert.equal(renderMarkdown('[one.md](https://other.org)', sources), '<p>one.md</p>\n')
})

test('methodology citation keeps its primary link and provides the official backup', () => {
  const name = '05-择校方法论.md'
  const url = 'https://www.moe.gov.cn/srcsite/A15/moe_778/s3261/202509/t20250918_1413836.html'
  const backup = { name: '研招网', url: 'https://yzst.chsi.com.cn/kyzx/jybzc/202509/20250925/2293432170-9.html' }
  const sources = getMessageEvidence({ metadata: { ragCitations: [{ sourceName: name, sourceUrl: url }] } })
  assert.deepEqual(sources, [{ name, url, backup }])
  assert.equal(renderMarkdown(name, sources), `<p><a href="${url}" target="_blank" rel="noreferrer">${name}</a></p>\n`)
  assert.deepEqual(getMessageEvidence({ metadata: { ragCitations: [{ sourceName: name, sourceUrl: 'https://example.org/' }] } }),
    [{ name, url: 'https://example.org/' }])
})
