import { computed, ref } from 'vue'

export interface EmoticonTagItem {
  clientId: string
  value: string
}

export function useEmoticonTagItems(idPrefix = 'emoticon-tag') {
  let tagSequence = 0
  const tagItems = ref<EmoticonTagItem[]>([])

  const createTagItem = (value: string): EmoticonTagItem => {
    tagSequence += 1
    return {
      clientId: `${idPrefix}-${tagSequence}`,
      value
    }
  }

  const tags = computed<string[]>({
    get: () => tagItems.value.map((item) => item.value),
    set: (values) => {
      tagItems.value = values.map(createTagItem)
    }
  })

  const addTagItem = (value: string) => {
    tagItems.value.push(createTagItem(value))
  }

  const removeTagItem = (clientId: string) => {
    const index = tagItems.value.findIndex((item) => item.clientId === clientId)
    if (index >= 0) {
      tagItems.value.splice(index, 1)
    }
  }

  return {
    tagItems,
    tags,
    addTagItem,
    removeTagItem,
  }
}
