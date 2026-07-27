import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MyBadges from '../MyBadges.vue'

const mocks = vi.hoisted(() => ({
  badges: {
    __v_isRef: true,
    value: [
      {
        badgeCode: 'FIRST_POST',
        name: 'First Post',
        description: 'Awarded after writing the first post.',
        tier: 'BRONZE',
        acquiredAt: '2026-07-01T00:00:00',
        acquired: true,
        representative: true,
      },
      {
        badgeCode: 'POSTS_10',
        name: '10 Posts',
        description: 'Awarded after writing 10 posts.',
        tier: 'SILVER',
        acquired: false,
        representative: false,
      },
    ],
  },
  isLoading: { __v_isRef: true, value: false },
  isError: { __v_isRef: true, value: false },
  isPending: { __v_isRef: true, value: false },
  refetch: vi.fn(),
  updateRepresentativeBadge: vi.fn(),
  addToast: vi.fn(),
}))

vi.mock('@/features/user/useUser', () => ({
  useUser: () => ({
    useMyBadges: () => ({
      data: mocks.badges,
      isLoading: mocks.isLoading,
      isError: mocks.isError,
      refetch: mocks.refetch,
    }),
    useUpdateRepresentativeBadge: () => ({
      mutateAsync: mocks.updateRepresentativeBadge,
      isPending: mocks.isPending,
    }),
  }),
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({ addToast: mocks.addToast }),
}))

vi.mock('@/utils/date', () => ({
  formatDate: (value: string) => `date:${value}`,
}))

const baseButtonStub = {
  props: ['disabled', 'ariaPressed'],
  emits: ['click'],
  template: '<button type="button" :disabled="disabled" :aria-pressed="ariaPressed" @click="$emit(\'click\')"><slot /></button>',
}

describe('MyBadges', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.isLoading.value = false
    mocks.isError.value = false
    mocks.isPending.value = false
    mocks.updateRepresentativeBadge.mockResolvedValue(null)
  })

  it('separates earned and locked badges and updates the representative badge', async () => {
    const wrapper = mount(MyBadges, {
      global: {
        mocks: {
          $t: (key: string, params?: Record<string, string>) => (
            params?.date ? `${key}:${params.date}` : key
          ),
        },
        stubs: {
          BaseButton: baseButtonStub,
          BaseSpinner: true,
          ErrorState: true,
          Award: true,
          LockKeyhole: true,
          Medal: true,
        },
      },
    })

    expect(wrapper.get('h1').text()).toBe('user.badges.title')
    expect(wrapper.text()).toContain('첫 게시글')
    expect(wrapper.text()).toContain('첫 번째 게시글을 작성하면 획득합니다.')
    expect(wrapper.text()).toContain('게시글 10개')
    expect(wrapper.text()).not.toContain('Awarded after writing the first post.')
    expect(wrapper.text()).toContain('user.badges.representative')
    expect(wrapper.text()).toContain('user.badges.acquiredAt:date:2026-07-01T00:00:00')

    const unsetButton = wrapper.findAll('button')
      .find((button) => button.text() === 'user.badges.unsetRepresentative')
    await unsetButton!.trigger('click')

    expect(mocks.updateRepresentativeBadge).toHaveBeenCalledWith(null)
    expect(mocks.addToast).toHaveBeenCalledWith('대표 배지를 변경했습니다.', 'success')
  })
})
