import { flushPromises, mount } from '@vue/test-utils'
import { AxiosError } from 'axios'
import { reactive, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import UserProfilePage from '../UserProfilePage.vue'
import { createDeferred } from '@/test/async'

const mocks = vi.hoisted(() => ({
  overviewPublic: true,
  postsPublic: true,
  commentsPublic: true,
  ownerAccess: true,
  postsFailure: null as Error | null,
  route: null as { params: { userId: string } } | null,
  authStore: {
    user: { userId: 7 },
    sessionGeneration: 1,
  },
  updateBadge: vi.fn(),
  addToast: vi.fn(),
  refetchBadges: vi.fn(),
  refetchProfile: vi.fn(),
  refetchPosts: vi.fn(),
  refetchComments: vi.fn(),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...await importOriginal<typeof import('vue-router')>(),
  useRoute: () => mocks.route,
}))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@unhead/vue', () => ({ useHead: vi.fn() }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => mocks.authStore }))
vi.mock('@/stores/toast', () => ({ useToastStore: () => ({ addToast: mocks.addToast }) }))
vi.mock('@/features/user/useUser', () => ({
  useUser: () => ({
    useUserProfile: () => ({
      data: ref({ userId: 7, displayName: 'Novi', profileImageUrl: null, createdAt: '2026-01-01', postCount: 2, commentCount: 1, profileOverviewPublic: mocks.overviewPublic, profilePostsPublic: mocks.postsPublic, profileCommentsPublic: mocks.commentsPublic, canViewOverview: mocks.ownerAccess || mocks.overviewPublic, canViewPosts: mocks.ownerAccess || mocks.postsPublic, canViewComments: mocks.ownerAccess || mocks.commentsPublic }),
      isLoading: ref(false), isError: ref(false), refetch: mocks.refetchProfile,
    }),
    useUserBadges: () => ({
      data: ref([
        { badgeCode: 'FIRST', name: 'First', description: 'First badge', tier: 'BRONZE', acquired: true, representative: false },
        { badgeCode: 'LOCKED', name: 'Locked', description: '', tier: 'GOLD', acquired: false, representative: false },
      ]),
      isLoading: ref(false), isError: ref(false), error: ref(null), refetch: mocks.refetchBadges,
    }),
    useUpdateRepresentativeBadge: () => ({ mutateAsync: mocks.updateBadge, isPending: ref(false) }),
    usePublicProfilePosts: () => ({
      data: ref({ content: [{ postId: 1, title: 'Post' }], totalPages: 2 }),
      isLoading: ref(false), isError: ref(!!mocks.postsFailure), error: ref(mocks.postsFailure), refetch: mocks.refetchPosts,
    }),
    usePublicProfileComments: () => ({
      data: ref({ content: [{ commentId: 2, content: '![emoticon](/uploads/happy.png)', post: { boardUrl: 'news', postId: 1, title: 'Post' } }], totalPages: 2 }),
      isLoading: ref(false), isError: ref(false), error: ref(null), refetch: mocks.refetchComments,
    }),
  }),
}))

function mountProfile() {
  mocks.route = reactive({ params: { userId: '7' } })
  return mount(UserProfilePage, {
    global: {
      stubs: {
        BaseButton: { template: '<button @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
        BaseSegmentedControl: {
          template: '<div><button data-posts @click="$emit(\'update:modelValue\', \'posts\')">posts</button><button data-comments @click="$emit(\'update:modelValue\', \'comments\')">comments</button></div>',
        },
        UserAvatar: true, UserMenu: true, BaseSpinner: true,
        ErrorState: { template: '<button data-retry @click="$emit(\'retry\')">retry</button>' },
        PostList: { template: '<div data-post-list />' },
        Pagination: { template: '<button data-page @click="$emit(\'page-change\', 1)">page</button>' },
        RouterLink: {
          props: ['to'],
          template: '<a :data-to="to"><slot /></a>',
        },
        FileText: true, MessageSquare: true, Award: true, User: true,
      },
    },
  })
}

describe('UserProfilePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.overviewPublic = true
    mocks.postsPublic = true
    mocks.commentsPublic = true
    mocks.ownerAccess = true
    mocks.postsFailure = null
    mocks.authStore.user = { userId: 7 }
    mocks.authStore.sessionGeneration = 1
    mocks.updateBadge.mockResolvedValue(undefined)
  })

  it('hides private overview, counts and activity while keeping all tabs available', async () => {
    mocks.overviewPublic = false
    mocks.postsPublic = false
    mocks.commentsPublic = false
    mocks.ownerAccess = false
    mocks.authStore.user = { userId: 8 }
    const wrapper = mountProfile()
    expect(wrapper.text()).toContain('user.publicProfile.privateSection')
    expect(wrapper.text()).not.toContain('user.profile.joined')
    expect(wrapper.text()).not.toContain('First')
    await wrapper.find('[data-posts]').trigger('click')
    expect(wrapper.text()).toContain('user.publicProfile.privateSection')
    expect(wrapper.find('[data-post-list]').exists()).toBe(false)
    await wrapper.find('[data-comments]').trigger('click')
    expect(wrapper.text()).toContain('user.publicProfile.privateSection')
    expect(wrapper.find('.comment-content-list').exists()).toBe(false)
  })

  it('allows owners to view hidden sections with a private-to-others indicator', async () => {
    mocks.overviewPublic = false
    mocks.postsPublic = false
    const wrapper = mountProfile()
    expect(wrapper.text()).toContain('First')
    expect(wrapper.text()).toContain('user.publicProfile.privateToOthers')
    await wrapper.find('[data-posts]').trigger('click')
    expect(wrapper.find('[data-post-list]').exists()).toBe(true)
    expect(wrapper.text()).toContain('user.publicProfile.privateToOthers')
  })

  it('replaces cached post content with privacy feedback after a dedicated 403', async () => {
    const failure = new AxiosError('private')
    failure.response = { status: 403, data: { error: { code: 'U012' } } } as NonNullable<typeof failure.response>
    mocks.postsFailure = failure
    const wrapper = mountProfile()
    await wrapper.find('[data-posts]').trigger('click')
    expect(wrapper.text()).toContain('user.publicProfile.privateSection')
    expect(wrapper.find('[data-post-list]').exists()).toBe(false)
    expect(wrapper.find('[data-retry]').exists()).toBe(false)
    expect(wrapper.find('[data-page]').exists()).toBe(false)
  })

  it('renders the overview and updates a representative badge', async () => {
    const wrapper = mountProfile()
    expect(wrapper.text()).toContain('Novi')
    expect(wrapper.text()).toContain('First')

    const badgeButton = wrapper.findAll('button').find((button) => button.text().includes('setRepresentative'))!
    await badgeButton.trigger('click')
    expect(mocks.updateBadge).toHaveBeenCalledWith('FIRST')
    expect(mocks.addToast).toHaveBeenCalledWith('user.publicProfile.representativeUpdated', 'success')
    expect(mocks.refetchBadges).not.toHaveBeenCalled()
  })

  it('switches between posts and comments and resets when the route changes', async () => {
    const wrapper = mountProfile()
    await wrapper.find('[data-posts]').trigger('click')
    expect(wrapper.find('[data-post-list]').exists()).toBe(true)
    await wrapper.find('[data-page]').trigger('click')
    await wrapper.find('[data-comments]').trigger('click')
    expect(wrapper.get('.comment-content-list img').attributes('src')).toContain('/uploads/happy.png')
    expect(wrapper.get('li > a').attributes('data-to')).toBe('/board/news/post/1/#comment-2')
    expect(wrapper.text()).not.toContain('![emoticon]')

    mocks.route!.params.userId = '8'
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('user.publicProfile.overviewDescription')
  })

  it('shows a failure toast when representative update fails', async () => {
    mocks.updateBadge.mockRejectedValueOnce(new Error('failed'))
    const wrapper = mountProfile()
    const badgeButton = wrapper.findAll('button').find((button) => button.text().includes('setRepresentative'))!
    await badgeButton.trigger('click')
    expect(mocks.addToast).toHaveBeenCalledWith('user.publicProfile.representativeUpdateFailed', 'error')
  })

  it('ignores a delayed representative badge success after the profile route changes', async () => {
    const update = createDeferred<void>()
    mocks.updateBadge.mockReturnValueOnce(update.promise)
    const wrapper = mountProfile()
    const badgeButton = wrapper.findAll('button').find((button) => button.text().includes('setRepresentative'))!

    const click = badgeButton.trigger('click')
    await vi.waitFor(() => expect(mocks.updateBadge).toHaveBeenCalledWith('FIRST'))
    mocks.route!.params.userId = '8'
    await wrapper.vm.$nextTick()
    update.resolve()
    await click
    await flushPromises()

    expect(mocks.addToast).not.toHaveBeenCalled()
    expect(mocks.refetchBadges).not.toHaveBeenCalled()
  })

  it('ignores a delayed representative badge failure after the auth session changes', async () => {
    const update = createDeferred<void>()
    mocks.updateBadge.mockReturnValueOnce(update.promise)
    const wrapper = mountProfile()
    const badgeButton = wrapper.findAll('button').find((button) => button.text().includes('setRepresentative'))!

    const click = badgeButton.trigger('click')
    await vi.waitFor(() => expect(mocks.updateBadge).toHaveBeenCalledWith('FIRST'))
    mocks.authStore.sessionGeneration = 2
    update.reject(new Error('failed'))
    await click
    await flushPromises()

    expect(mocks.addToast).not.toHaveBeenCalled()
    expect(mocks.refetchBadges).not.toHaveBeenCalled()
  })
})
