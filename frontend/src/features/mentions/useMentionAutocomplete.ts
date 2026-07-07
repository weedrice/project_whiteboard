import { ref, unref, type MaybeRefOrGetter } from 'vue'
import { userAccountApi } from '@/api/userAccountApi'
import { unwrapAxiosApiData } from '@/api/response'
import logger from '@/utils/logger'
import type { MentionCandidate } from '@/types'

export interface MentionQueryRange {
  query: string
  start: number
  end: number
}

export interface UseMentionAutocompleteOptions {
  resolveRange: () => MentionQueryRange | null
  onSelect: (candidate: MentionCandidate, range: MentionQueryRange) => void | Promise<void>
  disabled?: MaybeRefOrGetter<boolean>
  search?: (query: string) => Promise<MentionCandidate[]>
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

  const close = () => {
    isOpen.value = false
    items.value = []
    selectedIndex.value = 0
    activeRange.value = null
  }

  const search = options.search ?? (async (query: string) => {
    const response = await userAccountApi.getMentionCandidates(query)
    return unwrapAxiosApiData(response)
  })

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
      const candidates = await search(range.query)
      if (seq !== lookupSeq) return
      items.value = candidates
      isOpen.value = candidates.length > 0
      selectedIndex.value = 0
    } catch (error) {
      logger.error('Failed to load mention candidates:', error)
      if (seq === lookupSeq) {
        close()
      }
    }
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

