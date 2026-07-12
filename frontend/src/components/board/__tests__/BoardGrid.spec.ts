import { mount, RouterLinkStub } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import BoardGrid from '../BoardGrid.vue'
import type { BoardListItem } from '@/types'

const makeBoard = (overrides: Partial<BoardListItem> = {}): BoardListItem => ({
  boardId: 1,
  boardName: 'Free',
  boardUrl: 'free',
  description: 'first description',
  iconUrl: null,
  adminDisplayName: 'Admin A',
  subscriberCount: 1,
  isSubscribed: false,
  ...overrides,
} as BoardListItem)

describe('BoardGrid', () => {
  it('exposes the empty result as a polite status', () => {
    const wrapper = mount(BoardGrid, {
      props: { boards: [] },
      global: {
        mocks: { $t: (key: string) => key },
        stubs: { LayoutGrid: true, RouterLink: RouterLinkStub },
      },
    })

    const emptyState = wrapper.get('[role="status"]')
    expect(emptyState.attributes('aria-live')).toBe('polite')
    expect(emptyState.get('h2').text()).toBe('board.list.empty')
  })

  it('updates memoized card content when display-only board fields change', async () => {
    const wrapper = mount(BoardGrid, {
      props: {
        boards: [makeBoard()],
      },
      global: {
        mocks: {
          $t: (key: string, params?: Record<string, unknown>) => params?.count ? `${key}:${params.count}` : key,
        },
        stubs: {
          RouterLink: RouterLinkStub,
          User: true,
          Users: true,
        },
      },
    })

    expect(wrapper.text()).toContain('first description')
    expect(wrapper.text()).toContain('Admin A')

    await wrapper.setProps({
      boards: [makeBoard({
        description: 'updated description',
        adminDisplayName: 'Admin B',
      })],
    })

    expect(wrapper.text()).toContain('updated description')
    expect(wrapper.text()).toContain('Admin B')
  })

  it('uses the requested heading level and visible keyboard focus style', () => {
    const wrapper = mount(BoardGrid, {
      props: {
        boards: [makeBoard()],
        headingTag: 'h2',
      },
      global: {
        mocks: { $t: (key: string) => key },
        stubs: {
          RouterLink: RouterLinkStub,
          User: true,
          Users: true,
        },
      },
    })

    expect(wrapper.get('h2').text()).toBe('Free')
    expect(wrapper.getComponent(RouterLinkStub).classes()).toContain('nv-focus-ring')
  })
})
