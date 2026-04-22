import { defineComponent, h } from 'vue'
import { mount, RouterLinkStub } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const state = vi.hoisted(() => ({
    featured: { __v_isRef: true, value: null as any },
    editorPicks: { __v_isRef: true, value: [] as any[] },
    trending: { __v_isRef: true, value: [] as any[] },
    liveActivity: { __v_isRef: true, value: [] as any[] },
    spotlightBoards: { __v_isRef: true, value: [] as any[] },
    posts: { __v_isRef: true, value: [] as any[] },
    boards: { __v_isRef: true, value: [] as any[] },
    stats: { __v_isRef: true, value: { boardCount: 0, postCount: 0, liveCount: 0 } },
    isLoading: { __v_isRef: true, value: false },
    isError: { __v_isRef: true, value: false },
    isBoardsLoading: { __v_isRef: true, value: false },
    isBoardsError: { __v_isRef: true, value: false },
    refetch: vi.fn(),
}))

vi.mock('@unhead/vue', () => ({
    useHead: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string) => key,
    }),
}))

vi.mock('@/composables/useHomeLanding', () => ({
    useHomeLanding: () => state,
}))

const { default: HomeFeed } = await import('../HomeFeed.vue')

const HomePostCardStub = defineComponent({
    name: 'HomePostCardStub',
    props: {
        post: { type: Object, required: true },
        variant: { type: String, default: 'grid' },
    },
    setup(props) {
        return () => h('div', {
            'data-testid': 'post-card',
            'data-post-id': String((props.post as { postId: number }).postId),
            'data-variant': props.variant,
        }, String((props.post as { postId: number }).postId))
    },
})

const HomeActivityListStub = defineComponent({
    name: 'HomeActivityListStub',
    props: {
        posts: { type: Array, default: () => [] },
    },
    setup(props) {
        return () => h('div', { 'data-testid': 'activity-list' }, JSON.stringify(props.posts))
    },
})

const EmptyStateStub = defineComponent({
    name: 'EmptyStateStub',
    setup() {
        return () => h('div', { 'data-testid': 'empty-state' }, 'empty')
    },
})

const ErrorStateStub = defineComponent({
    name: 'ErrorStateStub',
    setup() {
        return () => h('div', { 'data-testid': 'error-state' }, 'error')
    },
})

const PostListSkeletonStub = defineComponent({
    name: 'PostListSkeletonStub',
    setup() {
        return () => h('div', { 'data-testid': 'post-skeleton' }, 'loading')
    },
})

const makePost = (postId: number, title = `Post ${postId}`) => ({
    postId,
    boardUrl: 'free',
    boardName: 'Free',
    authorName: `Author ${postId}`,
    title,
    viewCount: 1,
    likeCount: 0,
    commentCount: 0,
    isNotice: false,
    isNsfw: false,
    isSpoiler: false,
    createdAt: '2025-01-01',
})

describe('HomeFeed', () => {
    beforeEach(() => {
        state.featured.value = null
        state.editorPicks.value = []
        state.trending.value = []
        state.liveActivity.value = []
        state.spotlightBoards.value = []
        state.posts.value = []
        state.boards.value = []
        state.stats.value = { boardCount: 0, postCount: 0, liveCount: 0 }
        state.isLoading.value = false
        state.isError.value = false
        state.isBoardsLoading.value = false
        state.isBoardsError.value = false
        state.refetch.mockReset()
    })

    it('filters the fallback hero post out of the editor picks section', () => {
        state.editorPicks.value = [makePost(101, 'Hero'), makePost(202, 'Second pick')]
        state.trending.value = [makePost(303, 'Trending')]
        state.liveActivity.value = [makePost(404, 'Live')]

        const wrapper = mount(HomeFeed, {
            global: {
                mocks: {
                    $t: (key: string) => key,
                },
                stubs: {
                    RouterLink: RouterLinkStub,
                    HomePostCard: HomePostCardStub,
                    HomeActivityList: HomeActivityListStub,
                    EmptyState: EmptyStateStub,
                    ErrorState: ErrorStateStub,
                    PostListSkeleton: PostListSkeletonStub,
                },
            },
        })
        const cards = wrapper.findAll('[data-testid="post-card"]')
        expect(cards.map((card) => card.attributes('data-post-id'))).toEqual(['101', '202', '303'])
        expect(cards.filter((card) => card.attributes('data-post-id') === '101')).toHaveLength(1)
        expect(cards.find((card) => card.attributes('data-variant') === 'featured')?.attributes('data-post-id')).toBe('101')
        expect(cards.find((card) => card.attributes('data-variant') === 'compact')?.attributes('data-post-id')).toBe('202')
    })

    it('shows the empty state when every landing section is empty', () => {
        const wrapper = mount(HomeFeed, {
            global: {
                mocks: {
                    $t: (key: string) => key,
                },
                stubs: {
                    RouterLink: RouterLinkStub,
                    HomePostCard: HomePostCardStub,
                    HomeActivityList: HomeActivityListStub,
                    EmptyState: EmptyStateStub,
                    ErrorState: ErrorStateStub,
                    PostListSkeleton: PostListSkeletonStub,
                },
            },
        })
        expect(wrapper.find('[data-testid="empty-state"]').exists()).toBe(true)
        expect(wrapper.findAll('[data-testid="post-card"]')).toHaveLength(0)
    })
})
