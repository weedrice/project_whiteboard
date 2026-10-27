import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { userApi } from '@/api/user'
import { queryClient } from '@/queryClient'
import { configureAuthQueryScope, sessionQueryKey } from '@/queryAuthScope'
import { userQueryKeys } from '@/features/user/userQueryKeys'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { apiSuccessDataResponse } from '@/test/apiResponseFixtures'
import { createDeferred } from '@/test/async'
import type { PostSeries } from '@/types'
import { usePostSeriesOptions } from '../usePostSeriesOptions'

vi.mock('@/api/user', () => ({ userApi: { getPostSeries: vi.fn(), createPostSeries: vi.fn() } }))
vi.mock('@/stores/auth', () => ({ useAuthStore: vi.fn() }))
vi.mock('@/stores/toast', () => ({ useToastStore: vi.fn() }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))

const listResponse = (series: PostSeries[]) => apiSuccessDataResponse<typeof userApi.getPostSeries>(series)
const createResponse = (series: PostSeries) => apiSuccessDataResponse<typeof userApi.createPostSeries>(series)
const existingSeries = { seriesId: 1, title: 'Existing series' }
const createdSeries = { seriesId: 2, title: 'New series' }
const wrappers: VueWrapper[] = []
let auth: { sessionGeneration: number; isAuthenticated: boolean; user: { userId: number } }

function mountOptions() {
  const form = ref({ seriesId: '' as string | number | null })
  let feature!: ReturnType<typeof usePostSeriesOptions>
  const wrapper = mount(defineComponent({
    setup() {
      feature = usePostSeriesOptions({ form, formIdentity: ref('create:free') })
      return () => h('select', { value: form.value.seriesId }, feature.seriesOptions.value.map(series => (
        h('option', { key: series.seriesId, value: series.seriesId }, series.title)
      )))
    },
  }))
  wrappers.push(wrapper)
  return { wrapper, form, feature }
}

beforeEach(() => {
  vi.clearAllMocks()
  queryClient.clear()
  queryClient.setDefaultOptions({ queries: { retry: false, staleTime: 30_000, gcTime: Infinity } })
  auth = reactive({ sessionGeneration: 1, isAuthenticated: true, user: { userId: 1 } })
  vi.mocked(useAuthStore).mockReturnValue(auth as unknown as ReturnType<typeof useAuthStore>)
  vi.mocked(useToastStore).mockReturnValue({ addToast: vi.fn() } as unknown as ReturnType<typeof useToastStore>)
  configureAuthQueryScope(() => auth.sessionGeneration, () => auth.user.userId)
  vi.mocked(userApi.getPostSeries).mockResolvedValue(listResponse([]))
  vi.mocked(userApi.createPostSeries).mockResolvedValue(createResponse(createdSeries))
})

afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  queryClient.clear()
  configureAuthQueryScope(() => 0)
})

