import { defineComponent, h, type PropType } from 'vue'
import { mount, RouterLinkStub } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useHead } from '@unhead/vue'
import type { BoardListItem, FeedPost, HomeLandingPeriod, HomeLandingStats } from '@/types'

type MockRef<T> = {
    __v_isRef: true
    value: T
}

const state = vi.hoisted(() => {
    const mockRef = <T>(value: T): MockRef<T> => ({ __v_isRef: true, value })

    return {
        featured: mockRef<FeedPost | null>(null),
        editorPicks: mockRef<FeedPost[]>([]),
        trending: mockRef<FeedPost[]>([]),
        liveActivity: mockRef<FeedPost[]>([]),
        spotlightBoards: mockRef<BoardListItem[]>([]),
        posts: mockRef<FeedPost[]>([]),
        boards: mockRef<BoardListItem[]>([]),
        selectedPeriod: mockRef<HomeLandingPeriod>('24h'),
        setPeriod: vi.fn(),
        stats: mockRef<HomeLandingStats>({
            boardCount: 0,
            postCount: 0,
            liveCount: 0,
            onlineCount: 0,
            postsToday: 0,
            postsTodayDeltaPercent: null,
            activeBoardCount: 0,
            newMembersLast24Hours: 0,
            commentsToday: 0,
        }),
        isLoading: mockRef(false),
        isFetching: mockRef(false),
        isError: mockRef(false),
        isBoardsLoading: mockRef(false),
        isBoardsError: mockRef(false),
        refetch: vi.fn(),
    }
})

vi.mock('@unhead/vue', () => ({
    useHead: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string, params?: Record<string, string>) => {
            if (key === 'home.landing.statsCards.postsTodayDeltaVsYesterday') {
                return `${params?.value} vs yesterday`
            }
            if (key === 'home.landing.statsCards.noComparisonData') {
                return 'No comparison data yet'
            }
            return key
        },
        locale: { __v_isRef: true, value: 'en' },
    }),
}))

vi.mock('@/composables/useHomeLanding', () => ({
    useHomeLanding: () => state,
}))

const { default: HomeFeed } = await import('../HomeFeed.vue')

const HomePostCardStub = defineComponent({
    name: 'HomePostCardStub',
    props: {
        post: { type: Object as PropType<FeedPost>, required: true },
        variant: { type: String, default: 'grid' },
    },
    setup(props) {
        return () => h('div', {
            'data-testid': 'post-card',
            'data-post-id': String(props.post.postId),
            'data-variant': props.variant,
        }, String(props.post.postId))
    },
})

