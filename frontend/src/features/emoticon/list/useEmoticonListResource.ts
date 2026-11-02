import { computed, ref, watch } from 'vue'
import { useRoute, useRouter, type LocationQueryRaw } from 'vue-router'
import { emoticonApi } from '@/api/emoticon'
import { emoticonQueryKeys } from '@/features/emoticon/emoticonQueryKeys'
import { useApiPageQuery, useApiQuery } from '@/composables/useApiQuery'
import { usePageResponseState } from '@/composables/usePaginatedQueryState'
import type { EmoticonMaster, EmoticonSearchParams } from '@/types/emoticon'
import { optionalQuerySignal } from '@/utils/querySignal'
import { firstQueryValue } from '@/utils/routeQueryValue'

export function useEmoticonListResource() {
  const router = useRouter()
  const route = useRoute()
  const periodQuery = computed(() => {
    const value = firstQueryValue(route.query.period)
    return value === 'weekly' || value === 'monthly' ? value : 'daily'
  })
  const popularPeriod = computed({
    get: () => periodQuery.value,
    set: (period: 'daily' | 'weekly' | 'monthly') => {
      void navigateList({ period: period === 'daily' ? undefined : period })
    },
  })
  const sortBy = computed<NonNullable<EmoticonSearchParams['sortBy']>>(() => {
    const value = firstQueryValue(route.query.sort)
    return value === 'oldest' || value === 'popular' ? value : 'latest'
  })
  const currentPage = computed(() => {
    const value = Number(firstQueryValue(route.query.page))
    return Number.isSafeInteger(value) && value > 0 && value <= 2147483647 ? value - 1 : 0
  })
  const searchKeyword = computed(() => firstQueryValue(route.query.q).trim())
  const searchTypeQuery = computed<NonNullable<EmoticonSearchParams['searchType']>>(() => {
    const value = firstQueryValue(route.query.type)
    return value === 'NAME' || value === 'CREATOR' || value === 'TAG' ? value : 'ALL'
  })
  const searchInput = ref('')
  const searchType = ref(searchTypeQuery.value)
  const isSearching = computed(() => searchKeyword.value.length > 0)

  watch(searchKeyword, (keyword) => { searchInput.value = keyword }, { immediate: true })
  watch(searchTypeQuery, (type) => { searchType.value = type })

  function navigateList(changes: LocationQueryRaw) {
    return router.push({
      name: 'emoticon-list',
      query: {
        ...route.query,
        q: searchKeyword.value || undefined,
        type: searchTypeQuery.value === 'ALL' ? undefined : searchTypeQuery.value,
        sort: sortBy.value === 'latest' ? undefined : sortBy.value,
        period: periodQuery.value === 'daily' ? undefined : periodQuery.value,
        page: currentPage.value > 0 ? String(currentPage.value + 1) : undefined,
        ...changes,
      },
    })
  }

  const {
    data: popularEmoticons,
    isLoading: popularLoading,
    isError: popularError,
    refetch: refetchPopularEmoticons,
  } = useApiQuery({
    queryKey: computed(() => emoticonQueryKeys.popular(popularPeriod.value)),
    request: (context) => {
      const requestConfig = optionalQuerySignal(undefined, context)
      return requestConfig
        ? emoticonApi.getPopularEmoticons(popularPeriod.value, requestConfig)
        : emoticonApi.getPopularEmoticons(popularPeriod.value)
    },
  })

  const {
    data: emoticonsPage,
    isLoading: emoticonsLoading,
    isError: emoticonsError,
    refetch: refetchEmoticons,
  } = useApiPageQuery<EmoticonMaster>({
    queryKey: computed(() => emoticonQueryKeys.searchable(
      currentPage.value,
      sortBy.value,
      searchKeyword.value,
      searchTypeQuery.value
    )),
    request: (context) => {
      const params: EmoticonSearchParams = {
        page: currentPage.value,
        size: 20,
        sortBy: sortBy.value,
      }
      if (searchKeyword.value) {
        params.keyword = searchKeyword.value
        params.searchType = searchTypeQuery.value
      }
      const requestConfig = optionalQuerySignal(undefined, context)
      return requestConfig
        ? emoticonApi.searchAll(params, requestConfig)
        : emoticonApi.searchAll(params)
    },
  })

  const {
    items: emoticons,
    totalPages,
    totalElements,
  } = usePageResponseState(emoticonsPage, currentPage)

  const goToPage = (page: number) => {
    if (Number.isInteger(page) && page >= 0 && page < totalPages.value) {
      void navigateList({ page: page > 0 ? String(page + 1) : undefined })
    }
  }

  const changeSortBy = (newSort: NonNullable<EmoticonSearchParams['sortBy']>) => {
    void navigateList({ sort: newSort === 'latest' ? undefined : newSort, page: undefined })
  }

  const handleSearch = () => {
    const keyword = searchInput.value.trim()
    if (!keyword) return
    void navigateList({
      q: keyword,
      type: searchType.value === 'ALL' ? undefined : searchType.value,
      page: undefined,
    })
  }

  const clearSearch = () => {
    searchInput.value = ''
    void navigateList({ q: undefined, page: undefined })
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
    popularError,
    refetchPopularEmoticons,
    emoticonsLoading,
    emoticonsError,
    refetchEmoticons,
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
