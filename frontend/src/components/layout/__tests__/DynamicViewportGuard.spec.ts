import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const readSource = (path: string) => readFileSync(resolve(process.cwd(), path), 'utf8')

describe('dynamic viewport contract', () => {
  it.each([
    'src/components/layout/DefaultLayout.vue',
    'src/views/admin/AdminLayout.vue',
    'src/views/auth/OAuthCallback.vue',
  ])('uses dynamic viewport sizing in %s', (path) => {
    const source = readSource(path)

    expect(source).toContain('min-h-dvh')
    expect(source).not.toMatch(/(?:min-h|h)-screen/)
  })

  it('keeps full-screen fallback and picker bounds compatible with mobile browser chrome', () => {
    const boundary = readSource('src/components/common/ErrorBoundary.vue')
    const picker = readSource('src/components/common/widgets/EmoticonPicker.vue')

    expect(boundary).toMatch(/min-height: 100vh;[\s\S]*min-height: 100dvh;/)
    expect(picker).toContain('max-height: min(420px, calc(100dvh - 32px))')
  })
})
