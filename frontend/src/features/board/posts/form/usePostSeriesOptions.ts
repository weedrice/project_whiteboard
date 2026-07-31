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
  const localOptions = ref<PostSeries[] | null>(null)
  const serverOptions = ref<PostSeries[]>([])
  const isPostSeriesError = ref(false)
  const newSeriesTitle = ref('')
  const isCreatingSeries = ref(false)
  const seriesOptions = computed(() => localOptions.value ?? serverOptions.value)
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
        queryKey: sessionQueryKey(generation, userQueryKeys.postSeries),
        meta: AUTH_SCOPED_QUERY_META,
        queryFn: async ({ signal }) => unwrapAxiosApiData(await userApi.getPostSeries({ signal })),
      })
      if (generation !== authStore.sessionGeneration) return
      serverOptions.value = series
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
      localOptions.value = [
        ...seriesOptions.value.filter((series) => series.seriesId !== createdSeries.seriesId),
        createdSeries,
      ]
      queryClient.setQueryData(
        sessionQueryKey(generation, userQueryKeys.postSeries),
        localOptions.value,
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

  onMounted(() => {
    if (authStore.isAuthenticated) void loadPostSeries()
  })

  watch(() => authStore.sessionGeneration, () => {
    cancelCreateSeriesRequest()
    localOptions.value = null
    serverOptions.value = []
    isPostSeriesError.value = false
    resetSeriesInput()
  })

  onScopeDispose(cancelCreateSeriesRequest)

  return {
    seriesOptions,
    newSeriesTitle,
    isCreatingSeries,
    isPostSeriesError,
    loadPostSeries,
    createSeries,
    cancelCreateSeriesRequest,
    resetSeriesInput,
  }
}
