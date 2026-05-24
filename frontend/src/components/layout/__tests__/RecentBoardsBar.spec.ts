import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import RecentBoardsBar from '../RecentBoardsBar.vue'

vi.mock('@/utils/image', () => ({
  getOptimizedBoardIconUrl: (url: string) => url,
  handleImageError: vi.fn()
}))

describe('RecentBoardsBar', () => {
  afterEach(() => {
    localStorage.clear()
  })

  it('labels the recent board remove button with the board name', () => {
    localStorage.setItem('recentBoards', JSON.stringify([
      {
        boardUrl: 'free',
        boardName: 'Free',
        visitedAt: '2026-05-23T00:00:00.000Z'
      },
      {
        boardUrl: 'notice',
        boardName: 'Notice',
        visitedAt: '2026-05-23T01:00:00.000Z'
      }
    ]))

    const wrapper = mount(RecentBoardsBar, {
      global: {
        stubs: {
          RouterLink: RouterLinkStub
        },
        mocks: {
          $t: (key: string) => key
        }
      }
    })

    const removeButton = wrapper.get('.recent-board-remove')
    expect(removeButton.attributes('aria-label')).toBe('Free 제거')
    expect(removeButton.attributes('type')).toBe('button')
    expect(removeButton.find('svg').attributes('aria-hidden')).toBe('true')
    expect(wrapper.getComponent(RouterLinkStub).find('.recent-board-remove').exists()).toBe(false)
    expect(wrapper.getComponent(RouterLinkStub).classes()).toContain('recent-board-link')
    expect(wrapper.get('.recent-boards-clear').attributes('type')).toBe('button')
  })
})
