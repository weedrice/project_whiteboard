import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'
import { ref } from 'vue'
import { createPinia } from 'pinia'
import RecentBoardsBar from '../RecentBoardsBar.vue'

const recentUpdates = ref([{ boardUrl: 'free', latestPostAt: null }, { boardUrl: 'notice', latestPostAt: null }])
vi.mock('@/composables/useApiQuery', () => ({
  useApiQuery: () => ({
    data: recentUpdates,
    isSuccess: ref(true),
  }),
}))

vi.mock('@/utils/image', () => ({
  getOptimizedBoardIconUrl: (url: string) => url,
  handleImageError: vi.fn()
}))

describe('RecentBoardsBar', () => {
  afterEach(() => {
    localStorage.clear()
  })

  it('labels the recent board remove button with the board name', () => {
    localStorage.setItem('recentBoards:guest', JSON.stringify([
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
        plugins: [createPinia()],
        stubs: {
          RouterLink: RouterLinkStub
        },
        mocks: {
          $t: (key: string, params?: Record<string, string>) => {
            if (key === 'layout.recentBoards.removeAria') return `${params?.name} 제거`
            return key
          }
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

  it('hides and removes boards the server does not return as accessible', async () => {
    recentUpdates.value = [{ boardUrl: 'free', latestPostAt: null }]
    localStorage.setItem('recentBoards:guest', JSON.stringify([
      { boardUrl: 'free', boardName: 'Free', visitedAt: '2026-05-23T00:00:00.000Z' },
      { boardUrl: 'private', boardName: 'Private', visitedAt: '2026-05-23T01:00:00.000Z' },
    ]))

    const wrapper = mount(RecentBoardsBar, {
      global: {
        plugins: [createPinia()],
        stubs: { RouterLink: RouterLinkStub },
        mocks: { $t: (key: string) => key },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Free')
    expect(wrapper.text()).not.toContain('Private')
    expect(localStorage.getItem('recentBoards:guest')).not.toContain('private')
  })
})