const HomeActivityListStub = defineComponent({
    name: 'HomeActivityListStub',
    props: {
        posts: { type: Array as PropType<FeedPost[]>, default: () => [] },
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

const HomeLandingSkeletonStub = defineComponent({
    name: 'HomeLandingSkeletonStub',
    setup() {
        return () => h('div', { 'data-testid': 'home-skeleton' }, 'loading')
    },
})

const makePost = (postId: number, title = `Post ${postId}`): FeedPost => ({
    postId,
    boardUrl: 'free',
    boardName: 'Free',
    author: {
        userId: postId,
        displayName: `Author ${postId}`,
    },
    authorName: `Author ${postId}`,
    title,
    viewCount: 1,
    likeCount: 0,
    commentCount: 0,
    isNotice: false,
    isNsfw: false,
    isSpoiler: false,
    createdAt: '2025-01-01',
    liked: false,
    scrapped: false,
    subscribed: false,
})

const makeBoard = (boardId: number): BoardListItem => ({
    boardId,
    boardUrl: `board-${boardId}`,
    boardName: `Board ${boardId}`,
    sortOrder: boardId,
    subscriberCount: boardId * 10,
    postCount: boardId * 100,
    isSubscribed: false,
    isActive: true,
    isPublic: true,
    subscriptionAccessible: true,
})

const isHeadOptionsWithTitle = (value: unknown): value is { title: MockRef<string> } => {
    if (typeof value !== 'object' || value === null || !('title' in value)) {
        return false
    }

    const title = value.title
    return typeof title === 'object'
        && title !== null
        && 'value' in title
        && typeof title.value === 'string'
}

describe('HomeFeed', () => {
    beforeEach(() => {
        state.featured.value = null
        state.editorPicks.value = []
        state.trending.value = []
        state.liveActivity.value = []
        state.spotlightBoards.value = []
        state.posts.value = []
        state.boards.value = []
        state.selectedPeriod.value = '24h'
        state.setPeriod.mockReset()
        state.stats.value = {
            boardCount: 0,
            postCount: 0,
            liveCount: 0,
            onlineCount: 0,
            postsToday: 0,
            postsTodayDeltaPercent: null,
            activeBoardCount: 0,
            newMembersLast24Hours: 0,
            commentsToday: 0,
        }
        state.isLoading.value = false
        state.isFetching.value = false
        state.isError.value = false
        state.isBoardsLoading.value = false
        state.isBoardsError.value = false
        state.refetch.mockReset()
        document.title = ''
    })

    it('registers the home title with useHead', () => {
        document.title = 'Post title - Board'

        mount(HomeFeed, {
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
                    HomeLandingSkeleton: HomeLandingSkeletonStub,
                },
            },
        })

        const headOptions = vi.mocked(useHead).mock.calls.at(-1)?.[0]

        expect(isHeadOptionsWithTitle(headOptions)).toBe(true)
        if (!isHeadOptionsWithTitle(headOptions)) {
            throw new Error('Expected useHead to receive a reactive title option')
        }
        expect(headOptions.title.value).toBe('common.appName')
    })

    it('keeps the fallback hero post out of duplicate post cards and renders site stats', () => {
        state.editorPicks.value = [makePost(101, 'Hero'), makePost(202, 'Second pick')]
        state.trending.value = [makePost(303, 'Trending')]
        state.liveActivity.value = [makePost(404, 'Live')]
        state.spotlightBoards.value = Array.from({ length: 6 }, (_, index) => makeBoard(index + 1))
        state.stats.value = {
            boardCount: 11,
            postCount: 8421,
            liveCount: 1824,
            onlineCount: 1247,
            postsToday: 312,
            postsTodayDeltaPercent: 18,
            activeBoardCount: 11,
            newMembersLast24Hours: 47,
            commentsToday: 1824,
        }

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
                    HomeLandingSkeleton: HomeLandingSkeletonStub,
                },
            },
        })
        const cards = wrapper.findAll('[data-testid="post-card"]')
        expect(cards.map((card) => card.attributes('data-post-id'))).toEqual(['101', '303'])
        expect(cards.filter((card) => card.attributes('data-post-id') === '101')).toHaveLength(1)
        expect(cards.find((card) => card.attributes('data-variant') === 'featured')?.attributes('data-post-id')).toBe('101')
        expect(cards.some((card) => card.attributes('data-variant') === 'compact')).toBe(false)
        expect(wrapper.text()).toContain('1,247')
        expect(wrapper.text()).toContain('8,421')
        expect(wrapper.text()).toContain('home.landing.totalPosts')
        expect(wrapper.text()).not.toContain('home.landing.storiesWorthReading')
        expect(wrapper.text()).not.toContain('home.landing.discover')
        expect(wrapper.text()).toContain('home.landing.trendingNow')
        expect(wrapper.text()).toContain('home.landing.liveActivityTitle')
        expect(wrapper.text()).toContain('home.landing.siteStats')
        expect(wrapper.text()).toContain('312')
        expect(wrapper.text()).toContain('+18% vs yesterday')
        expect(wrapper.text()).toContain('+47')
        expect(wrapper.text()).toContain('1,824')
    })

    it('does not use latest activity as the curated hero post', () => {
        state.liveActivity.value = [makePost(404, 'Latest activity')]

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
                    HomeLandingSkeleton: HomeLandingSkeletonStub,
                },
            },
        })

        expect(wrapper.find('[data-testid="empty-state"]').exists()).toBe(false)
        expect(wrapper.find('[data-testid="activity-list"]').text()).toContain('404')
        expect(wrapper.findAll('[data-testid="post-card"]')).toHaveLength(0)
        expect(wrapper.text()).not.toContain('home.landing.featuredLoading')
        expect(wrapper.text()).toContain('home.landing.siteStats')
    })

    it('links the remaining board strip area to the all boards page', () => {
        state.editorPicks.value = [makePost(101, 'Hero')]
        state.spotlightBoards.value = Array.from({ length: 6 }, (_, index) => makeBoard(index + 1))
        state.boards.value = state.spotlightBoards.value

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
                    HomeLandingSkeleton: HomeLandingSkeletonStub,
                },
            },
        })

        const boardLinks = wrapper.findAllComponents(RouterLinkStub)
            .filter((link) => String(link.props('to')).startsWith('/board/'))
        const viewAllSlot = wrapper.findAllComponents(RouterLinkStub)
            .find((link) => link.text().includes('common.viewAll') && link.text().includes('home.landing.topBoards'))

        expect(boardLinks).toHaveLength(6)
        expect(viewAllSlot?.props('to')).toBe('/boards')
        expect(viewAllSlot?.attributes('style')).toContain('--remaining-board-slots: 1')
    })

    it('keeps the home shell visible with actions when every landing source is empty', () => {
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
                    HomeLandingSkeleton: HomeLandingSkeletonStub,
                },
            },
        })
        expect(wrapper.find('[data-testid="empty-state"]').exists()).toBe(false)
        expect(wrapper.findAll('[data-testid="post-card"]')).toHaveLength(0)
        expect(wrapper.text()).toContain('home.landing.emptyTitle')
        expect(wrapper.text()).toContain('home.landing.emptyDescription')
        expect(wrapper.text()).toContain('home.landing.emptyPrimaryAction')
        expect(wrapper.text()).toContain('home.landing.emptySecondaryAction')
        expect(wrapper.text()).toContain('home.landing.siteStats')
        expect(wrapper.text()).toContain('home.landing.emptyBoards')
        expect(wrapper.text()).toContain('home.landing.trendingEmpty')
        expect(wrapper.text()).toContain('home.landing.liveActivityEmpty')

        const links = wrapper.findAllComponents(RouterLinkStub)
        expect(links.some((link) => link.props('to') === '/board/create')).toBe(true)
        expect(links.some((link) => link.props('to') === '/boards')).toBe(true)
    })

    it('uses the home landing skeleton while the landing data is loading', () => {
        state.isLoading.value = true

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
                    HomeLandingSkeleton: HomeLandingSkeletonStub,
                },
            },
        })

        expect(wrapper.find('[data-testid="home-skeleton"]').exists()).toBe(true)
        expect(wrapper.find('[data-testid="empty-state"]').exists()).toBe(false)
    })

    it('changes the trending period through the filter bar', async () => {
        state.editorPicks.value = [makePost(101, 'Hero')]

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
                    HomeLandingSkeleton: HomeLandingSkeletonStub,
                },
            },
        })

        const target = wrapper.findAll('button').find((button) => button.text() === 'home.landing.trendingPeriods.last7Days')
        expect(target).toBeTruthy()
        expect(target?.attributes('aria-pressed')).toBe('false')
        await target!.trigger('click')
        expect(state.setPeriod).toHaveBeenCalledWith('7d')
    })
})
