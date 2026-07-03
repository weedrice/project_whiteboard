import { computed, ref, type Ref } from 'vue'
import type { EmoticonMaster } from '@/types/emoticon'

export const useEmoticonPickerSearch = (emoticons: Ref<EmoticonMaster[] | undefined>) => {
    const searchKeyword = ref('')

    const filteredEmoticons = computed(() => {
        const items = emoticons.value ?? []
        const keyword = searchKeyword.value.trim().toLowerCase()
        if (!keyword) return items

        return items.filter(emoticon =>
            emoticon.name.toLowerCase().includes(keyword)
            || emoticon.tags?.some(tag => tag.toLowerCase().includes(keyword)),
        )
    })

    const clearSearch = () => {
        searchKeyword.value = ''
    }

    return {
        searchKeyword,
        filteredEmoticons,
        clearSearch,
    }
}
