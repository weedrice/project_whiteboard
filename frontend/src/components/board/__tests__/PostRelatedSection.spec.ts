import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import PostRelatedSection from '../PostRelatedSection.vue'

vi.mock('@/features/board/posts/queries/usePost', async () => {
  const { ref } = await import('vue')

  return {
    usePost: () => ({
      useRelatedPosts: () => ({
        data: ref([{
          postId: 2,
          boardUrl: 'free',
          boardName: 'Free',
          title: 'Related post',
          authorName: 'Related author',
          createdAt: '2026-08-06T00:00:00',
          viewCount: 1,
          likeCount: 0,
          commentCount: 0,
          isNotice: false,
          isNsfw: false,
          isSpoiler: false,
        }]),
        isLoading: ref(false),
        isError: ref(false),
        refetch: vi.fn(),
      }),
    }),
  }
})

vi.mock('@/composables/useIntersectionObserver', async () => {
  const { ref } = await import('vue')

  return {
    useIntersectionObserver: () => ({
      targetRef: ref<HTMLElement | null>(null),
    }),
  }
})

describe('PostRelatedSection', () => {
  it('omits the kicker and author while rendering compact related-post titles', () => {
    const HomePostCardStub = defineComponent({
      name: 'HomePostCard',
      props: {
        post: { type: Object, required: true },
        variant: String,
        showMediaPreview: Boolean,
        showBody: Boolean,
        showAuthor: Boolean,
      },
      template: '<article class="nv-home-card"><h3 class="nv-home-card-title">{{ post.title }}</h3></article>',
    })
    const wrapper = mount(PostRelatedSection, {
      props: { postId: 1 },
      global: {
        mocks: {
          $t: (key: string) => key,
        },
        stubs: {
          ErrorState: true,
          HomePostCard: HomePostCardStub,
        },
      },
    })

    const card = wrapper.getComponent(HomePostCardStub)

    expect(wrapper.text()).not.toContain('board.postDetail.relatedKicker')
    expect(wrapper.get('h2').text()).toBe('board.postDetail.relatedTitle')
    expect(card.props('variant')).toBe('compact')
    expect(card.props('showAuthor')).toBe(false)
    expect(card.get('.nv-home-card-title').text()).toBe('Related post')
  })
})
