import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const singleListPages = [
  'src/views/user/ScrapList.vue',
  'src/views/search/RecentViewed.vue',
  'src/views/user/MyNotifications.vue',
  'src/views/user/BlockList.vue',
  'src/views/user/PointHistory.vue',
  'src/views/user/MyReports.vue',
]

describe('route page heading hierarchy', () => {
  it.each(singleListPages)('%s gives its list the page heading', (file) => {
    const source = readFileSync(resolve(process.cwd(), file), 'utf8')

    expect(source).toMatch(/<PaginatedListCard[\s\S]*?title-tag="h1"/)
  })

  it('uses one page heading and one section heading for draft lists', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/views/user/DraftList.vue'), 'utf8')

    expect(source.match(/title-tag="h1"/g)).toHaveLength(1)
    expect(source.match(/title-tag="h2"/g)).toHaveLength(1)
  })

  it.each([
    'src/components/board/PostFormHeader.vue',
    'src/views/board/BoardCreate.vue',
    'src/views/board/BoardEdit.vue',
    'src/views/user/MyPageDashboard.vue',
    'src/views/user/UserSettings.vue',
    'src/views/user/SubscribedBoards.vue',
  ])('%s exposes its route page heading as h1', (file) => {
    const source = readFileSync(resolve(process.cwd(), file), 'utf8')

    expect(source).toMatch(/<h1\b/)
  })

  it('demotes the board heading when a post detail supplies the page heading', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/views/board/BoardDetail.vue'), 'utf8')

    expect(source).toContain(`:heading-tag="currentPostId ? 'h2' : 'h1'"`)
  })

  it.each([
    'src/components/user/BadgeAwardCelebration.vue',
    'src/components/user/MyInquiryDetailModal.vue',
    'src/components/admin/AdminInquiryDetailModal.vue',
  ])('%s nests its content heading below the shared modal title', (file) => {
    const source = readFileSync(resolve(process.cwd(), file), 'utf8')

    expect(source).toMatch(/<h3\b/)
    expect(source).not.toMatch(/<h2\b/)
  })
})
