import request from '@/utils/request'

const base = '/postgrad/rag-evaluation'

export function listDatasets(params) { return request({ url: `${base}/datasets`, method: 'get', params }) }
export function getDataset(id) { return request({ url: `${base}/datasets/${id}`, method: 'get' }) }
export function createDataset(data) { return request({ url: `${base}/datasets`, method: 'post', data }) }
export function updateDataset(id, data) { return request({ url: `${base}/datasets/${id}`, method: 'put', data }) }
export function saveCase(datasetId, data) { return request({ url: `${base}/datasets/${datasetId}/cases`, method: 'post', data }) }
export function updateCase(id, data) { return request({ url: `${base}/cases/${id}`, method: 'put', data }) }
export function deleteCase(id) { return request({ url: `${base}/cases/${id}`, method: 'delete' }) }
export function freezeDataset(id, version) { return request({ url: `${base}/datasets/${id}/freeze`, method: 'post', data: { version } }) }
export function importBundled(name) { return request({ url: `${base}/datasets/import/${name}`, method: 'post' }) }
export function importJsonl(data) { return request({ url: `${base}/datasets/import-jsonl`, method: 'post', data, headers: { 'Content-Type': 'multipart/form-data' } }) }
export function exportDataset(id) { return request({ url: `${base}/datasets/${id}/export`, method: 'get', responseType: 'blob' }) }
export function listRuns(params) { return request({ url: `${base}/runs`, method: 'get', params }) }
export function startRun(data) { return request({ url: `${base}/runs`, method: 'post', data }) }
export function getRun(id) { return request({ url: `${base}/runs/${id}`, method: 'get' }) }
export function cancelRun(id) { return request({ url: `${base}/runs/${id}/cancel`, method: 'post' }) }
export function reviewResult(id, data) { return request({ url: `${base}/results/${id}/review`, method: 'put', data }) }
export function compareRuns(params) { return request({ url: `${base}/compare`, method: 'get', params }) }
export function getTraceDraft(traceId) { return request({ url: `${base}/trace-draft/${traceId}`, method: 'get' }) }
export function downloadReport(runId, format) { return request({ url: `${base}/runs/${runId}/report`, method: 'get', params: { format }, responseType: 'blob' }) }

// ── AGENT_E2E: Agent 配置版本 ──
export function listAgentConfigVersions(params) { return request({ url: `${base}/agent-config-versions`, method: 'get', params }) }
export function createAgentConfigVersion(data) { return request({ url: `${base}/agent-config-versions`, method: 'post', data }) }
export function getAgentConfigVersion(id) { return request({ url: `${base}/agent-config-versions/${id}`, method: 'get' }) }
export function startAgentRun(data) { return request({ url: `${base}/agent-runs`, method: 'post', data }) }
export function listAgentRunResults(runId) { return request({ url: `${base}/runs/${runId}/agent-results`, method: 'get' }) }
