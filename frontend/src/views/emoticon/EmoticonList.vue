<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { emoticonApi } from '@/api/emoticon'
import { useAuthStore } from '@/stores/auth'
import { useHead } from '@unhead/vue'
import { Search, X, PlusCircle, TrendingUp } from 'lucide-vue-next'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import { DEFAULT_EMOTICON_IMAGE_URL, applyImageFallback } from '@/utils/imageFallback'
import type { EmoticonSearchParams } from '@/types/emoticon'

const router = useRouter()
const authStore = useAuthStore()

useHead({
  title: '노비콘',
  meta: [
    { name: 'description', content: '노비콘을 조회하고 구매하세요.' }
  ]
})

// 상태
const popularPeriod = ref<'daily' | 'weekly' | 'monthly'>('daily')
const sortBy = ref<'latest' | 'oldest' | 'popular'>('latest')
const currentPage = ref(0)
const pageSize = 20
const searchKeyword = ref('')
const searchInput = ref('')
const searchType = ref<NonNullable<EmoticonSearchParams['searchType']>>('ALL') // ALL, NAME, CREATOR, TAG
const isSearching = ref(false)

// 인기 이모티콘 조회
const { data: popularEmoticons, isLoading: popularLoading } = useQuery({
  queryKey: ['emoticons', 'popular', popularPeriod],
  queryFn: async () => {
    const { data } = await emoticonApi.getPopularEmoticons(popularPeriod.value)
    return data.data
  }
})

// 전체 이모티콘 조회
const { data: emoticonsPage, isLoading: emoticonsLoading } = useQuery({
  queryKey: ['emoticons', 'list', currentPage, sortBy, searchKeyword, searchType],
  queryFn: async () => {
    const params: EmoticonSearchParams = {
      page: currentPage.value,
      size: pageSize,
      sortBy: sortBy.value
    }
    if (searchKeyword.value) {
      params.keyword = searchKeyword.value
      params.searchType = searchType.value
    }
    const { data } = await emoticonApi.searchAll(params)
    return data.data
  }
})

const emoticons = computed(() => emoticonsPage.value?.content || [])
const totalPages = computed(() => emoticonsPage.value?.totalPages || 0)
const totalElements = computed(() => emoticonsPage.value?.totalElements || 0)

// 페이지 변경
const goToPage = (page: number) => {
  if (page >= 0 && page < totalPages.value) {
    currentPage.value = page
  }
}

// 정렬 변경
const changeSortBy = (newSort: NonNullable<EmoticonSearchParams['sortBy']>) => {
  sortBy.value = newSort
  currentPage.value = 0
}

// 검색
const handleSearch = () => {
  if (!searchInput.value.trim()) return
  searchKeyword.value = searchInput.value.trim()
  isSearching.value = true
  currentPage.value = 0
}

// 검색 초기화
const clearSearch = () => {
  searchInput.value = ''
  searchKeyword.value = ''
  isSearching.value = false
  currentPage.value = 0
}

// 상세 페이지 이동
const goToDetail = (emoticonId: number) => {
  router.push({ name: 'emoticon-detail', params: { emoticonId } })
}

// 등록 페이지 이동
// 기간 버튼 텍스트
const periodLabels = {
  daily: '일간',
  weekly: '주간',
  monthly: '월간'
}

const sortOptions: Array<{ value: NonNullable<EmoticonSearchParams['sortBy']>; label: string }> = [
  { value: 'latest', label: '최신순' },
  { value: 'oldest', label: '오래된순' },
  { value: 'popular', label: '판매순' }
]
</script>

