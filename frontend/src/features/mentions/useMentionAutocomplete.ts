import { getCurrentScope, onScopeDispose, ref, unref, type MaybeRefOrGetter } from 'vue'
import { userAccountApi } from '@/api/userAccountApi'
import { unwrapAxiosApiData } from '@/api/response'
import logger from '@/utils/logger'
import type { MentionCandidate } from '@/types'
import { getCurrentSessionGeneration, subscribeAuthSessionBoundary } from '@/queryAuthScope'

export interface MentionQueryRange {
  query: string
  start: number
  end: number
}

export interface UseMentionAutocompleteOptions {
  resolveRange: () => MentionQueryRange | null
  onSelect: (candidate: MentionCandidate, range: MentionQueryRange) => void | Promise<void>
  disabled?: MaybeRefOrGetter<boolean>
  search?: (query: string, signal: AbortSignal) => Promise<MentionCandidate[]>
}

type MentionCandidateLoader = (query: string, signal: AbortSignal) => Promise<MentionCandidate[]>

const defaultMentionCandidateLoader: MentionCandidateLoader = async (query, signal) => {
  const response = await userAccountApi.getMentionCandidates(query, { signal })
  return unwrapAxiosApiData(response)
}

export function createMentionCandidateLookup(
  load: MentionCandidateLoader = defaultMentionCandidateLoader,
  resolveGeneration: () => number = getCurrentSessionGeneration,
) {
  let requestSequence = 0
  let controller: AbortController | null = null
  const isLoading = ref(false)
  const lookupError = ref<unknown | null>(null)
  let lastQuery = ''

  const cancel = () => {
    requestSequence += 1
    controller?.abort()
    controller = null
  }

  const search = async (query: string) => {
    cancel()
    lastQuery = query
    isLoading.value = true
    lookupError.value = null
    const sequence = requestSequence
    const generation = resolveGeneration()
    const requestController = new AbortController()
    controller = requestController
    const stopSessionBoundary = subscribeAuthSessionBoundary(() => {
      if (controller === requestController) cancel()
    })

    try {
      const candidates = await load(query, requestController.signal)
      if (
        requestController.signal.aborted
        || sequence !== requestSequence
        || generation !== resolveGeneration()
      ) {
        return []
      }
      return candidates
    } catch (caughtError) {
      if (
        requestController.signal.aborted
        || (caughtError instanceof DOMException && caughtError.name === 'AbortError')
        || (typeof caughtError === 'object' && caughtError !== null && 'name' in caughtError && caughtError.name === 'CanceledError')
      ) {
        return []
      }
      lookupError.value = caughtError
      throw caughtError
    } finally {
      if (sequence === requestSequence) isLoading.value = false
      stopSessionBoundary()
      if (controller === requestController) controller = null
    }
  }

  const retry = () => lastQuery ? search(lastQuery) : Promise.resolve([])

  return { search, cancel, retry, isLoading, error: lookupError }
}

function resolveDisabled(disabled: MaybeRefOrGetter<boolean> | undefined): boolean {
  if (typeof disabled === 'function') {
    return Boolean((disabled as () => boolean)())
  }
  return Boolean(unref(disabled))
}

export function useMentionAutocomplete(options: UseMentionAutocompleteOptions) {
  const items = ref<MentionCandidate[]>([])
  const isOpen = ref(false)
  const selectedIndex = ref(0)
  const activeRange = ref<MentionQueryRange | null>(null)
  const isLoading = ref(false)
  const loadError = ref<unknown | null>(null)
  let lookupSeq = 0
  const lookup = createMentionCandidateLookup(options.search ?? defaultMentionCandidateLoader)

  const close = () => {
    lookupSeq += 1
    lookup.cancel()
    isOpen.value = false
    items.value = []
    selectedIndex.value = 0
    activeRange.value = null
    isLoading.value = false
    loadError.value = null
  }
  const stopSessionBoundary = subscribeAuthSessionBoundary(close)

  const refresh = async () => {
    if (resolveDisabled(options.disabled)) {
      close()
      return
    }

    const range = options.resolveRange()
    if (!range?.query.trim()) {
      close()
      return
    }

    const seq = ++lookupSeq
    activeRange.value = range
    isLoading.value = true
    loadError.value = null
    try {
      const candidates = await lookup.search(range.query)
      if (seq !== lookupSeq) return
      items.value = candidates
      isOpen.value = candidates.length > 0
      selectedIndex.value = 0
    } catch (caughtError) {
      if (caughtError instanceof DOMException && caughtError.name === 'AbortError') return
      logger.error('Failed to load mention candidates:', caughtError)
      if (seq === lookupSeq) {
        items.value = []
        isOpen.value = false
        selectedIndex.value = 0
        loadError.value = caughtError
      }
    } finally {
      if (seq === lookupSeq) isLoading.value = false
    }
  }

  if (getCurrentScope()) {
    onScopeDispose(() => {
      stopSessionBoundary()
      close()
    })
  }

  const select = async (candidate: MentionCandidate) => {
    const range = options.resolveRange() ?? activeRange.value
    if (!range) {
      close()
      return
    }

    await options.onSelect(candidate, range)
    close()
  }

  const handleKeydown = (event: KeyboardEvent) => {
    if (!isOpen.value || items.value.length === 0) return

    if (event.key === 'ArrowDown') {
      event.preventDefault()
      selectedIndex.value = (selectedIndex.value + 1) % items.value.length
      return
    }

    if (event.key === 'ArrowUp') {
      event.preventDefault()
      selectedIndex.value = (selectedIndex.value - 1 + items.value.length) % items.value.length
      return
    }

    if (event.key === 'Enter' || event.key === 'Tab') {
      event.preventDefault()
      const candidate = items.value[selectedIndex.value]
      if (candidate) {
        void select(candidate)
      }
      return
    }

    if (event.key === 'Escape') {
      event.preventDefault()
      close()
    }
  }

  return {
    items,
    isOpen,
    selectedIndex,
    isLoading,
    error: loadError,
    refresh,
    retry: refresh,
    close,
    select,
    handleKeydown,
  }
}
