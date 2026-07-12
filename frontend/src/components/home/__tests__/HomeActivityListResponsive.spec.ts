import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('HomeActivityList responsive layout', () => {
  it('stacks activity metadata on narrow screens and restores the row layout at sm', () => {
    const component = readFileSync(resolve(process.cwd(), 'src/components/home/HomeActivityList.vue'), 'utf8')
    const styles = readFileSync(resolve(process.cwd(), 'src/styles/home-feed.css'), 'utf8')

    expect(component).toContain('sm:w-[5.75rem]')
    expect(component).toContain('sm:ml-4 sm:mt-0')
    expect(component).toMatch(/MessageSquare[^>]+aria-hidden="true"/)
    expect(component).toMatch(/ChevronRight[^>]+aria-hidden="true"/)
    expect(styles).toMatch(/\.nv-home-activity-row\s*{[\s\S]*?flex-col[\s\S]*?sm:flex-row/)
  })
})
