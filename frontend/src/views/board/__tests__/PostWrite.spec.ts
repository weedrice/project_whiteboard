import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import PostWrite from '../PostWrite.vue'

afterEach(() => {
  vi.restoreAllMocks()
})

function createPostFormStub(hasUnsavedChanges: boolean, leaveMessage?: string) {
  return defineComponent({
    name: 'PostForm',
    props: {
      mode: {
        type: String,
        required: true
      }
    },
    setup(props, { expose }) {
      const exposed: Record<string, unknown> = {
        hasUnsavedChanges: () => hasUnsavedChanges,
      }
      if (leaveMessage !== undefined) {
        exposed.getLeaveConfirmMessage = () => leaveMessage
      }
      expose(exposed as {
        hasUnsavedChanges: () => boolean
        getLeaveConfirmMessage?: () => string
      })
      return () => h('div', { 'data-testid': 'post-form', 'data-mode': props.mode })
    }
  })
}

async function mountPostWrite(hasUnsavedChanges = false, leaveMessage?: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/board/test/write',
        component: PostWrite
      },
      {
        path: '/done',
        component: { template: '<div>Done</div>' }
      }
    ]
  })

  await router.push('/board/test/write')
  await router.isReady()

  const wrapper = mount(
    { template: '<router-view />' },
    {
      global: {
        plugins: [router],
        stubs: {
          PostForm: createPostFormStub(hasUnsavedChanges, leaveMessage)
        }
      }
    }
  )

  await wrapper.vm.$nextTick()
  return { router, wrapper }
}

describe('PostWrite', () => {
  it('renders PostForm with mode create', async () => {
    const { wrapper } = await mountPostWrite()
    const form = wrapper.find('[data-testid="post-form"]')

    expect(form.exists()).toBe(true)
    expect(form.attributes('data-mode')).toBe('create')
  })

  it('allows route leave when there are no unsaved changes', async () => {
    const { router } = await mountPostWrite(false)
    const confirmSpy = vi.spyOn(window, 'confirm')

    await router.push('/done')

    expect(confirmSpy).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/done')
  })

  it('blocks route leave when unsaved changes exist and user cancels', async () => {
    const { router } = await mountPostWrite(true, 'unsaved write changes')
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false)

    await router.push('/done')

    expect(confirmSpy).toHaveBeenCalledWith('unsaved write changes')
    expect(router.currentRoute.value.path).toBe('/board/test/write')
  })

  it('allows route leave when unsaved changes exist and user confirms', async () => {
    const { router } = await mountPostWrite(true, 'unsaved write changes')
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)

    await router.push('/done')

    expect(confirmSpy).toHaveBeenCalledWith('unsaved write changes')
    expect(router.currentRoute.value.path).toBe('/done')
  })

  it('uses fallback leave-confirm message when child does not expose custom message getter', async () => {
    const { router } = await mountPostWrite(true, undefined)
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false)

    await router.push('/done')

    expect(confirmSpy).toHaveBeenCalledWith(expect.any(String))
    expect(router.currentRoute.value.path).toBe('/board/test/write')
  })
})
