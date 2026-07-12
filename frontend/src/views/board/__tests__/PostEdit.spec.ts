import { describe, it, expect, vi, afterEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import PostEdit from '../PostEdit.vue'

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({
    confirm: (message: string) => window.confirm(message),
  }),
}))

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
      },
      boardUrl: {
        type: String,
        default: ''
      },
      postId: {
        type: String,
        default: ''
      },
      onSubmitted: {
        type: Function,
        default: undefined
      }
    },
    emits: ['cancel'],
    setup(props, { expose, emit }) {
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
      return () => h('button', {
        type: 'button',
        'data-testid': 'post-form',
        'data-mode': props.mode,
        'data-board-url': props.boardUrl,
        'data-post-id': props.postId,
        onClick: () => emit('cancel')
      })
    }
  })
}

async function mountPostEdit(hasUnsavedChanges = false, leaveMessage?: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/board/:boardUrl/post/:postId/edit',
        component: PostEdit
      },
      {
        path: '/done',
        component: { template: '<div>Done</div>' }
      },
      {
        path: '/board/:boardUrl/post/:postId',
        component: { template: '<div>Post</div>' }
      }
    ]
  })

  await router.push('/board/test/post/1/edit')
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

describe('PostEdit', () => {
  it('renders PostForm with mode edit', async () => {
    const { wrapper } = await mountPostEdit()
    const form = wrapper.find('[data-testid="post-form"]')

    expect(form.exists()).toBe(true)
    expect(form.attributes('data-mode')).toBe('edit')
    expect(form.attributes('data-board-url')).toBe('test')
    expect(form.attributes('data-post-id')).toBe('1')
  })

  it('allows route leave when there are no unsaved changes', async () => {
    const { router } = await mountPostEdit(false)
    const confirmSpy = vi.spyOn(window, 'confirm')

    await router.push('/done')

    expect(confirmSpy).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/done')
  })

  it('blocks route leave when unsaved changes exist and user cancels', async () => {
    const { router } = await mountPostEdit(true, 'unsaved edit changes')
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false)

    await router.push('/done')

    expect(confirmSpy).toHaveBeenCalledWith('unsaved edit changes')
    expect(router.currentRoute.value.path).toBe('/board/test/post/1/edit')
  })

  it('allows route leave when unsaved changes exist and user confirms', async () => {
    const { router } = await mountPostEdit(true, 'unsaved edit changes')
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)

    await router.push('/done')

    expect(confirmSpy).toHaveBeenCalledWith('unsaved edit changes')
    expect(router.currentRoute.value.path).toBe('/done')
  })

  it('uses fallback leave-confirm message when child does not expose custom message getter', async () => {
    const { router } = await mountPostEdit(true, undefined)
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false)

    await router.push('/done')

    expect(confirmSpy).toHaveBeenCalledWith(expect.any(String))
    expect(router.currentRoute.value.path).toBe('/board/test/post/1/edit')
  })

  it('navigates after update from the view-level onSubmitted handler', async () => {
    const { router, wrapper } = await mountPostEdit(false)
    const form = wrapper.findComponent({ name: 'PostForm' })

    await form.props('onSubmitted')({
      boardUrl: 'test',
      postId: 1
    })
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/board/test/post/1')
  })

  it('handles PostForm cancel from the view', async () => {
    const { router, wrapper } = await mountPostEdit(false)

    await router.push('/done')
    await router.push('/board/test/post/1/edit')
    await wrapper.find('[data-testid="post-form"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/done')
  })
})
