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

  const cancel = () => {
    requestSequence += 1
    controller?.abort()
    controller = null
  }

  const search = async (query: string) => {
    cancel()
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
    } catch (error) {
      if (
        requestController.signal.aborted
        || (error instanceof DOMException && error.name === 'AbortError')
        || (typeof error === 'object' && error !== null && 'name' in error && error.name === 'CanceledError')
      ) {
        return []
      }
      throw error
    } finally {
      stopSessionBoundary()
      if (controller === requestController) controller = null
    }
  }

  return { search, cancel }
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
  let lookupSeq = 0
  const lookup = createMentionCandidateLookup(options.search ?? defaultMentionCandidateLoader)

  const close = () => {
    lookupSeq += 1
    lookup.cancel()
    isOpen.value = false
    items.value = []
    selectedIndex.value = 0
    activeRange.value = null
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
    try {
      const candidates = await lookup.search(range.query)
      if (seq !== lookupSeq) return
      items.value = candidates
      isOpen.value = candidates.length > 0
      selectedIndex.value = 0
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return
      logger.error('Failed to load mention candidates:', error)
      if (seq === lookupSeq) {
        close()
      }
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
    refresh,
    close,
    select,
    handleKeydown,
  }
}
