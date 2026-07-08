import { defineComponent } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import BaseTable from '@/components/common/ui/BaseTable.vue'
import PostList from '../PostList.vue'
import type { PostSummary } from '@/types'
import type { LocationQueryRaw, RouteLocationRaw } from 'vue-router'

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string) => ({
        'user.deletedUser': '탈퇴한 사용자',
      })[key] ?? key
    })
  }
})

vi.mock('@/utils/date', () => ({
  formatRelativeDate: () => 'now'
}))

describe('PostList', () => {
  const createPost = (overrides: Partial<PostSummary> = {}): PostSummary => ({
    postId: 1,
    title: 'Post title',
    createdAt: '2026-04-15T00:00:00',
    viewCount: 0,
    likeCount: 0,
    commentCount: 0,
    isNotice: false,
    isNsfw: false,
    isSpoiler: false,
    author: {
      userId: 1,
      displayName: 'Author'
    },
    ...overrides
  })

  const resolvePostRoute = (post: PostSummary, boardUrl: string, linkQuery?: LocationQueryRaw) => ({
    name: 'post-detail',
    params: {
      boardUrl,
      postId: post.postId
    },
    query: linkQuery
  })

  const resolveBoardRoute = (_post: PostSummary, boardUrl: string): RouteLocationRaw => ({
    name: 'board-detail',
    params: {
      boardUrl
    }
  })

  it('preserves pagination query in post detail links when provided', () => {
    const wrapper = mount(PostList, {
      props: {
        posts: [
          createPost({
            postId: 101,
            boardUrl: 'free',
            title: 'Page link test',
            viewCount: 1,
            likeCount: 2,
            commentCount: 3
          })
        ],
        linkQuery: {
          page: '3',
          q: 'vue'
        },
        resolvePostRoute
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
      name: 'post-detail',
      params: {
        boardUrl: 'free',
        postId: 101
      },
      query: {
        page: '3',
        q: 'vue'
      }
    })
  })

  it('uses an injected post route resolver when provided', () => {
    const wrapper = mount(PostList, {
      props: {
        posts: [
          createPost({
            postId: 102,
            boardUrl: 'free',
            title: 'Injected route',
          })
        ],
        linkQuery: {
          page: '2'
        },
        resolvePostRoute: (post, boardUrl, linkQuery) => ({
          name: 'post-detail',
          params: {
            boardUrl,
            postId: post.postId
          },
          query: linkQuery
        })
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

    expect(wrapper.findComponent(RouterLinkStub).props('to')).toEqual({
      name: 'post-detail',
      params: {
        boardUrl: 'free',
        postId: 102
      },
      query: {
        page: '2'
      }
    })
  })

  it('keeps posts non-interactive when the injected route resolver returns null', () => {
    const wrapper = mount(PostList, {
      props: {
        posts: [
          createPost({
            postId: 103,
            boardUrl: 'free',
            title: 'Suppressed route',
          })
        ],
        resolvePostRoute: () => null
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
    expect(wrapper.text()).toContain('Suppressed route')
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
        posts: [createPost()]
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
    const columns = table.props('columns') as Array<{ key: string, label: string, align?: string, sortable?: boolean }>

    expect(columns.map((column) => column.key)).toEqual([
      'postId',
      'title',
      'author',
      'likeCount',
      'viewCount',
      'createdAt'
    ])
    expect(columns.find((column) => column.key === 'author')?.label).toBe('common.author')
    expect(columns.find((column) => column.key === 'createdAt')?.label).toBe('common.date')
    expect(columns.find((column) => column.key === 'viewCount')?.align).toBe('right')
    expect(columns.find((column) => column.key === 'postId')?.sortable).toBe(true)
    expect(columns.find((column) => column.key === 'createdAt')?.sortable).toBe(false)
    const rowClass = table.props('rowClass') as (item: { postId: number }) => string
    expect(rowClass({ postId: 1 })).toContain('post-list-row')
  })

  it('maps the number header sort back to the default createdAt ordering', async () => {
    const SortProxyTable = defineComponent({
      name: 'BaseTable',
      emits: ['sort'],
      template: '<button type="button" data-testid="sort-trigger" @click="$emit(\'sort\', \'postId\')">sort</button>'
    })

    const wrapper = mount(PostList, {
      props: {
        posts: [createPost()],
        currentSort: 'createdAt,desc'
      },
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          BaseTable: SortProxyTable,
          RouterLink: RouterLinkStub,
          UserMenu: true
        }
      }
    })

    await wrapper.get('[data-testid="sort-trigger"]').trigger('click')

    expect(wrapper.emitted('update:sort')?.[0]).toEqual(['createdAt,asc'])
  })

  it('passes the active sort state to BaseTable using display column keys', () => {
    const BaseTableStub = defineComponent({
      name: 'BaseTable',
      props: {
        currentSortKey: {
          type: String,
          default: null
        },
        currentSortDirection: {
          type: String,
          default: null
        }
      },
      template: '<div />'
    })

    const wrapper = mount(PostList, {
      props: {
        posts: [createPost()],
        currentSort: 'createdAt,desc'
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

    expect(table.props('currentSortKey')).toBe('postId')
    expect(table.props('currentSortDirection')).toBe('desc')
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
        posts: [createPost()],
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
          createPost({
            postId: 202,
            title: 'Invalid target post',
            viewCount: 4,
            likeCount: 5,
            commentCount: 0
          })
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
    expect(wrapper.text()).toContain('Invalid target post')
  })

  it('intercepts inquiry clicks without navigating', async () => {
    const wrapper = mount(PostList, {
      props: {
        posts: [
          createPost({
            postId: 303,
            boardUrl: 'inquiry',
            title: 'Inquiry post',
            viewCount: 4,
            likeCount: 1,
            commentCount: 0,
            inquiryAnswered: false
          })
        ],
        interceptInquiry: true,
        shouldInterceptPost: (post) => post.boardUrl === 'inquiry'
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

    const inquiryButton = wrapper.findAll('button').find((button) => button.text().includes('Inquiry post'))
    expect(inquiryButton).toBeTruthy()
    await inquiryButton!.trigger('click')

    expect(wrapper.emitted('inquiry-click')).toHaveLength(1)
    expect(wrapper.findComponent(RouterLinkStub).exists()).toBe(false)
  })

  it('shows inquiry status from the parent board url when the post summary omits boardUrl', () => {
    const wrapper = mount(PostList, {
      props: {
        boardUrl: 'inquiry',
        showInquiryStatus: (_post, boardUrl) => boardUrl === 'inquiry',
        posts: [
          createPost({
            inquiryAnswered: true
          })
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

    expect(wrapper.text()).toContain('board.inquiryStatus.answered')
  })

  it('renders deleted users with a fallback label when the display name is missing', () => {
    const wrapper = mount(PostList, {
      props: {
        posts: [
          createPost({
            author: {
              userId: 99,
              displayName: ''
            }
          })
        ]
      },
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          UserMenu: true
        },
        components: {
          BaseTable
        }
      }
    })

    expect(wrapper.find('.nv-post-author-fallback').text()).toContain('\uD0C8\uD1F4\uD55C \uC0AC\uC6A9\uC790')
  })

  it('renders desktop board and post links through BaseTable slots', () => {
    const wrapper = mount(PostList, {
      props: {
        posts: [
          createPost({
            postId: 404,
            boardUrl: 'free',
            boardName: 'Free Board',
            title: 'Desktop slot post',
            viewCount: 7,
            likeCount: 9,
            commentCount: 2,
            author: {
              userId: 1,
              displayName: 'Author'
            }
          })
        ],
        showBoardName: true,
        resolvePostRoute
      },
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          UserMenu: true
        },
        components: {
          BaseTable
        }
      }
    })

    const targets = wrapper.findAllComponents(RouterLinkStub).map((link) => link.props('to'))

    expect(targets).toContain('/board/free')
    expect(targets).toContainEqual({
      name: 'post-detail',
      params: {
        boardUrl: 'free',
        postId: 404
      },
      query: undefined
    })
  })

  it('uses an injected board route resolver for desktop board links', () => {
    const wrapper = mount(PostList, {
      props: {
        posts: [
          createPost({
            postId: 405,
            boardUrl: 'free',
            boardName: 'Free Board',
            title: 'Board route resolver',
            author: {
              userId: 1,
              displayName: 'Author'
            }
          })
        ],
        showBoardName: true,
        resolvePostRoute,
        resolveBoardRoute
      },
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          UserMenu: true
        },
        components: {
          BaseTable
        }
      }
    })

    const targets = wrapper.findAllComponents(RouterLinkStub).map((link) => link.props('to'))

    expect(targets).toContainEqual({
      name: 'board-detail',
      params: {
        boardUrl: 'free'
      }
    })
  })

  it('can suppress post metadata that is unavailable for normalized summaries', () => {
    const wrapper = mount(PostList, {
      props: {
        posts: [
          createPost({
            postId: 505,
            boardUrl: 'free',
            title: 'Partial summary',
            commentCount: 3,
            isNotice: true,
            isSecret: true,
            hasImage: true
          })
        ],
        showNoticeBadge: false,
        showCommentCount: false,
        showPreviewIndicator: false,
        showSecretIndicator: false
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

    expect(wrapper.find('.nv-post-badge-notice').exists()).toBe(false)
    expect(wrapper.find('.nv-post-comment-count').exists()).toBe(false)
    expect(wrapper.find('.nv-post-mobile-comments').exists()).toBe(false)
    expect(wrapper.html()).not.toContain('board.writePost.secret')
  })

  it('emits load-more from the mobile infinite action when enabled', async () => {
    const wrapper = mount(PostList, {
      props: {
        posts: [createPost()],
        enableInfiniteLoad: true,
        hasMorePosts: true,
        isLoadingMore: false
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

    const loadMoreButton = wrapper.get('.nv-mobile-load-more')
    await loadMoreButton.trigger('click')

    expect(wrapper.emitted('load-more')).toHaveLength(1)

    await wrapper.setProps({ isLoadingMore: true })
    await loadMoreButton.trigger('click')

    expect(wrapper.emitted('load-more')).toHaveLength(1)
  })
})
