import { getCurrentScope, onScopeDispose, ref } from 'vue'

interface LatestAsyncTaskContext {
  signal: AbortSignal
}

interface LatestRequestGateOptions<TContext> {
  captureContext?: () => TContext
  isContextCurrent?: (context: TContext) => boolean
  onActiveChange?: (active: boolean) => void
}

interface LatestRequest {
  signal: AbortSignal
  isCurrent: () => boolean
  finish: () => boolean
}

interface LatestAsyncTaskOptions<TError> {
  getErrorValue?: (error: unknown) => TError
  onError?: (error: unknown) => void
}

export function useLatestRequestGate<TContext = undefined>(
  options: LatestRequestGateOptions<TContext> = {},
) {
  let latestRequestId = 0
  let activeController: AbortController | null = null

  function cancel() {
    latestRequestId++
    activeController?.abort()
    activeController = null
    options.onActiveChange?.(false)
  }

  function start(): LatestRequest {
    activeController?.abort()
    const controller = new AbortController()
    activeController = controller
    const requestId = ++latestRequestId
    const context = options.captureContext?.() as TContext
    const ownsActiveRequest = () => requestId === latestRequestId
      && activeController === controller
      && !controller.signal.aborted
    const isCurrent = () => ownsActiveRequest()
      && (options.isContextCurrent?.(context) ?? true)

    options.onActiveChange?.(true)

    return {
      signal: controller.signal,
      isCurrent,
      finish: () => {
        if (!ownsActiveRequest()) return false
        activeController = null
        options.onActiveChange?.(false)
        return true
      },
    }
  }

  if (getCurrentScope()) {
    onScopeDispose(cancel)
  }

  return {
    start,
    cancel,
  }
}

export function useLatestAsyncTask<TError = unknown>(options: LatestAsyncTaskOptions<TError> = {}) {
  const loading = ref(false)
  const error = ref<TError | null>(null)
  const requestGate = useLatestRequestGate({
    onActiveChange: (active) => {
      loading.value = active
    },
  })

  async function run<TResult>(task: (context: LatestAsyncTaskContext) => Promise<TResult>): Promise<TResult | undefined> {
    const request = requestGate.start()
    error.value = null

    try {
      const result = await task({ signal: request.signal })
      if (!request.isCurrent()) return undefined
      return result
    } catch (caughtError: unknown) {
      if (!request.isCurrent()) return undefined
      options.onError?.(caughtError)
      if (options.getErrorValue) {
        error.value = options.getErrorValue(caughtError)
      }
      return undefined
    } finally {
      request.finish()
    }
  }

  function reset() {
    requestGate.cancel()
    error.value = null
  }

  return {
    loading,
    error,
    run,
    reset,
  }
}
