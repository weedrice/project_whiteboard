import { describe, expect, it } from 'vitest'
import type { PostSummary } from '@/types'
import {
  createPostListColumns,
  getPostListBoardNameLabel,
  getPostListCountLabel,
  getPostListActiveSortDirection,
  getPostListActiveSortKey,
  getPostListInteractiveTag,
  getPostListNextSort,
  getPostListPreviewImageUrl,
  getPostListResolvedBoardUrl,
  getPostListRowNumberLabel,
  getPostListTitleProps,
  getPostListTitleTag,
  resolvePostListBoardRoute,
  resolvePostListPostRoute,
  shouldInterceptPostListInquiry,
} from '../postListModel'

const post = (overrides: Partial<PostSummary> = {}): PostSummary => ({
  postId: 15,
  title: 'Post',
  createdAt: '2026-01-01T00:00:00',
  viewCount: 1,
  likeCount: 2,
  commentCount: 3,
  isNotice: false,
  isNsfw: false,
  isSpoiler: false,
  boardUrl: 'Free',
  author: {
    userId: 1,
    displayName: 'Author',
  },
  ...overrides,
})

describe('postListModel', () => {
  it('normalizes board urls and resolves board/post routes', () => {
    const item = post({ boardUrl: '/Free/' })

    expect(getPostListResolvedBoardUrl(item)).toBe('free')
    expect(resolvePostListBoardRoute(item, undefined)).toBe('/board/free/')
    expect(resolvePostListBoardRoute(item, undefined, (_post, boardUrl) => ({
      name: 'board-detail',
      params: { boardUrl },
    }))).toEqual({
      name: 'board-detail',
      params: { boardUrl: 'free' },
    })
    expect(resolvePostListPostRoute(item, undefined, { page: '2' }, (_post, boardUrl, linkQuery) => ({
      name: 'post-detail',
      params: { boardUrl, postId: item.postId },
      query: linkQuery,
    }))).toEqual({
      name: 'post-detail',
      params: { boardUrl: 'free', postId: 15 },
      query: { page: '2' },
    })
  })

  it('maps display sorting to API sorting', () => {
    expect(getPostListNextSort('createdAt,desc', 'postId')).toBe('createdAt,asc')
    expect(getPostListNextSort('createdAt,asc', 'postId')).toBe('createdAt,desc')
    expect(getPostListNextSort('createdAt,desc', 'likeCount')).toBe('likeCount,desc')
    expect(getPostListActiveSortKey('createdAt,desc')).toBe('postId')
    expect(getPostListActiveSortDirection('createdAt,desc')).toBe('desc')
    expect(getPostListActiveSortKey('unknown,desc')).toBeNull()
    expect(getPostListActiveSortDirection('unknown,desc')).toBeNull()
  })

  it('returns preview images only for unprotected posts', () => {
    expect(getPostListPreviewImageUrl(post({ thumbnailUrl: ' /thumb.jpg ' }))).toBe('/thumb.jpg')
    expect(getPostListPreviewImageUrl(post({ hasImage: true }))).toBeNull()
    expect(getPostListPreviewImageUrl(post({ thumbnailUrl: '/nsfw.jpg', isNsfw: true }))).toBeNull()
    expect(getPostListPreviewImageUrl(post({ thumbnailUrl: '/spoiler.jpg', isSpoiler: true }))).toBeNull()
    expect(getPostListPreviewImageUrl(post({ thumbnailUrl: '/secret.jpg', isSecret: true }))).toBeNull()
    expect(getPostListPreviewImageUrl(post({ thumbnailUrl: '/blinded.jpg', isBlinded: true }))).toBeNull()
  })

  it('builds navigation tags and title props from route availability', () => {
    expect(getPostListInteractiveTag(true, true)).toBe('button')
    expect(getPostListInteractiveTag(false, true)).toBe('router-link')
    expect(getPostListInteractiveTag(false, false)).toBe('div')
    expect(getPostListTitleTag(true, true)).toBe('button')
    expect(getPostListTitleTag(false, true)).toBe('router-link')
    expect(getPostListTitleTag(false, false)).toBe('span')
    expect(getPostListTitleProps('button', null, 'invalid')).toEqual({ type: 'button' })
    expect(getPostListTitleProps('router-link', '/target', 'invalid')).toEqual({ to: '/target' })
    expect(getPostListTitleProps('span', null, 'invalid')).toEqual({ title: 'invalid' })
  })

  it('creates desktop columns for board and compact contexts', () => {
    const labels = {
      no: 'No',
      board: 'Board',
      title: 'Title',
      author: 'Author',
      likes: 'Likes',
      views: 'Views',
      date: 'Date',
    }

    const defaultColumns = createPostListColumns(labels, { showBoardName: false, hideNoColumn: false })
    expect(defaultColumns.map((column) => column.key)).toEqual([
      'postId',
      'title',
      'author',
      'likeCount',
      'viewCount',
      'createdAt',
    ])
    expect(Object.fromEntries(defaultColumns.map((column) => [column.key, column.width]))).toMatchObject({
      title: '45%',
      author: '18%',
      viewCount: '6%',
    })

    const boardColumns = createPostListColumns(labels, { showBoardName: true, hideNoColumn: true })
    expect(boardColumns.map((column) => column.key)).toEqual([
      'boardName',
      'title',
      'author',
      'likeCount',
      'viewCount',
      'createdAt',
    ])
    expect(Object.fromEntries(boardColumns.map((column) => [column.key, column.width]))).toMatchObject({
      title: '31%',
      author: '18%',
      viewCount: '6%',
    })
  })

  it('checks inquiry interception with normalized board urls', () => {
    expect(shouldInterceptPostListInquiry(
      post({ boardUrl: '/inquiry/' }),
      undefined,
      true,
      (_post, boardUrl) => boardUrl === 'inquiry',
    )).toBe(true)
    expect(shouldInterceptPostListInquiry(post(), undefined, false, () => true)).toBe(false)
  })

  it('formats desktop table cell fallback labels', () => {
    expect(getPostListRowNumberLabel(post({ rowNum: 7 }), 'Notice', true)).toBe(7)
    expect(getPostListRowNumberLabel(post({ isNotice: true, rowNum: 7 }), 'Notice', true)).toBe('Notice')
    expect(getPostListBoardNameLabel(post({ boardName: '' }))).toBe('-')
    expect(getPostListBoardNameLabel(post({ boardName: 'Free board' }))).toBe('Free board')
    expect(getPostListCountLabel(12)).toBe('12')
  })
})
