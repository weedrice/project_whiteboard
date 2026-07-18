import { computed, defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useBoardCreationPolicy } from '../useBoardCreationPolicy'

const configState = vi.hoisted(() => ({
  setValue: vi.fn<(value: string | undefined) => void>(),
  fetchConfig: vi.fn(),
  invalidateConfig: vi.fn(),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ user: { points: 1_000 } }),
}))

vi.mock('@/stores/config', async () => {
  const { ref } = await import('vue')
  const value = ref<string>()
  configState.setValue.mockImplementation((nextValue) => {
    value.value = nextValue
  })
  return {
    useConfigStore: () => ({
      getConfig: () => value.value,
      fetchConfig: configState.fetchConfig,
      invalidateConfig: configState.invalidateConfig,
    }),
  }
})

function deferred() {
  let resolve!: () => void
  const promise = new Promise<void>((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

describe('useBoardCreationPolicy', () => {
  beforeEach(() => {
    configState.setValue(undefined)
    configState.fetchConfig.mockReset()
    configState.invalidateConfig.mockReset()
  })

  it('does not let an older cost request overwrite the newer request state', async () => {
    const first = deferred()
    const second = deferred()
    configState.fetchConfig
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
    let policy!: ReturnType<typeof useBoardCreationPolicy>
    const wrapper = mount(defineComponent({
      setup() {
        policy = useBoardCreationPolicy({ isEdit: computed(() => false) })
        return () => null
      },
    }))
    await flushPromises()

    const retry = policy.loadBoardCreateCost()
    configState.setValue('700')
    second.resolve()
    await retry
    expect(policy.boardCreateCost.value).toBe(700)
    expect(policy.boardCreateCostError.value).toBe(false)

    first.resolve()
    await flushPromises()
    expect(policy.boardCreateCost.value).toBe(700)
    expect(policy.isBoardCreateCostLoading.value).toBe(false)
    expect(policy.boardCreateCostError.value).toBe(false)

    wrapper.unmount()
  })
})
