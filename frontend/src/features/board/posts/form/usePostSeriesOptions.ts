import { createAuthScopedQueryOptions } from '@/utils/authScopedQuery'
import {
  computed,
  onMounted,
  onScopeDispose,
  ref,
  watch,
  type Ref,
} from 'vue'
import { useI18n } from 'vue-i18n'
import { userApi } from '@/api/user'
import { unwrapAxiosApiData } from '@/api/response'
import { userQueryKeys } from '@/features/user/userQueryKeys'
import { AUTH_SCOPED_QUERY_META, sessionQueryKey } from '@/queryAuthScope'
import { queryClient } from '@/queryClient'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import type { PostSeries } from '@/types'
import { POST_SERIES_TITLE_MAX_LENGTH } from '@/utils/postForm'

interface UsePostSeriesOptionsParams {
  form: Ref<{ seriesId: string | number | null }>
  formIdentity: Readonly<Ref<string>>
}

export function usePostSeriesOptions({
  form,
  formIdentity,
}: UsePostSeriesOptionsParams) {
  const { t } = useI18n()
  const authStore = useAuthStore()
  const toastStore = useToastStore()
  const createdOptions = ref<PostSeries[]>([])
  const serverOptions = ref<PostSeries[]>([])
  const hasLoadedPostSeries = ref(false)
  const isPostSeriesError = ref(false)
  const newSeriesTitle = ref('')
  const isCreatingSeries = ref(false)
  const seriesOptions = computed(() => [...new Map(
    [...serverOptions.value, ...createdOptions.value].map(series => [series.seriesId, series]),
  ).values()])
  let createAbortController: AbortController | null = null

  function cancelCreateSeriesRequest() {
    const controller = createAbortController
    createAbortController = null
    controller?.abort()
    isCreatingSeries.value = false
  }

  async function loadPostSeries() {
    const generation = authStore.sessionGeneration
    isPostSeriesError.value = false
    try {
      const series = await queryClient.fetchQuery({
        ...createAuthScopedQueryOptions(authStore, sessionQueryKey(generation, userQueryKeys.postSeries), async ({ requestConfig }) => unwrapAxiosApiData(await userApi.getPostSeries(requestConfig))),
        meta: AUTH_SCOPED_QUERY_META,
      })
      if (generation !== authStore.sessionGeneration) return
      serverOptions.value = series
      const loadedIds = new Set(series.map(option => option.seriesId))
      createdOptions.value = createdOptions.value.filter(option => !loadedIds.has(option.seriesId))
      // An older list response may omit a series created while it was loading.
      // Keep it in both the selector and the cache used when reopening the form.
      if (createdOptions.value.length > 0) {
        queryClient.setQueryData(sessionQueryKey(generation, userQueryKeys.postSeries), seriesOptions.value)
      }
      hasLoadedPostSeries.value = true
    } catch {
      if (generation !== authStore.sessionGeneration) return
      isPostSeriesError.value = true
    }
  }

  async function createSeries() {
    const title = newSeriesTitle.value.trim()
    if (!title || title.length > POST_SERIES_TITLE_MAX_LENGTH || isCreatingSeries.value) return

    isCreatingSeries.value = true
    const generation = authStore.sessionGeneration
    const identity = formIdentity.value
    const controller = new AbortController()
    createAbortController?.abort()
    createAbortController = controller
    try {
      const createdSeries = unwrapAxiosApiData(await userApi.createPostSeries({ title }, {
        signal: controller.signal,
      }))
      if (
        controller.signal.aborted
        || createAbortController !== controller
        || authStore.sessionGeneration !== generation
        || formIdentity.value !== identity
      ) return
      createdOptions.value = [
        ...createdOptions.value.filter((series) => series.seriesId !== createdSeries.seriesId),
        createdSeries,
      ]
      queryClient.setQueryData(
        sessionQueryKey(generation, userQueryKeys.postSeries),
        seriesOptions.value,
      )
      form.value.seriesId = createdSeries.seriesId
      newSeriesTitle.value = ''
      toastStore.addToast(t('board.writePost.createSeriesSuccess'), 'success')
    } catch {
      if (
        controller.signal.aborted
        || createAbortController !== controller
        || authStore.sessionGeneration !== generation
        || formIdentity.value !== identity
      ) return
      toastStore.addToast(t('board.writePost.createSeriesFailed'), 'error')
    } finally {
      if (
        createAbortController === controller
        && authStore.sessionGeneration === generation
        && formIdentity.value === identity
      ) {
        createAbortController = null
        isCreatingSeries.value = false
      }
    }
  }

  function resetSeriesInput() {
    newSeriesTitle.value = ''
  }

  function resetUnavailableSelectedSeries() {
    if (!hasLoadedPostSeries.value || form.value.seriesId == null || form.value.seriesId === '') return
    const selectedSeriesId = Number(form.value.seriesId)
    if (seriesOptions.value.some((series) => series.seriesId === selectedSeriesId)) return
    form.value.seriesId = ''
    toastStore.addToast(t('board.writePost.draftStatus.referencesReset'), 'warning')
  }

  onMounted(() => {
    if (authStore.isAuthenticated) void loadPostSeries()
  })

  watch(() => authStore.sessionGeneration, () => {
    cancelCreateSeriesRequest()
    createdOptions.value = []
    serverOptions.value = []
    hasLoadedPostSeries.value = false
    isPostSeriesError.value = false
    resetSeriesInput()
  })

  watch(
    [hasLoadedPostSeries, seriesOptions, () => form.value.seriesId],
    resetUnavailableSelectedSeries,
    { flush: 'post' },
  )

  onScopeDispose(cancelCreateSeriesRequest)

  return {
    seriesOptions,
    newSeriesTitle,
    isCreatingSeries,
    isPostSeriesError,
    hasLoadedPostSeries,
    loadPostSeries,
    createSeries,
    cancelCreateSeriesRequest,
    resetSeriesInput,
  }
}
