import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const login = readFileSync(new URL('./Login.vue', import.meta.url), 'utf8')

assert.match(login, /手机号或邮箱至少填写一项，均可用于登录/)
assert.doesNotMatch(login, /手机号（选填）|邮箱（选填）/)
assert.match(login, /const validatePhone\s*=/)
assert.match(login, /\^1\[3-9\]\\d\{9\}\$/)
assert.match(login, /const validateEmail\s*=/)
assert.match(login, /phone:\s*\[\{ validator: validatePhone/)
assert.match(login, /email:\s*\[\{ validator: validateEmail/)
assert.match(login, /@input="revalidateIdentity\('email'\)"/)
assert.match(login, /@input="revalidateIdentity\('phone'\)"/)
assert.match(login, /function revalidateIdentity\(field\)/)

console.log('Login registration contract tests passed')
