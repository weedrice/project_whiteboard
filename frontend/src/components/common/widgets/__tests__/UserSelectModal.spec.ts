import { mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { ref, type Ref } from 'vue'
import UserSelectModal from '../UserSelectModal.vue'

const mocks = vi.hoisted(() => ({
  adminCalls: [] as Array<{ params: Ref<Record<string, unknown>>, enabled: Ref<boolean> }>,
  boardCalls: [] as Array<{ boardUrl: Ref<string>, params: Ref<Record<string, unknown>>, enabled: Ref<boolean> }>,
}))

vi.mock('@/composables/useAdmin', () => ({
  useAdmin: () => ({
    useUsers: (params: Ref<Record<string, unknown>>, enabled: Ref<boolean>) => {
      mocks.adminCalls.push({ params, enabled })
      return {
        data: ref({
          content: [
            { userId: 1, loginId: 'admin-user', displayName: 'Admin User', email: 'admin@test.com' },
          ],
        }),
        isLoading: ref(false),
      }
    },
  }),
}))

vi.mock('@/composables/useBoard', () => ({
  useBoard: () => ({
    useBoardManagerCandidates: (
      boardUrl: Ref<string>,
      params: Ref<Record<string, unknown>>,
      enabled: Ref<boolean>
    ) => {
      mocks.boardCalls.push({ boardUrl, params, enabled })
      return {
        data: ref({
          content: [
            { userId: 2, loginId: 'board-user', displayName: 'Board User', currentManager: true },
          ],
        }),
        isLoading: ref(false),
      }
    },
  }),
}))

const mountModal = (props: {
  isOpen: boolean
  title?: string
  selectionMode?: 'single' | 'multiple'
  source?: 'admin' | 'board-manager-candidates'
  boardUrl?: string
  initialSelectedIds?: number[]
  excludeUserIds?: number[]
}) => mount(UserSelectModal, {
  props,
  global: {
    mocks: {
      $t: (key: string) => key,
    },
    stubs: {
      BaseModal: {
        props: ['isOpen'],
        template: '<div v-if="isOpen"><slot /></div>',
      },
      BaseInput: {
        props: ['modelValue', 'id', 'name', 'label', 'autocomplete', 'hideLabel'],
        emits: ['update:modelValue'],
        template: `
          <div>
            <label v-if="label" :for="id" :class="{ 'sr-only': hideLabel }">{{ label }}</label>
            <input
              :id="id"
              :name="name"
              :autocomplete="autocomplete"
              :value="modelValue"
              @input="$emit('update:modelValue', $event.target.value)"
            />
          </div>
        `,
      },
      BaseButton: {
        template: '<button><slot /></button>',
      },
      BaseSpinner: {
        template: '<div />',
      },
    },
  },
})

describe('UserSelectModal', () => {
  beforeEach(() => {
    mocks.adminCalls.length = 0
    mocks.boardCalls.length = 0
  })

  it('uses admin users by default', () => {
    const wrapper = mountModal({ isOpen: true })

    expect(mocks.adminCalls.at(-1)?.enabled.value).toBe(true)
    expect(mocks.boardCalls.at(-1)?.enabled.value).toBe(false)
    expect(wrapper.get('label[for="user-select-search"]').text()).toBe('admin.users.searchPlaceholder')
    expect(wrapper.get('#user-select-search').attributes()).toMatchObject({
      name: 'userSelectSearch',
      autocomplete: 'off',
    })
    expect(wrapper.text()).toContain('admin-user')
    expect(wrapper.text()).toContain('common.email')
  })

  it('uses board manager candidates without email column', () => {
    const wrapper = mountModal({
      isOpen: true,
      source: 'board-manager-candidates',
      boardUrl: 'free',
    })

    expect(mocks.adminCalls.at(-1)?.enabled.value).toBe(false)
    expect(mocks.boardCalls.at(-1)?.enabled.value).toBe(true)
    expect(mocks.boardCalls.at(-1)?.boardUrl.value).toBe('free')
    expect(wrapper.text()).toContain('board-user')
    expect(wrapper.text()).toContain('current')
    expect(wrapper.text()).not.toContain('common.email')
  })

  it('disables both queries while closed', () => {
    mountModal({
      isOpen: false,
      source: 'board-manager-candidates',
      boardUrl: 'free',
    })

    expect(mocks.adminCalls.at(-1)?.enabled.value).toBe(false)
    expect(mocks.boardCalls.at(-1)?.enabled.value).toBe(false)
  })
})
