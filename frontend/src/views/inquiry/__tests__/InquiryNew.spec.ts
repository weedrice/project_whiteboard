import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import InquiryNew from '../InquiryNew.vue'
import { configureAuthQueryScope } from '@/queryAuthScope'

const { state } = vi.hoisted(() => {
  const refOf = <T>(value: T) => ({ __v_isRef: true, value })
  return {
    state: {
      commitUploads: vi.fn(),
      discardUploads: vi.fn().mockResolvedValue(undefined),
      beginSubmission: vi.fn(() => true),
      failSubmission: vi.fn().mockResolvedValue(undefined),
      invalidateQueries: vi.fn().mockResolvedValue(undefined),
      mutation: { mutate: vi.fn(), isPending: refOf(false) },
      mutationOptions: null as null | {
        onSuccess?: (response: unknown, variables: Record<string, unknown>) => unknown
        onError?: (error: unknown, variables: Record<string, unknown>) => unknown
      },
      routerPush: vi.fn().mockResolvedValue(undefined),
      routerReplace: vi.fn().mockResolvedValue(undefined),
      sessionGeneration: 0,
    },
  }
})

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...await importOriginal<typeof import('vue-router')>(),
  useRouter: () => ({ push: state.routerPush, replace: state.routerReplace }),
}))

vi.mock('@tanstack/vue-query', async (importOriginal) => ({
  ...await importOriginal<typeof import('@tanstack/vue-query')>(),
  useMutation: (options: typeof state.mutationOptions) => {
    state.mutationOptions = options
    return state.mutation
  },
  useQueryClient: () => ({ invalidateQueries: state.invalidateQueries }),
}))

vi.mock('@/api/inquiry', () => ({
  inquiryApi: { create: vi.fn() },
}))

const InquiryImageUploaderStub = defineComponent({
  props: { disabled: Boolean },
  emits: ['uploading'],
  setup(_, { expose }) {
    expose({
      commitUploads: state.commitUploads,
      discardUploads: state.discardUploads,
      beginSubmission: state.beginSubmission,
      failSubmission: state.failSubmission,
    })
    return () => h('div', { 'data-test': 'uploader' })
  },
})

function mountView() {
  return mount(InquiryNew, {
    global: {
      stubs: {
        InquiryImageUploader: InquiryImageUploaderStub,
        BaseButton: { props: ['disabled', 'to'], template: '<button :disabled="disabled"><slot /></button>' },
        PageHeader: true,
      },
    },
  })
}

describe('InquiryNew', () => {
  beforeEach(() => {
    state.commitUploads.mockReset()
    state.discardUploads.mockClear()
    state.beginSubmission.mockClear()
    state.failSubmission.mockClear()
    state.invalidateQueries.mockClear()
    state.routerPush.mockClear()
    state.routerReplace.mockClear()
    state.mutation.mutate.mockClear()
    state.mutation.isPending.value = false
    state.mutationOptions = null
    state.sessionGeneration = 0
    configureAuthQueryScope(() => state.sessionGeneration)
  })

  it('invalidates the auth-scoped inquiry list before navigating after creation', async () => {
    mountView()

    const uploader = { commitUploads: state.commitUploads }
    await state.mutationOptions!.onSuccess?.({
      data: { success: true, data: { inquiryId: 42 } },
    }, { generation: 0, uploader })
    await flushPromises()

    expect(state.commitUploads).toHaveBeenCalledOnce()
    expect(state.invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['session', 0, 'inquiries', 'mine'],
    })
    expect(state.routerReplace).toHaveBeenCalledWith('/inquiries/42')
  })

  it('commits the server result without navigating a replacement login session', async () => {
    mountView()
    state.sessionGeneration = 1

    await state.mutationOptions!.onSuccess?.({
      data: { success: true, data: { inquiryId: 42 } },
    }, { generation: 0, uploader: { commitUploads: state.commitUploads } })

    expect(state.commitUploads).toHaveBeenCalledOnce()
    expect(state.invalidateQueries).not.toHaveBeenCalled()
    expect(state.routerReplace).not.toHaveBeenCalled()
  })

  it('discards temporary uploads when creation fails', async () => {
    mountView()

    await state.mutationOptions!.onError?.(new Error('failed'), { uploader: { failSubmission: state.failSubmission } })
    await flushPromises()

    expect(state.failSubmission).toHaveBeenCalledOnce()
    expect(state.routerReplace).not.toHaveBeenCalled()
  })

  it('does not submit while an image upload is in flight', async () => {
    const wrapper = mountView()
    wrapper.findComponent(InquiryImageUploaderStub).vm.$emit('uploading', true)
    await wrapper.vm.$nextTick()

    await wrapper.get('form').trigger('submit')

    expect(state.mutation.mutate).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toBe('inquiry.upload.uploading')
  })

  it('locks the entire form while creation is pending', async () => {
    state.mutation.isPending.value = true
    const wrapper = mountView()

    expect(wrapper.get('select').attributes('disabled')).toBeDefined()
    expect(wrapper.get('input').attributes('disabled')).toBeDefined()
    expect(wrapper.get('textarea').attributes('disabled')).toBeDefined()
    expect(wrapper.getComponent(InquiryImageUploaderStub).props('disabled')).toBe(true)

    await wrapper.get('form').trigger('submit')

    expect(state.mutation.mutate).not.toHaveBeenCalled()
  })

  it('keeps a recovery link and prevents duplicate submission when navigation fails after creation', async () => {
    state.routerReplace.mockRejectedValueOnce(new Error('navigation failed'))
    const wrapper = mountView()
    const uploader = { commitUploads: state.commitUploads }

    await state.mutationOptions!.onSuccess?.({
      data: { success: true, data: { inquiryId: 77 } },
    }, { generation: 0, uploader })
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe('inquiry.form.createdNavigationFailed')
    expect(wrapper.text()).toContain('inquiry.form.openCreated')

    await wrapper.get('form').trigger('submit')

    expect(state.mutation.mutate).not.toHaveBeenCalled()
  })
})