<template>
  <div class="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <!-- 페이지 헤더 -->
    <div class="mb-8">
      <h1 class="text-2xl font-bold text-gray-900 dark:text-white">노비콘</h1>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">다양한 노비콘을 구경하고 구매하세요!</p>
    </div>

    <!-- 인기 노비콘 섹션 -->
    <section class="mb-12">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center gap-2">
          <TrendingUp class="w-5 h-5 text-indigo-600 dark:text-indigo-400" />
          <h2 class="text-lg font-semibold text-gray-900 dark:text-white">인기 노비콘</h2>
        </div>
        <div class="flex gap-2">
          <button
            v-for="(label, period) in periodLabels"
            :key="period"
            @click="popularPeriod = period as 'daily' | 'weekly' | 'monthly'"
            :class="[
              'px-3 py-1.5 text-sm font-medium rounded-md transition-colors',
              popularPeriod === period
                ? 'bg-indigo-600 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600'
            ]"
          >
            {{ label }}
          </button>
        </div>
      </div>

      <!-- 인기 노비콘 그리드 -->
      <div v-if="popularLoading" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        <div
          v-for="i in 5"
          :key="i"
          class="relative bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 overflow-hidden animate-pulse"
        >
          <div class="absolute top-2 left-2 z-10 w-6 h-6 bg-gray-300 dark:bg-gray-600 rounded-full"></div>
          <div class="aspect-square bg-gray-200 dark:bg-gray-700"></div>
          <div class="p-3 space-y-2">
            <div class="h-4 bg-gray-200 dark:bg-gray-700 rounded w-3/4"></div>
            <div class="h-3 bg-gray-200 dark:bg-gray-700 rounded w-1/2"></div>
            <div class="h-3 bg-gray-200 dark:bg-gray-700 rounded w-2/3"></div>
          </div>
        </div>
      </div>
      <div v-else-if="popularEmoticons && popularEmoticons.length > 0" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        <div
          v-for="(emoticon, index) in popularEmoticons"
          :key="emoticon.emoticonId"
          @click="goToDetail(emoticon.emoticonId)"
          class="relative bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 overflow-hidden cursor-pointer hover:shadow-md transition-shadow"
        >
          <!-- 순위 뱃지 -->
          <div class="absolute top-2 left-2 z-10 w-6 h-6 bg-indigo-600 text-white text-xs font-bold rounded-full flex items-center justify-center">
            {{ index + 1 }}
          </div>
          <!-- 썸네일 -->
          <div class="aspect-square bg-gray-100 dark:bg-gray-700">
            <img
              :src="emoticon.thumbnailUrl || DEFAULT_EMOTICON_IMAGE_URL"
              :alt="emoticon.name"
              class="w-full h-full object-contain"
              @error="applyImageFallback"
            />
          </div>
          <!-- 정보 -->
          <div class="p-3">
            <h3 class="text-sm font-medium text-gray-900 dark:text-white truncate">{{ emoticon.name }}</h3>
            <p class="text-xs text-gray-500 dark:text-gray-400 truncate">{{ emoticon.creatorName }}</p>
            <p class="text-xs text-indigo-600 dark:text-indigo-400 mt-1">
              판매 {{ emoticon.purchaseCount?.toLocaleString() || 0 }}회
            </p>
          </div>
        </div>
      </div>
      <div v-else class="text-center py-8 text-gray-500 dark:text-gray-400">
        인기 노비콘이 없습니다.
      </div>
    </section>

    <!-- 전체 노비콘 섹션 -->
    <section>
      <div class="flex flex-col gap-3 mb-4 sm:flex-row sm:items-center sm:justify-between">
        <h2 class="text-lg font-semibold text-gray-900 dark:text-white">
          전체 노비콘 <span class="text-sm font-normal text-gray-500">({{ totalElements.toLocaleString() }}개)</span>
        </h2>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="option in sortOptions"
            :key="option.value"
            @click="changeSortBy(option.value)"
            :class="[
              'px-3 py-1.5 text-sm font-medium rounded-md transition-colors',
              sortBy === option.value
                ? 'bg-indigo-600 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600'
            ]"
          >
            {{ option.label }}
          </button>
        </div>
      </div>

      <!-- 전체 노비콘 그리드 -->
      <div v-if="emoticonsLoading" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        <div
          v-for="i in 10"
          :key="i"
          class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 overflow-hidden animate-pulse"
        >
          <div class="aspect-square bg-gray-200 dark:bg-gray-700"></div>
          <div class="p-3 space-y-2">
            <div class="h-4 bg-gray-200 dark:bg-gray-700 rounded w-3/4"></div>
            <div class="h-3 bg-gray-200 dark:bg-gray-700 rounded w-1/2"></div>
            <div class="h-3 bg-gray-200 dark:bg-gray-700 rounded w-2/3"></div>
          </div>
        </div>
      </div>
      <div v-else-if="emoticons.length > 0" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        <div
          v-for="emoticon in emoticons"
          :key="emoticon.emoticonId"
          @click="goToDetail(emoticon.emoticonId)"
          class="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 overflow-hidden cursor-pointer hover:shadow-md transition-shadow"
        >
          <!-- 썸네일 -->
          <div class="aspect-square bg-gray-100 dark:bg-gray-700">
            <img
              :src="emoticon.thumbnailUrl || DEFAULT_EMOTICON_IMAGE_URL"
              :alt="emoticon.name"
              class="w-full h-full object-contain"
              @error="applyImageFallback"
            />
          </div>
          <!-- 정보 -->
          <div class="p-3">
            <h3 class="text-sm font-medium text-gray-900 dark:text-white truncate">{{ emoticon.name }}</h3>
            <p class="text-xs text-gray-500 dark:text-gray-400 truncate">{{ emoticon.creatorName }}</p>
            <p class="text-xs text-indigo-600 dark:text-indigo-400 mt-1">
              판매 {{ emoticon.purchaseCount?.toLocaleString() || 0 }}회
            </p>
          </div>
        </div>
      </div>
      <div v-else class="text-center py-12 text-gray-500 dark:text-gray-400">
        등록된 노비콘이 없습니다.
      </div>

      <!-- 페이지네이션 -->
      <div v-if="totalPages > 1" class="mt-8 flex justify-center">
        <nav class="flex items-center gap-1">
          <button
            @click="goToPage(currentPage - 1)"
            :disabled="currentPage === 0"
            class="px-3 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-md hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            이전
          </button>
          <template v-for="page in totalPages" :key="page">
            <button
              v-if="page <= 5 || page === totalPages || (page >= currentPage && page <= currentPage + 2)"
              @click="goToPage(page - 1)"
              :class="[
                'px-3 py-2 text-sm font-medium rounded-md',
                currentPage === page - 1
                  ? 'bg-indigo-600 text-white'
                  : 'text-gray-700 dark:text-gray-300 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 hover:bg-gray-50 dark:hover:bg-gray-700'
              ]"
            >
              {{ page }}
            </button>
            <span v-else-if="page === 6 || page === currentPage - 1" class="px-2 text-gray-500">...</span>
          </template>
          <button
            @click="goToPage(currentPage + 1)"
            :disabled="currentPage >= totalPages - 1"
            class="px-3 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-md hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            다음
          </button>
        </nav>
      </div>
    </section>

    <!-- 검색 바 (가운데) & 등록 버튼 (오른쪽) - 게시글 목록과 동일 레이아웃 -->
    <div class="mt-4 px-4 py-4 sm:px-6 bg-gray-50 dark:bg-gray-800 rounded-lg transition-colors duration-200">
      <div class="flex flex-col sm:flex-row items-center gap-4">
        <div class="flex-1 min-w-0 hidden sm:block" aria-hidden="true"></div>
        <div class="w-full sm:w-auto flex justify-center shrink-0">
          <div class="list-search-row">
            <div class="list-search-group">
              <select v-model="searchType" class="list-search-select-inline">
                <option value="ALL">전체</option>
                <option value="NAME">이름</option>
                <option value="CREATOR">등록자</option>
                <option value="TAG">태그</option>
              </select>
              <div class="list-search-input-inner">
                <BaseInput
                  v-model="searchInput"
                  @keyup.enter="handleSearch"
                  placeholder="검색어를 입력하세요"
                  inputClass="list-search-input"
                  hideLabel
                >
                  <template #prefix>
                    <Search class="h-5 w-5 text-gray-400" />
                  </template>
                  <template #suffix>
                    <button
                      v-if="isSearching"
                      type="button"
                      @click="clearSearch"
                      aria-label="Clear search"
                      class="text-gray-400 hover:text-gray-500 dark:hover:text-gray-300 cursor-pointer"
                    >
                      <X class="h-5 w-5" />
                    </button>
                  </template>
                </BaseInput>
              </div>
              <BaseButton @click="handleSearch" variant="secondary" type="button" class="list-search-btn">
                검색
              </BaseButton>
            </div>
          </div>
        </div>

        <!-- 등록 버튼 (우측) -->
        <div class="flex-1 min-w-0 w-full sm:w-auto flex justify-end">
          <router-link
            v-if="authStore.isAuthenticated"
            to="/emoticons/register"
            class="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 whitespace-nowrap"
          >
            <PlusCircle class="-ml-1 mr-2 h-5 w-5" />
            등록하기
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>
