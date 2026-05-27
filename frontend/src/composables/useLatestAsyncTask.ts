import { getCurrentScope, onScopeDispose, ref } from 'vue'

interface LatestAsyncTaskContext {
  signal: AbortSignal
}

interface LatestAsyncTaskOptions<TError> {
  getErrorValue?: (error: unknown) => TError
  onError?: (error: unknown) => void
}

export function useLatestAsyncTask<TError = unknown>(options: LatestAsyncTaskOptions<TError> = {}) {
  const loading = ref(false)
  const error = ref<TError | null>(null)
  let latestRequestId = 0
  let abortController: AbortController | null = null

  function isLatestRequest(requestId: number) {
    return requestId === latestRequestId
  }

  function abortActiveRequest() {
    abortController?.abort()
    abortController = null
  }

  async function run<TResult>(task: (context: LatestAsyncTaskContext) => Promise<TResult>): Promise<TResult | undefined> {
    abortActiveRequest()
    const controller = new AbortController()
    abortController = controller
    const requestId = ++latestRequestId
    loading.value = true
    error.value = null

    try {
      const result = await task({ signal: controller.signal })
      if (!isLatestRequest(requestId)) return undefined
      return result
    } catch (caughtError: unknown) {
      if (!isLatestRequest(requestId) || controller.signal.aborted) return undefined
      options.onError?.(caughtError)
      if (options.getErrorValue) {
        error.value = options.getErrorValue(caughtError)
      }
      return undefined
    } finally {
      if (abortController === controller) {
        abortController = null
      }
      if (isLatestRequest(requestId)) {
        loading.value = false
      }
    }
  }

  function reset() {
    abortActiveRequest()
    latestRequestId++
    loading.value = false
    error.value = null
  }

  if (getCurrentScope()) {
    onScopeDispose(reset)
  }

  return {
    loading,
    error,
    run,
    reset,
  }
}
