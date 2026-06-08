import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { emoticonApi } from '@/api/emoticon'
import { popularEmoticonsQueryKey, searchableEmoticonsQueryKey } from '@/composables/useEmoticonEditResource'
import { usePageResponseState, usePaginatedQueryState } from '@/composables/usePaginatedQueryState'
import type { EmoticonSearchParams } from '@/types/emoticon'

export function useEmoticonListResource() {
  const router = useRouter()
  const popularPeriod = ref<'daily' | 'weekly' | 'monthly'>('daily')
  const sortBy = ref<NonNullable<EmoticonSearchParams['sortBy']>>('latest')
  const {
    page: currentPage,
    size,
    resetPage,
  } = usePaginatedQueryState({
    initialSize: 20,
  })
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
        size: size.value,
        sortBy: sortBy.value,
      }
      if (searchKeyword.value) {
        params.keyword = searchKeyword.value
        params.searchType = searchType.value
      }
      return emoticonApi.searchAllData(params)
    },
  })

  const {
    items: emoticons,
    totalPages,
    totalElements,
  } = usePageResponseState(emoticonsPage, currentPage)

  const goToPage = (page: number) => {
    if (page >= 0 && page < totalPages.value) {
      currentPage.value = page
    }
  }

  const changeSortBy = (newSort: NonNullable<EmoticonSearchParams['sortBy']>) => {
    sortBy.value = newSort
    resetPage()
  }

  const handleSearch = () => {
    const keyword = searchInput.value.trim()
    if (!keyword) return
    searchKeyword.value = keyword
    isSearching.value = true
    resetPage()
  }

  const clearSearch = () => {
    searchInput.value = ''
    searchKeyword.value = ''
    isSearching.value = false
    resetPage()
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
