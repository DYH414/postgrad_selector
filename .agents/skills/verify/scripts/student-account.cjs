const assert = require('node:assert/strict')
const { readFile } = require('node:fs/promises')

async function readStudent() {
  assert.ok(process.env.POSTGRAD_TEST_ACCOUNTS, 'Set POSTGRAD_TEST_ACCOUNTS to the local credentials file')
  const text = await readFile(process.env.POSTGRAD_TEST_ACCOUNTS, 'utf8')
  const row = text.split('\n').find(line => line.startsWith('| Student |'))
  assert.ok(row, 'Expected a Student row in POSTGRAD_TEST_ACCOUNTS')
  const [, , account, password] = row.split('|').map(cell => cell.trim())
  assert.ok(account && password, 'The Student row needs an email and password')
  return { account, password }
}

async function checkAccount() {
  const [command, ui] = process.argv.slice(2)
  const { account, password } = await readStudent()
  const base = ui + '/dev-api/app/auth'
  async function request(path, options) {
    const response = await fetch(base + path, { ...options, signal: AbortSignal.timeout(15000) })
    const body = await response.json()
    console.log(`${options.method || 'GET'} /app/auth${path} HTTP ${response.status} JSON ${body.code}`)
    assert.ok(response.ok && body.code === 200, 'Applicant account request failed')
    return body
  }

  const headers = { 'Content-Type': 'application/json', Origin: ui }
  if (command === 'register') {
    await request('/register', {
      method: 'POST', headers,
      body: JSON.stringify({ email: account, password, confirmPassword: password }),
    })
    return
  }
  assert.equal(command, 'doctor')
  const login = await request('/login', {
    method: 'POST', headers, body: JSON.stringify({ account, password }),
  })
  assert.ok(login.token, 'Login returned no token')
  const authentication = { Authorization: 'Bearer ' + login.token }
  try {
    const me = await request('/me', { headers: authentication })
    assert.ok(me.userId === login.userId && me.role === 'user', 'Expected the fixture student')
  } finally {
    await request('/logout', { method: 'POST', headers: authentication })
  }
}

module.exports = { readStudent }

if (require.main === module) {
  checkAccount().catch(error => {
    console.error(error.message)
    process.exitCode = 1
  })
}
