import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { emoticonApi } from '@/api/emoticon'
import type { EmoticonSearchParams } from '@/types/emoticon'

export interface DisplayedPage {
  key: string
  page: number | null
}

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
    queryKey: ['emoticons', 'popular', popularPeriod],
    queryFn: async () => emoticonApi.getPopularEmoticonsData(popularPeriod.value),
  })

  const { data: emoticonsPage, isLoading: emoticonsLoading } = useQuery({
    queryKey: ['emoticons', 'list', currentPage, sortBy, searchKeyword, searchType],
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
  const displayedPages = computed(() => {
    const pages: DisplayedPage[] = []
    const lastPage = totalPages.value
    const current = currentPage.value + 1

    if (lastPage <= 7) {
      return Array.from({ length: lastPage }, (_, index) => ({
        key: `page-${index + 1}`,
        page: index + 1,
      }))
    }

    const visiblePages = new Set<number>([1, lastPage])
    for (let page = Math.max(2, current - 1); page <= Math.min(lastPage - 1, current + 1); page += 1) {
      visiblePages.add(page)
    }

    let previousPage = 0
    Array.from(visiblePages)
      .sort((a, b) => a - b)
      .forEach((page) => {
        if (previousPage && page - previousPage > 1) {
          pages.push({
            key: `ellipsis-${previousPage}-${page}`,
            page: null,
          })
        }
        pages.push({
          key: `page-${page}`,
          page,
        })
        previousPage = page
      })

    return pages
  })

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
    displayedPages,
    goToPage,
    changeSortBy,
    handleSearch,
    clearSearch,
    goToDetail,
  }
}
