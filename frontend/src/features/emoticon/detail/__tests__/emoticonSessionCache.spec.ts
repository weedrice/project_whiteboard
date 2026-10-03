import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, defineComponent, nextTick, reactive } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import type { AxiosRequestConfig } from 'axios'
import type { EmoticonMaster } from '@/types/emoticon'
import { useEmoticonDetailResource } from '../useEmoticonDetailResource'
import { useEmoticonEditResource } from '@/features/emoticon/form/useEmoticonEditResource'
import { useEmoticonEditForm } from '@/features/emoticon/form/useEmoticonEditForm'
import { useEmoticonEditSubmit } from '@/features/emoticon/form/useEmoticonEditSubmit'
import { clearAuthScopedQueries, configureAuthQueryScope, notifyAuthSessionBoundary, sessionQueryKey } from '@/queryAuthScope'
import { emoticonQueryKeys } from '@/features/emoticon/emoticonQueryKeys'
import { createDeferred } from '@/test/async'

interface TestAuth {
  sessionGeneration: number
  user: { userId: number } | null
  isAuthenticated: boolean
  accessToken: string
}

const mocks = vi.hoisted(() => ({
  auth: null as TestAuth | null,
  getEmoticon: vi.fn(),
  checkPurchaseStatus: vi.fn(),
  purchaseEmoticon: vi.fn(),
  toggleVisibilityData: vi.fn(),
  updateEmoticon: vi.fn(),
  addToast: vi.fn(),
}))

vi.mock('@/api/emoticon', () => ({ emoticonApi: mocks }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => mocks.auth }))
vi.mock('@/stores/toast', () => ({ useToastStore: () => ({ addToast: mocks.addToast }) }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))

let auth: TestAuth
let serverDetail: EmoticonMaster
const wrappers: VueWrapper[] = []
const clients: QueryClient[] = []

function mountResources() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 30000 } } })
  clients.push(client)
  let detail!: ReturnType<typeof useEmoticonDetailResource>
  let editor!: ReturnType<typeof useEmoticonEditResource>
  let form!: ReturnType<typeof useEmoticonEditForm>
  let submit!: ReturnType<typeof useEmoticonEditSubmit>
  const onSaved = vi.fn()
  const onError = vi.fn()
  const wrapper = mount(defineComponent({
    setup() {
      const emoticonId = computed(() => 41)
      detail = useEmoticonDetailResource(emoticonId)
      editor = useEmoticonEditResource({ emoticonId })
      form = useEmoticonEditForm({
        emoticonId,
        emoticon: editor.emoticon,
        editFormState: editor.editFormState,
        selectThumbnailImage: vi.fn(),
        selectEmoticonImages: vi.fn(),
        confirm: vi.fn(async () => true),
        t: key => key,
        onMaxTags: vi.fn(),
        maxImageCount: computed(() => 20),
      })
      submit = useEmoticonEditSubmit({
        emoticonId,
        isFormValid: form.isFormValid,
        isSubmitting: form.isSubmitting,
        thumbnailFile: form.thumbnailFile,
        imagesToDelete: form.imagesToDelete,
        newEmoticonPreviews: form.newEmoticonPreviews,
        emoticonName: form.emoticonName,
        tags: form.tags,
        uploadSession: form.uploadSession,
        queryClient: client,
        fallbackErrorMessage: 'save failed',
        onSuccess: onSaved,
        onError,
      })
      return () => detail.emoticonView.value?.name ?? 'empty'
    },
  }), { global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } })
  wrappers.push(wrapper)
  return { client, detail, editor, form, submit, onSaved, onError, wrapper }
}

function transitionSession(client: QueryClient, userId: number | null) {
  auth.sessionGeneration += 1
  auth.user = userId === null ? null : { userId }
  auth.isAuthenticated = userId !== null
  notifyAuthSessionBoundary(auth.sessionGeneration)
  clearAuthScopedQueries(client)
}

beforeEach(() => {
  vi.clearAllMocks()
  auth = reactive({ sessionGeneration: 1, user: { userId: 1 }, isAuthenticated: true, accessToken: 'token-a' })
  mocks.auth = auth
  configureAuthQueryScope(() => auth.sessionGeneration)
  serverDetail = {
    emoticonId: 41, name: 'Owner-only hidden pack', isActive: false,
    creatorId: 1, creatorName: 'Owner', createdAt: '2026-10-01', modifiedAt: '2026-10-01',
    thumbnailUrl: '/uploads/hidden-thumbnail.png', tags: ['private'], purchaseCount: 0,
    images: [{ imageId: 1, emoticonId: 41, imageUrl: '/uploads/hidden-image.png', sortOrder: 0 }],
  }
  mocks.getEmoticon.mockReset().mockImplementation(async () => {
    if (!serverDetail.isActive && auth.user?.userId !== 1) throw new Error('EMOTICON_NOT_FOUND')
    return { data: { success: true, data: { ...serverDetail } } }
  })
  mocks.checkPurchaseStatus.mockReset().mockResolvedValue({ data: { success: true, data: { purchased: false, available: true } } })
  mocks.purchaseEmoticon.mockReset().mockImplementation(async () => { serverDetail.purchaseCount = 1 })
  mocks.toggleVisibilityData.mockReset().mockImplementation(async () => {
    serverDetail.isActive = !serverDetail.isActive
    return { ...serverDetail }
  })
  mocks.updateEmoticon.mockReset().mockImplementation(async (_id, data: { name: string }) => {
    serverDetail.name = data.name
    return { data: { success: true, data: { ...serverDetail } } }
  })
})

afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  clients.splice(0).forEach(client => client.clear())
  configureAuthQueryScope(() => 0)
})

describe('emoticon detail session cache', () => {
  it.each(['logout', 'switch account'] as const)('clears hidden detail and local edit fields on %s', async (transition) => {
    const { client, detail, editor, form, wrapper } = mountResources()
    await vi.waitFor(() => expect(form.emoticonName.value).toBe(serverDetail.name))
    form.emoticonName.value = 'Unsaved private name'
    form.tagInput.value = 'private draft'
    form.markImageForDeletion(1)

    transitionSession(client, transition === 'logout' ? null : 2)

    expect(form.emoticonName.value).toBe('')
    expect(form.existingImages.value).toEqual([])
    expect(form.thumbnailPreview.value).toBeNull()
    expect(form.tags.value).toEqual([])
    expect(form.imagesToDelete.value).toEqual([])
    expect(form.hasUnsavedChanges.value).toBe(false)
    await vi.waitFor(() => expect(detail.error.value).toBeInstanceOf(Error))
    expect(wrapper.text()).toBe('empty')
    expect(editor.editFormState.value).toBeNull()
    expect(client.getQueryData(sessionQueryKey(1, emoticonQueryKeys.detail(41)))).toBeUndefined()
    expect(client.getQueryData(['emoticon', 41])).toBeUndefined()
    expect(mocks.getEmoticon).toHaveBeenCalledTimes(2)
  })

  it('rejects a late prior-session detail and aborts its consumed query signal', async () => {
    const oldResponse = createDeferred<{ data: { success: boolean, data: EmoticonMaster } }>()
    mocks.getEmoticon.mockImplementationOnce((_id: number, _config: AxiosRequestConfig) => oldResponse.promise)
    const { client, detail, editor, form } = mountResources()
    await vi.waitFor(() => expect(mocks.getEmoticon).toHaveBeenCalledOnce())
    const oldSignal = mocks.getEmoticon.mock.calls[0]?.[1]?.signal as AbortSignal

    transitionSession(client, 2)
    await vi.waitFor(() => expect(detail.error.value).toBeInstanceOf(Error))
    oldResponse.resolve({ data: { success: true, data: { ...serverDetail } } })
    await oldResponse.promise
    await nextTick()

    expect(oldSignal.aborted).toBe(true)
    expect(detail.emoticonView.value).toBeNull()
    expect(editor.editFormState.value).toBeNull()
    expect(form.existingImages.value).toEqual([])
    expect(client.getQueryData(sessionQueryKey(1, emoticonQueryKeys.detail(41)))).toBeUndefined()
  })

  it('reloads and rehydrates the same owner after a new login while preserving normal token rotation', async () => {
    const { client, form, detail } = mountResources()
    await vi.waitFor(() => expect(form.emoticonName.value).toBe(serverDetail.name))
    auth.accessToken = 'rotated-token'
    await nextTick()
    expect(mocks.getEmoticon).toHaveBeenCalledOnce()

    serverDetail.name = 'Fresh owner detail'
    transitionSession(client, 1)
    await vi.waitFor(() => expect(form.emoticonName.value).toBe('Fresh owner detail'))

    expect(detail.emoticonView.value?.name).toBe('Fresh owner detail')
    expect(mocks.getEmoticon).toHaveBeenCalledTimes(2)
    expect(client.getQueryData(sessionQueryKey(2, emoticonQueryKeys.detail(41)))).toMatchObject({ name: 'Fresh owner detail' })
  })

  it('refreshes the shared detail/edit query after purchase without invalidating a different session', async () => {
    serverDetail.isActive = true
    const { client, detail, editor } = mountResources()
    const otherKey = sessionQueryKey(99, emoticonQueryKeys.detail(41))
    client.setQueryData(otherKey, serverDetail)
    await vi.waitFor(() => expect(detail.emoticonView.value?.purchaseCount).toBe(0))

    detail.purchase()
    await vi.waitFor(() => expect(editor.emoticon.value?.purchaseCount).toBe(1))

    expect(detail.emoticonView.value?.purchaseCount).toBe(1)
    expect(client.getQueryState(otherKey)?.isInvalidated).toBe(false)
    expect(mocks.getEmoticon).toHaveBeenCalledTimes(2)
  })

  it('refreshes the scoped detail after a visibility change', async () => {
    const { detail, editor } = mountResources()
    await vi.waitFor(() => expect(editor.emoticon.value?.isActive).toBe(false))

    detail.toggleVisibility()
    await vi.waitFor(() => expect(detail.emoticonView.value?.isActive).toBe(true))

    expect(editor.emoticon.value?.isActive).toBe(true)
    expect(mocks.getEmoticon).toHaveBeenCalledTimes(2)
  })

  it('refreshes the same session detail after saving an edit', async () => {
    const { detail, form, submit, onSaved, onError } = mountResources()
    await vi.waitFor(() => expect(form.isFormValid.value).toBe(true))
    form.emoticonName.value = 'Updated hidden pack'

    await submit.handleSubmit()
    await vi.waitFor(() => expect(detail.emoticonView.value?.name).toBe('Updated hidden pack'))

    expect(onSaved).toHaveBeenCalledWith(41)
    expect(onError).not.toHaveBeenCalled()
    expect(mocks.getEmoticon).toHaveBeenCalledTimes(2)
  })
})
