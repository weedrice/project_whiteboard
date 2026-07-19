import { flushPromises, mount } from '@vue/test-utils'
import { reactive, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import UserProfilePage from '../UserProfilePage.vue'
import { createDeferred } from '@/test/async'

const mocks = vi.hoisted(() => ({
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
      data: ref({ userId: 7, displayName: 'Novi', profileImageUrl: null, createdAt: '2026-01-01', postCount: 2, commentCount: 1 }),
      isLoading: ref(false), isError: ref(false), refetch: mocks.refetchProfile,
    }),
    useUserBadges: () => ({
      data: ref([
        { badgeCode: 'FIRST', name: 'First', description: 'First badge', tier: 'BRONZE', acquired: true, representative: false },
        { badgeCode: 'LOCKED', name: 'Locked', description: '', tier: 'GOLD', acquired: false, representative: false },
      ]),
      isLoading: ref(false), isError: ref(false), refetch: mocks.refetchBadges,
    }),
    useUpdateRepresentativeBadge: () => ({ mutateAsync: mocks.updateBadge, isPending: ref(false) }),
    usePublicProfilePosts: () => ({
      data: ref({ content: [{ postId: 1, title: 'Post' }], totalPages: 2 }),
      isLoading: ref(false), isError: ref(false), refetch: mocks.refetchPosts,
    }),
    usePublicProfileComments: () => ({
      data: ref({ content: [{ commentId: 2, content: '![emoticon](/uploads/happy.png)', post: { boardUrl: 'news', postId: 1, title: 'Post' } }], totalPages: 2 }),
      isLoading: ref(false), isError: ref(false), refetch: mocks.refetchComments,
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
        RouterLink: { template: '<a><slot /></a>' },
        FileText: true, MessageSquare: true, Award: true, User: true,
      },
    },
  })
}

describe('UserProfilePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.authStore.user = { userId: 7 }
    mocks.authStore.sessionGeneration = 1
    mocks.updateBadge.mockResolvedValue(undefined)
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
