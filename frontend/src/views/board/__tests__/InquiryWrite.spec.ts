import { flushPromises, mount } from '@vue/test-utils'
import { AxiosHeaders, type AxiosResponse } from 'axios'
import { createMemoryHistory, createRouter } from 'vue-router'
import { defineComponent, h } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import InquiryWrite from '../InquiryWrite.vue'
import { boardApi } from '@/api/board'
import type { ApiResponse } from '@/types'

vi.mock('@/api/board', () => ({
  boardApi: {
    ensureInquiryBoard: vi.fn(),
  },
}))

vi.mock('@/utils/logger', () => ({
  default: {
    error: vi.fn(),
  },
}))

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({ t: (key: string) => key }),
  }
})

vi.mock('@/composables/usePostFormLeaveGuard', () => ({
  usePostFormLeaveGuard: vi.fn(),
}))

const PostFormStub = defineComponent({
  name: 'PostForm',
  props: {
    mode: { type: String, required: true },
    boardUrl: { type: String, default: '' },
    onSubmitted: { type: Function, default: undefined },
    createTitleOverride: { type: String, default: '' },
    createSuccessToastMessage: { type: String, default: '' },
    hideCategory: { type: Boolean, default: false },
    hideTags: { type: Boolean, default: false },
    hideNotice: { type: Boolean, default: false },
    hideSpoiler: { type: Boolean, default: false },
    hideSecret: { type: Boolean, default: false },
    skipBoardLookup: { type: Boolean, default: false },
  },
  setup(props) {
    return () => h('div', { 'data-testid': 'post-form' }, props.boardUrl)
  },
})

async function mountInquiryWrite() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/inquiry', component: InquiryWrite },
      { path: '/', component: { template: '<div>Home</div>' } },
    ],
  })
  await router.push('/inquiry')
  await router.isReady()

  const wrapper = mount(
    { template: '<router-view />' },
    {
      global: {
        plugins: [router],
        stubs: {
          BaseSpinner: true,
          PostForm: PostFormStub,
        },
      },
    },
  )
  await flushPromises()
  return { wrapper, router }
}

describe('InquiryWrite', () => {
  it('prepares the inquiry board and renders PostForm with inquiry-only options', async () => {
    const ensureResponse: AxiosResponse<ApiResponse<void>> = {
      data: { success: true, data: undefined },
      status: 200,
      statusText: 'OK',
      headers: {},
      config: { headers: new AxiosHeaders() },
    }
    vi.mocked(boardApi.ensureInquiryBoard).mockResolvedValueOnce(ensureResponse)

    const { wrapper } = await mountInquiryWrite()
    const postForm = wrapper.findComponent(PostFormStub)

    expect(boardApi.ensureInquiryBoard).toHaveBeenCalledWith('inquiry')
    expect(postForm.exists()).toBe(true)
    expect(postForm.props()).toEqual(expect.objectContaining({
      mode: 'create',
      boardUrl: 'inquiry',
      createTitleOverride: '문의 작성',
      createSuccessToastMessage: '문의가 성공적으로 등록되었습니다.',
      hideCategory: true,
      hideTags: true,
      hideNotice: true,
      hideSpoiler: true,
      hideSecret: true,
      skipBoardLookup: true,
    }))
    expect(typeof postForm.props('onSubmitted')).toBe('function')
  })
})
