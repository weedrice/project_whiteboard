import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import PostDetailArticleContent from '@/components/board/PostDetailArticleContent.vue'
import type { PostDetailViewModel } from '@/features/board/posts/detail/usePostDetailViewModel'

const postView = (overrides: Partial<PostDetailViewModel> = {}): PostDetailViewModel => ({
  postId: 1,
  title: 'Post title',
  createdAt: '2026-07-01T00:00:00',
  viewCount: 10,
  commentCount: 2,
  likeCount: 4,
  liked: false,
  scrapped: false,
  tags: ['alpha', 'beta'],
  boardName: 'Free',
  boardUrl: 'free',
  authorUserId: 7,
  authorDisplayName: 'Author',
  ...overrides,
})

function mountContent(overrides: Partial<InstanceType<typeof PostDetailArticleContent>['$props']> = {}) {
  return mount(PostDetailArticleContent, {
    props: {
      postView: postView(),
      postContents: '<p>본문</p>',
      assignContentRef: vi.fn(),
      currentUrl: 'https://noviis.kr/free/1',
      compactUrl: 'noviis.kr/free/1',
      showCopyHint: true,
      isBlurred: true,
      timeLeft: 3,
      isAuthenticated: true,
      canReport: true,
      isLikeAnimating: false,
      isBookmarkAnimating: false,
      ...overrides,
    },
    global: {
      mocks: {
        $t: (key: string, params?: Record<string, unknown>) => params ? `${key}:${params.time}` : key,
      },
      stubs: {
        BaseButton: {
          template: '<button type="button" class="base-button"><slot /></button>',
        },
        PostContentView: {
          props: ['content', 'sandboxTitle'],
          template: '<article data-testid="content" :data-title="sandboxTitle" v-html="content" />',
        },
        PostTags: {
          props: ['modelValue', 'readOnly', 'boardUrl', 'compact'],
          template: '<button type="button" data-testid="tag" @click="$emit(\'tag-click\', modelValue[0])">{{ modelValue.join(\',\') }}</button>',
        },
        PostDetailReactionBar: {
          template: `
            <div>
              <button type="button" data-testid="like" @click="$emit('like')">like</button>
              <button type="button" data-testid="bookmark" @click="$emit('bookmark')">bookmark</button>
              <button type="button" data-testid="share" @click="$emit('share')">share</button>
              <button type="button" data-testid="report" @click="$emit('report')">report</button>
            </div>
          `,
        },
      },
    },
  })
}

describe('PostDetailArticleContent', () => {
  it('renders article helpers and forwards user actions', async () => {
    const wrapper = mountContent()

    expect(wrapper.get('[data-testid="content"]').html()).toContain('본문')
    expect(wrapper.get('.nv-post-copy-hint').text()).toBe('common.messages.urlCopied')
    expect(wrapper.get('.nv-post-url-chip').text()).toContain('noviis.kr/free/1')
    expect(wrapper.text()).toContain('board.postDetail.spoilerTimer:3')
    expect(wrapper.get('[data-testid="tag"]').text()).toBe('alpha,beta')

    await wrapper.get('.nv-post-url-chip').trigger('click')
    await wrapper.get('.base-button').trigger('click')
    await wrapper.get('[data-testid="tag"]').trigger('click')
    await wrapper.get('[data-testid="like"]').trigger('click')
    await wrapper.get('[data-testid="bookmark"]').trigger('click')
    await wrapper.get('[data-testid="share"]').trigger('click')
    await wrapper.get('[data-testid="report"]').trigger('click')

    expect(wrapper.emitted('copy-url')).toHaveLength(1)
    expect(wrapper.emitted('reveal-spoiler')).toHaveLength(1)
    expect(wrapper.emitted('tag-click')?.[0]).toEqual(['alpha'])
    expect(wrapper.emitted('like')).toHaveLength(1)
    expect(wrapper.emitted('bookmark')).toHaveLength(1)
    expect(wrapper.emitted('share')).toHaveLength(1)
    expect(wrapper.emitted('report')).toHaveLength(1)
  })

  it('omits optional spoiler, copy hint and tags when inactive', () => {
    const wrapper = mountContent({
      postView: postView({ tags: [] }),
      showCopyHint: false,
      isBlurred: false,
    })

    expect(wrapper.find('.nv-post-copy-hint').exists()).toBe(false)
    expect(wrapper.find('.nv-post-spoiler').exists()).toBe(false)
    expect(wrapper.find('[data-testid="tag"]').exists()).toBe(false)
  })
})
