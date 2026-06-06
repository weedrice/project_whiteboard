import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { emoticonApi } from '@/api/emoticon'
import { popularEmoticonsQueryKey, searchableEmoticonsQueryKey } from '@/composables/useEmoticonEditResource'
import type { EmoticonSearchParams } from '@/types/emoticon'

const pageSize = 20

export function useEmoticonListResource() {
  const router = useRouter()
  const popularPeriod = ref<'daily' | 'weekly' | 'monthly'>('daily')
  const sortBy = ref<NonNullable<EmoticonSearchParams['sortBy']>>('latest')
  const currentPage = ref(0)
  const searchKeyword = ref('')
  const searchInput = ref('')
  const searchType = ref<NonNullable<EmoticonSearchParams['searchType']>>('ALL')
  const isSearching = ref(false)

  const { data: popularEmoticons, isLoading: popularLoading } = useQuery({
    queryKey: popularEmoticonsQueryKey(popularPeriod),
    queryFn: async () => emoticonApi.getPopularEmoticonsData(popularPeriod.value),
  })

  const { data: emoticonsPage, isLoading: emoticonsLoading } = useQuery({
    queryKey: searchableEmoticonsQueryKey(currentPage, sortBy, searchKeyword, searchType),
    queryFn: async () => {
      const params: EmoticonSearchParams = {
        page: currentPage.value,
        size: pageSize,
        sortBy: sortBy.value,
      }
      if (searchKeyword.value) {
        params.keyword = searchKeyword.value
        params.searchType = searchType.value
      }
      return emoticonApi.searchAllData(params)
    },
  })

  const emoticons = computed(() => emoticonsPage.value?.content || [])
  const totalPages = computed(() => emoticonsPage.value?.totalPages || 0)
  const totalElements = computed(() => emoticonsPage.value?.totalElements || 0)
  const goToPage = (page: number) => {
    if (page >= 0 && page < totalPages.value) {
      currentPage.value = page
    }
  }

  const changeSortBy = (newSort: NonNullable<EmoticonSearchParams['sortBy']>) => {
    sortBy.value = newSort
    currentPage.value = 0
  }

  const handleSearch = () => {
    const keyword = searchInput.value.trim()
    if (!keyword) return
    searchKeyword.value = keyword
    isSearching.value = true
    currentPage.value = 0
  }

  const clearSearch = () => {
    searchInput.value = ''
    searchKeyword.value = ''
    isSearching.value = false
    currentPage.value = 0
  }

  const goToDetail = (emoticonId: number) => {
    router.push({ name: 'emoticon-detail', params: { emoticonId } })
  }

  return {
    popularPeriod,
    sortBy,
    currentPage,
    searchInput,
    searchType,
    isSearching,
    popularEmoticons,
    popularLoading,
    emoticonsLoading,
    emoticons,
    totalPages,
    totalElements,
    goToPage,
    changeSortBy,
    handleSearch,
    clearSearch,
    goToDetail,
  }
}
