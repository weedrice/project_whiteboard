import { defineComponent, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useBoardEditManagerAssignment } from '@/features/board/edit/useBoardEditManagerAssignment'
import { createDeferred } from '@/test/async'
import type { BoardDetail } from '@/types'

const mocks = vi.hoisted(() => ({
  addToast: vi.fn(),
  handleError: vi.fn(),
}))

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/stores/toast', () => ({ useToastStore: () => ({ addToast: mocks.addToast }) }))
vi.mock('@/composables/useErrorHandler', () => ({
  useErrorHandler: () => ({ handleError: mocks.handleError }),
}))

describe('useBoardEditManagerAssignment', () => {
  beforeEach(() => vi.clearAllMocks())

  it('ignores a manager transfer response from an older authentication session', async () => {
    const transfer = createDeferred<BoardDetail>()
    const boardUrl = ref('free')
    const sessionGeneration = ref(1)
    let manager!: ReturnType<typeof useBoardEditManagerAssignment>
    const wrapper = mount(defineComponent({
      setup() {
        manager = useBoardEditManagerAssignment({
          boardUrl,
          sessionGeneration,
          transferBoardManager: vi.fn().mockReturnValue(transfer.promise),
        })
        return () => null
      },
    }))

    const pending = manager.confirmManagerSelection([{ loginId: 'next-manager', displayName: 'Next' }])
    sessionGeneration.value = 2
    transfer.resolve({ adminDisplayName: 'Old session manager' } as BoardDetail)
    await pending

    expect(manager.currentManagerLabel.value).toBe('')
    expect(mocks.addToast).not.toHaveBeenCalled()
    expect(mocks.handleError).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
