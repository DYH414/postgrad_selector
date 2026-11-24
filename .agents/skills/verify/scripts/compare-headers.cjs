const assert = require('node:assert/strict')

async function checkCompareHeaders(table, expected) {
  const cells = table.locator('tr').first().locator('th,td')
  const headers = (await cells.allInnerTexts()).slice(1)
    .map(text => text.split(/\s*\n\s*/).filter(Boolean))
  assert.deepEqual(headers, expected)
}

module.exports = { checkCompareHeaders }