describe('post series options with real query scheduling', () => {
  it('keeps existing and newly created series when the initial list finishes last, including after reopening', async () => {
    const pendingList = createDeferred<Awaited<ReturnType<typeof userApi.getPostSeries>>>()
    vi.mocked(userApi.getPostSeries).mockReturnValueOnce(pendingList.promise)
    const { wrapper, form, feature } = mountOptions()
    await flushPromises()
    expect(userApi.getPostSeries).toHaveBeenCalledOnce()

    feature.newSeriesTitle.value = createdSeries.title
    await feature.createSeries()
    expect(form.value.seriesId).toBe(createdSeries.seriesId)
    pendingList.resolve(listResponse([existingSeries]))
    await flushPromises()

    expect(wrapper.findAll('option').map(option => option.text())).toEqual(['Existing series', 'New series'])
    expect(form.value.seriesId).toBe(createdSeries.seriesId)
    expect(queryClient.getQueryData(sessionQueryKey(1, userQueryKeys.postSeries))).toEqual([existingSeries, createdSeries])
    wrapper.unmount()
    const reopened = mountOptions()
    await flushPromises()
    expect(reopened.feature.seriesOptions.value).toEqual([existingSeries, createdSeries])
    expect(userApi.getPostSeries).toHaveBeenCalledOnce()
  })

  it('deduplicates a created series already returned by the list and accepts the list representation', async () => {
    const pendingList = createDeferred<Awaited<ReturnType<typeof userApi.getPostSeries>>>()
    vi.mocked(userApi.getPostSeries).mockReturnValueOnce(pendingList.promise)
    const { wrapper, form, feature } = mountOptions()
    feature.newSeriesTitle.value = createdSeries.title
    await feature.createSeries()
    const confirmedSeries = { ...createdSeries, title: 'Confirmed series title' }
    pendingList.resolve(listResponse([existingSeries, confirmedSeries]))
    await flushPromises()

    expect(wrapper.findAll('option')).toHaveLength(2)
    expect(feature.seriesOptions.value).toEqual([existingSeries, confirmedSeries])
    expect(form.value.seriesId).toBe(createdSeries.seriesId)
    expect(queryClient.getQueryData(sessionQueryKey(1, userQueryKeys.postSeries))).toEqual([existingSeries, confirmedSeries])

    // Once the list confirms a creation, a later deletion must not be hidden by a local overlay.
    queryClient.invalidateQueries({ queryKey: sessionQueryKey(1, userQueryKeys.postSeries) })
    vi.mocked(userApi.getPostSeries).mockResolvedValueOnce(listResponse([existingSeries]))
    await feature.loadPostSeries()
    await nextTick()
    expect(feature.seriesOptions.value).toEqual([existingSeries])
    expect(form.value.seriesId).toBe('')
  })

  it('preserves a normally loaded list when creating a new series', async () => {
    vi.mocked(userApi.getPostSeries).mockResolvedValueOnce(listResponse([existingSeries]))
    const { feature } = mountOptions()
    await flushPromises()
    feature.newSeriesTitle.value = createdSeries.title
    await feature.createSeries()
    expect(feature.seriesOptions.value).toEqual([existingSeries, createdSeries])
    expect(queryClient.getQueryData(sessionQueryKey(1, userQueryKeys.postSeries))).toEqual([existingSeries, createdSeries])
  })

  it('clears created options on account change and ignores the previous account list response', async () => {
    const pendingList = createDeferred<Awaited<ReturnType<typeof userApi.getPostSeries>>>()
    vi.mocked(userApi.getPostSeries).mockReturnValueOnce(pendingList.promise)
    const { feature } = mountOptions()
    feature.newSeriesTitle.value = createdSeries.title
    await feature.createSeries()
    auth.sessionGeneration = 2
    auth.user = { userId: 2 }
    await nextTick()
    expect(feature.seriesOptions.value).toEqual([])
    expect(feature.hasLoadedPostSeries.value).toBe(false)

    const nextAccountSeries = { seriesId: 3, title: 'Other account series' }
    vi.mocked(userApi.getPostSeries).mockResolvedValueOnce(listResponse([nextAccountSeries]))
    await feature.loadPostSeries()
    pendingList.resolve(listResponse([existingSeries]))
    await flushPromises()
    expect(feature.seriesOptions.value).toEqual([nextAccountSeries])
    expect(queryClient.getQueryData(sessionQueryKey(2, userQueryKeys.postSeries))).toEqual([nextAccountSeries])
  })

  it('aborts a pending creation on session reset without restoring its late result', async () => {
    const pendingCreate = createDeferred<Awaited<ReturnType<typeof userApi.createPostSeries>>>()
    vi.mocked(userApi.createPostSeries).mockReturnValueOnce(pendingCreate.promise)
    const { feature } = mountOptions()
    await flushPromises()
    feature.newSeriesTitle.value = createdSeries.title
    const create = feature.createSeries()
    const signal = vi.mocked(userApi.createPostSeries).mock.calls[0]![1]!.signal!
    auth.sessionGeneration = 2
    await nextTick()
    expect(signal.aborted).toBe(true)
    expect(feature.isCreatingSeries.value).toBe(false)
    pendingCreate.resolve(createResponse(createdSeries))
    await create
    expect(feature.seriesOptions.value).toEqual([])
    expect(feature.newSeriesTitle.value).toBe('')
    expect(queryClient.getQueryData(sessionQueryKey(2, userQueryKeys.postSeries))).toBeUndefined()
  })
})
