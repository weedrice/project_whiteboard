import { ref } from 'vue'
import { useEmoticonTagItems } from '@/composables/useEmoticonTagItems'
import { resolveEmoticonTagAddition } from '@/utils/emoticonImage'

interface UseEmoticonTagsOptions {
  idPrefix?: string
  onMaxTags?: () => void
}

export function useEmoticonTags(options: UseEmoticonTagsOptions = {}) {
  const tagInput = ref('')
  const { tagItems, tags, addTagItem, removeTagItem } = useEmoticonTagItems(options.idPrefix)

  const addTag = () => {
    const result = resolveEmoticonTagAddition(tagInput.value, tags.value)
    if (result.error === 'maxTags') {
      options.onMaxTags?.()
    } else if (result.tag) {
      addTagItem(result.tag)
    }
    tagInput.value = ''
  }

  const removeTag = (clientId: string) => {
    removeTagItem(clientId)
  }

  return {
    tagInput,
    tagItems,
    tags,
    addTag,
    removeTag,
  }
}
