import assert from 'node:assert/strict'
import { test } from 'node:test'
import { renderMarkdown } from './markdown.js'

test('knowledge-document filenames render as text', () => {
  const filename = '02-考研国家线与复试.md'
  assert.equal(renderMarkdown(filename), `<p>${filename}</p>\n`)
})

test('explicit URLs still render as links', () => {
  const url = 'https://example.org/docs/reference.md'
  assert.equal(renderMarkdown(url), `<p><a href="${url}">${url}</a></p>\n`)
  assert.equal(renderMarkdown(`[招生简章](${url})`), `<p><a href="${url}">招生简章</a></p>\n`)
})
