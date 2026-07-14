import { computed } from 'vue'
import { useConfigStore } from '@/stores/config'

export const EMOTICON_IMAGE_MAX_COUNT_KEY = 'EMOTICON_IMAGE_MAX_COUNT'
export const DEFAULT_EMOTICON_IMAGE_MAX_COUNT = 20
export const ABSOLUTE_EMOTICON_IMAGE_MAX_COUNT = 100

export function resolveEmoticonImageMaxCount(value: string | null | undefined): number {
  if (value == null || !/^\d+$/.test(value.trim())) {
    return DEFAULT_EMOTICON_IMAGE_MAX_COUNT
  }

  const parsed = Number(value)
  if (parsed < 1 || parsed > ABSOLUTE_EMOTICON_IMAGE_MAX_COUNT) {
    return DEFAULT_EMOTICON_IMAGE_MAX_COUNT
  }

  return parsed
}

export function useEmoticonImagePolicy() {
  const configStore = useConfigStore()
  const maxImageCount = computed(() => (
    resolveEmoticonImageMaxCount(configStore.getConfig(EMOTICON_IMAGE_MAX_COUNT_KEY))
  ))

  const refresh = async () => {
    delete configStore.configs[EMOTICON_IMAGE_MAX_COUNT_KEY]
    await configStore.fetchPublicConfigs()
  }

  return {
    maxImageCount,
    refresh,
  }
}
