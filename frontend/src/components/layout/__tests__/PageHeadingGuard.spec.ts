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
    'src/views/search/SearchPage.vue',
  ])('%s does not skip from its route heading to h3', (file) => {
    const source = readFileSync(resolve(process.cwd(), file), 'utf8')

    expect(source).toMatch(/<h1\b/)
    expect(source).toMatch(/<h2\b/)
  })

  it('nests the administrator list below the shared admin page heading', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/views/admin/AdminManagement.vue'), 'utf8')

    expect(source).toMatch(/<AdminDataPage\b/)
    expect(source).toMatch(/<h2\b/)
    expect(source).not.toMatch(/<h3\b/)
  })

  it.each([
    'src/views/board/AllBoardsPage.vue',
    'src/views/search/SearchPage.vue',
    'src/views/tag/TagPage.vue',
    'src/views/home/HomeFeed.vue',
  ])('%s gives its page-level error an h2 title', (file) => {
    const source = readFileSync(resolve(process.cwd(), file), 'utf8')

    expect(source).toMatch(/<ErrorState[\s\S]*?title-tag="h2"/)
  })

  it.each([
    'src/components/board/PostFormHeader.vue',
    'src/views/user/MyPageDashboard.vue',
    'src/views/user/SubscribedBoards.vue',
  ])('%s exposes its route page heading as h1', (file) => {
    const source = readFileSync(resolve(process.cwd(), file), 'utf8')

    expect(source).toMatch(/<h1\b/)
  })

  it.each([
    'src/views/board/BoardCreate.vue',
    'src/views/board/BoardEdit.vue',
    'src/views/board/InquiryWrite.vue',
    'src/views/user/UserSettings.vue',
    'src/views/emoticon/EmoticonRegister.vue',
    'src/views/emoticon/EmoticonEdit.vue',
  ])('%s uses the shared route page header', (file) => {
    const source = readFileSync(resolve(process.cwd(), file), 'utf8')

    expect(source).toMatch(/<PageHeader\b/)
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
