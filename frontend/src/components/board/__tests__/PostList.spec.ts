import { defineComponent } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import PostList from '../PostList.vue'

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string) => key
    })
  }
})

vi.mock('@/utils/date', () => ({
  formatRelativeDate: () => 'now'
}))

describe('PostList', () => {
  it('preserves pagination query in post detail links when provided', () => {
    const wrapper = mount(PostList, {
      props: {
        posts: [
          {
            postId: 101,
            boardUrl: 'free',
            title: '페이지 링크 테스트',
            createdAt: '2026-04-15T00:00:00',
            viewCount: 1,
            likeCount: 2,
            commentCount: 3
          }
        ],
        linkQuery: {
          page: '3',
          q: 'vue'
        }
      },
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          BaseTable: true,
          UserMenu: true
        }
      }
    })

    const postLink = wrapper.findComponent(RouterLinkStub)

    expect(postLink.exists()).toBe(true)
    expect(postLink.props('to')).toEqual({
      path: '/board/free/post/101',
      query: {
        page: '3',
        q: 'vue'
      }
    })
  })

  it('uses the noviis column order on desktop', () => {
    const BaseTableStub = defineComponent({
      name: 'BaseTable',
      props: {
        columns: {
          type: Array,
          required: true
        },
        rowClass: {
          type: Function,
          default: undefined
        }
      },
      template: '<div />'
    })

    const wrapper = mount(PostList, {
      props: {
        posts: []
      },
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          BaseTable: BaseTableStub,
          RouterLink: RouterLinkStub,
          UserMenu: true
        }
      }
    })

    const table = wrapper.findComponent(BaseTableStub)
    const columns = table.props('columns') as Array<{ key: string }>

    expect(columns.map((column) => column.key)).toEqual([
      'postId',
      'title',
      'author',
      'likeCount',
      'viewCount',
      'createdAt'
    ])
    const rowClass = table.props('rowClass') as (item: { postId: number }) => string
    expect(rowClass({ postId: 1 })).toContain('post-list-row')
  })

  it('keeps desktop column widths within 100 percent when board names are shown', () => {
    const BaseTableStub = defineComponent({
      name: 'BaseTable',
      props: {
        columns: {
          type: Array,
          required: true
        }
      },
      template: '<div />'
    })

    const wrapper = mount(PostList, {
      props: {
        posts: [],
        showBoardName: true
      },
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          BaseTable: BaseTableStub,
          RouterLink: RouterLinkStub,
          UserMenu: true
        }
      }
    })

    const table = wrapper.findComponent(BaseTableStub)
    const columns = table.props('columns') as Array<{ width?: string }>
    const totalWidth = columns.reduce((sum, column) => sum + Number.parseInt(column.width || '0', 10), 0)

    expect(totalWidth).toBeLessThanOrEqual(100)
  })

  it('keeps invalid board targets non-interactive on mobile', () => {
    const wrapper = mount(PostList, {
      props: {
        posts: [
          {
            postId: 202,
            title: '잘못된 링크 글',
            createdAt: '2026-04-15T00:00:00',
            viewCount: 4,
            likeCount: 5,
            commentCount: 0
          }
        ]
      },
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          BaseTable: true,
          UserMenu: true
        }
      }
    })

    expect(wrapper.findComponent(RouterLinkStub).exists()).toBe(false)
    expect(wrapper.text()).toContain('잘못된 링크 글')
  })

  it('intercepts inquiry clicks without navigating', async () => {
    const wrapper = mount(PostList, {
      props: {
        posts: [
          {
            postId: 303,
            boardUrl: 'inquiry',
            title: '문의 글',
            createdAt: '2026-04-15T00:00:00',
            viewCount: 4,
            likeCount: 1,
            commentCount: 0,
            inquiryAnswered: false
          }
        ],
        interceptInquiry: true
      },
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          BaseTable: true,
          UserMenu: true
        }
      }
    })

    await wrapper.find('button').trigger('click')

    expect(wrapper.emitted('inquiry-click')).toHaveLength(1)
    expect(wrapper.findComponent(RouterLinkStub).exists()).toBe(false)
  })
})
