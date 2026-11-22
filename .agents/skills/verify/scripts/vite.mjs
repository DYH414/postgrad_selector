#!/usr/bin/env node
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

const [root, cacheDir] = process.argv.slice(2)
const { createServer } = await import(pathToFileURL(resolve(root, 'node_modules/vite/dist/node/index.js')))
const server = await createServer({
  root,
  cacheDir,
  server: {
    host: '127.0.0.1',
    port: 18082,
    strictPort: true,
    proxy: { '/dev-api': { target: 'http://127.0.0.1:18080', changeOrigin: false } },
  },
})
await server.listen()
server.printUrls()
