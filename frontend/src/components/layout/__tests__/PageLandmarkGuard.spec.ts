import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const defaultLayoutPages = [
  'src/views/user/MyPage.vue',
  'src/views/user/UserProfilePage.vue',
  'src/views/tag/TagPage.vue',
  'src/views/onboarding/OnboardingPage.vue',
]

describe('default layout page landmarks', () => {
  it.each(defaultLayoutPages)('%s does not nest another main landmark', (file) => {
    const source = readFileSync(resolve(process.cwd(), file), 'utf8')

    expect(source).not.toMatch(/<main\b/)
  })
})
