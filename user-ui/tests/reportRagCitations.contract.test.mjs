import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const root = process.cwd()
const reportDocument = readFileSync(join(root, 'src/views/ai-recommend-v2/components/report/ReportDocument.vue'), 'utf8')
const chatPanel = readFileSync(join(root, 'src/views/ai-recommend-v2/components/AiChatMiniPanel.vue'), 'utf8')
const citationComponentPath = join(root, 'src/views/ai-recommend-v2/components/report/ReportRagCitations.vue')
let citationComponent = ''
try {
  citationComponent = readFileSync(citationComponentPath, 'utf8')
} catch {
  citationComponent = ''
}

assert.match(reportDocument, /ReportRagCitations/, 'ReportDocument must render RAG citations component')
assert.match(reportDocument, /report\?\.ragCitations/, 'ReportDocument must pass backend ragCitations')
assert.match(reportDocument, /#report-evidence/, 'Report outline must include evidence anchor')

assert.match(citationComponent, /rag-citation-section/, 'Citation component must render a citation section')
assert.match(citationComponent, /contentPreview/, 'Citation component must show content preview')
assert.match(citationComponent, /docType/, 'Citation component must show document type')
assert.match(citationComponent, /section/, 'Citation component must show semantic section')
assert.match(citationComponent, /programId/, 'Citation component must support candidate-level grouping')
assert.match(citationComponent, /generalCitations/, 'Citation component must separate general evidence')

assert.match(chatPanel, /getMessageEvidence/, 'Chat panel must detect evidence metadata on messages')
assert.match(chatPanel, /evidence-sources/, 'Chat panel must render compact evidence sources when present')
assert.match(chatPanel, /getProgramEvidence/, 'Chat tool indicator must label getProgramEvidence')
