const assert = require('node:assert/strict')

async function repeatCheckedChange(checkbox) {
  await checkbox.evaluate(input => {
    input.checked = true
    input.dispatchEvent(new Event('change', { bubbles: true }))
    input.dispatchEvent(new Event('change', { bubbles: true }))
  })
}

async function readShortlistSelection(page) {
  const checkboxes = page.locator('.shortlist-card').getByRole('checkbox')
  return {
    checked: await Promise.all((await checkboxes.all()).map(input => input.isChecked())),
    count: await page.getByText(/^已选择 \d+ 项$/).innerText(),
    disabled: await page.getByRole('button', { name: '对比所选', exact: true }).isDisabled(),
  }
}

function checkShortlistPair(selection) {
  assert.deepEqual(selection, { checked: [true, true], count: '已选择 2 项', disabled: false })
}

module.exports = { repeatCheckedChange, readShortlistSelection, checkShortlistPair }
