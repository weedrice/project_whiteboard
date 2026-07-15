import { describe, expect, it } from 'vitest'
import {
  extractPrecacheUrls,
  measurePrecache,
  verifyJavaScriptChunkBudget,
  verifyPrecacheBudget,
} from '../verify-pwa-budget.mjs'

describe('PWA precache budget verification', () => {
  it('extracts only local URLs from the injected Workbox manifest', () => {
    const source = 'precacheAndRoute([{"revision":null,"url":"index.html"},{"revision":"1","url":"js/app.js"},{"url":"https://cdn.example.com/a.js"}])'

    expect(extractPrecacheUrls(source)).toEqual(['index.html', 'js/app.js'])
  })

  it('rejects byte and entry count regressions', () => {
    expect(() => verifyPrecacheBudget({
      totalBytes: 101,
      entryCount: 2,
      maxBytes: 100,
      maxEntries: 2,
    })).toThrow(/budget is 100 bytes/)

    expect(() => verifyPrecacheBudget({
      totalBytes: 100,
      entryCount: 3,
      maxBytes: 100,
      maxEntries: 2,
    })).toThrow(/budget is 2 entries/)
  })

  it('rejects a missing injected app shell manifest', () => {
    expect(() => measurePrecache({
      distDirectory: process.cwd(),
      serviceWorkerSource: 'precacheAndRoute([])',
    })).toThrow(/missing the app shell/)
  })

  it('rejects oversized JavaScript chunks', () => {
    expect(() => verifyJavaScriptChunkBudget([
      { file: 'js/app.js', bytes: 450_001 },
    ], 450_000)).toThrow(/js\/app\.js=450001/)

    expect(() => verifyJavaScriptChunkBudget([
      { file: 'js/app.js', bytes: 450_000 },
    ], 450_000)).not.toThrow()
  })
})
