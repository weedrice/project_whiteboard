<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import { Search, X, PlusCircle, TrendingUp } from 'lucide-vue-next'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseSegmentedControl from '@/components/common/ui/BaseSegmentedControl.vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import { DEFAULT_EMOTICON_IMAGE_URL, applyImageFallback } from '@/utils/imageFallback'
import { formatInteger } from '@/utils/numberFormat'
import { useEmoticonListResource } from '@/composables/useEmoticonListResource'
import type { EmoticonSearchParams } from '@/types/emoticon'

const authStore = useAuthStore()
const { t } = useI18n()
const {
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
} = useEmoticonListResource()

useHead({
  title: computed(() => t('emoticon.title')),
  meta: [
    { name: 'description', content: computed(() => t('emoticon.list.description')) }
  ]
})

type PopularPeriod = 'daily' | 'weekly' | 'monthly'
type SortOption = NonNullable<EmoticonSearchParams['sortBy']>

const periodOptions = computed<Array<{ value: PopularPeriod; label: string }>>(() => [
  { value: 'daily', label: t('emoticon.list.period.daily') },
  { value: 'weekly', label: t('emoticon.list.period.weekly') },
  { value: 'monthly', label: t('emoticon.list.period.monthly') }
])

const sortOptions = computed<Array<{ value: SortOption; label: string }>>(() => [
  { value: 'latest', label: t('emoticon.list.sort.latest') },
  { value: 'oldest', label: t('emoticon.list.sort.oldest') },
  { value: 'popular', label: t('emoticon.list.sort.popular') }
])

function changePopularPeriod(period: string) {
  popularPeriod.value = period as PopularPeriod
}

function selectSortBy(value: string) {
  changeSortBy(value as SortOption)
}
</script>

<template>
  <div class="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <!-- 페이지 헤더 -->
    <div class="mb-8">
      <h1 class="text-2xl font-bold nv-title">{{ t('emoticon.title') }}</h1>
      <p class="mt-1 text-sm nv-text-subtle">{{ t('emoticon.list.description') }}</p>
    </div>

    <!-- 인기 노비콘 섹션 -->
    <section class="mb-12">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center gap-2">
          <TrendingUp class="w-5 h-5 nv-accent-text" />
          <h2 class="text-lg font-semibold nv-title">{{ t('emoticon.list.popular') }}</h2>
        </div>
        <BaseSegmentedControl
          :model-value="popularPeriod"
          :options="periodOptions"
          :label="t('emoticon.list.popular')"
          variant="pill"
          @change="changePopularPeriod"
        />
      </div>

      <!-- 인기 노비콘 그리드 -->
      <div v-if="popularLoading" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        <div
          v-for="i in 5"
          :key="i"
          class="relative nv-surface rounded-lg shadow-sm border nv-border overflow-hidden animate-pulse"
        >
          <div class="absolute top-2 left-2 z-10 w-6 h-6 nv-surface-muted rounded-full"></div>
          <div class="aspect-square nv-surface-muted"></div>
          <div class="p-3 space-y-2">
            <div class="h-4 nv-surface-muted rounded w-3/4"></div>
            <div class="h-3 nv-surface-muted rounded w-1/2"></div>
            <div class="h-3 nv-surface-muted rounded w-2/3"></div>
          </div>
        </div>
      </div>
      <div v-else-if="popularEmoticons && popularEmoticons.length > 0" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        <button
          v-for="(emoticon, index) in popularEmoticons"
          :key="emoticon.emoticonId"
          type="button"
          :aria-label="emoticon.name"
          @click="goToDetail(emoticon.emoticonId)"
          class="relative w-full nv-surface rounded-lg shadow-sm border nv-border overflow-hidden cursor-pointer text-left hover:shadow-md nv-focus-ring transition-shadow"
        >
          <!-- 순위 뱃지 -->
          <span class="absolute top-2 left-2 z-10 w-6 h-6 bg-[var(--nv-accent)] text-white text-xs font-bold rounded-full flex items-center justify-center">
            {{ index + 1 }}
          </span>
          <!-- 썸네일 -->
          <span class="block aspect-square nv-surface-muted">
            <img
              :src="emoticon.thumbnailUrl || DEFAULT_EMOTICON_IMAGE_URL"
              :alt="emoticon.name"
              class="w-full h-full object-contain"
              @error="applyImageFallback"
            />
          </span>
          <!-- 정보 -->
          <span class="block p-3">
            <span class="block text-sm font-medium nv-title truncate">{{ emoticon.name }}</span>
            <span class="block text-xs nv-text-subtle truncate">{{ emoticon.creatorName }}</span>
            <span class="block text-xs nv-accent-text mt-1">
              {{ t('emoticon.list.salesCount', { count: formatInteger(emoticon.purchaseCount) }) }}
            </span>
          </span>
        </button>
      </div>
      <div v-else class="text-center py-8 nv-text-subtle">
        {{ t('emoticon.list.popularEmpty') }}
      </div>
    </section>

    <!-- 전체 노비콘 섹션 -->
    <section>
      <div class="flex flex-col gap-3 mb-4 sm:flex-row sm:items-center sm:justify-between">
        <h2 class="text-lg font-semibold nv-title">
          {{ t('emoticon.list.all') }} <span class="text-sm font-normal nv-text-subtle">({{ t('emoticon.list.itemCount', { count: formatInteger(totalElements) }) }})</span>
        </h2>
        <BaseSegmentedControl
          :model-value="sortBy"
          :options="sortOptions"
          :label="t('emoticon.list.all')"
          variant="pill"
          @change="selectSortBy"
        />
      </div>

      <!-- 전체 노비콘 그리드 -->
      <div v-if="emoticonsLoading" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        <div
          v-for="i in 10"
          :key="i"
          class="nv-surface rounded-lg shadow-sm border nv-border overflow-hidden animate-pulse"
        >
          <div class="aspect-square nv-surface-muted"></div>
          <div class="p-3 space-y-2">
            <div class="h-4 nv-surface-muted rounded w-3/4"></div>
            <div class="h-3 nv-surface-muted rounded w-1/2"></div>
            <div class="h-3 nv-surface-muted rounded w-2/3"></div>
          </div>
        </div>
      </div>
      <div v-else-if="emoticons.length > 0" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        <button
          v-for="emoticon in emoticons"
          :key="emoticon.emoticonId"
          type="button"
          :aria-label="emoticon.name"
          @click="goToDetail(emoticon.emoticonId)"
          class="w-full nv-surface rounded-lg shadow-sm border nv-border overflow-hidden cursor-pointer text-left hover:shadow-md nv-focus-ring transition-shadow"
        >
          <!-- 썸네일 -->
          <span class="block aspect-square nv-surface-muted">
            <img
              :src="emoticon.thumbnailUrl || DEFAULT_EMOTICON_IMAGE_URL"
              :alt="emoticon.name"
              class="w-full h-full object-contain"
              @error="applyImageFallback"
            />
          </span>
          <!-- 정보 -->
          <span class="block p-3">
            <span class="block text-sm font-medium nv-title truncate">{{ emoticon.name }}</span>
            <span class="block text-xs nv-text-subtle truncate">{{ emoticon.creatorName }}</span>
            <span class="block text-xs nv-accent-text mt-1">
              {{ t('emoticon.list.salesCount', { count: formatInteger(emoticon.purchaseCount) }) }}
            </span>
          </span>
        </button>
      </div>
      <div v-else class="text-center py-12 nv-text-subtle">
        {{ t('emoticon.list.empty') }}
      </div>

      <!-- 페이지네이션 -->
      <div v-if="totalPages > 1" class="mt-8 flex justify-center">
        <Pagination :current-page="currentPage" :total-pages="totalPages" @page-change="goToPage" />
      </div>
    </section>

    <!-- 검색 바 (가운데) & 등록 버튼 (오른쪽) - 게시글 목록과 동일 레이아웃 -->
    <div class="mt-4 px-4 py-4 sm:px-6 nv-surface-muted rounded-lg transition-colors duration-200">
      <div class="flex flex-col sm:flex-row items-center gap-4">
        <div class="flex-1 min-w-0 hidden sm:block" aria-hidden="true"></div>
        <div class="w-full sm:w-auto flex justify-center shrink-0">
          <div class="list-search-row">
            <div class="list-search-group">
              <select v-model="searchType" class="list-search-select-inline" :aria-label="$t('emoticon.search.typeLabel')">
                <option value="ALL">{{ t('emoticon.list.searchType.all') }}</option>
                <option value="NAME">{{ t('emoticon.list.searchType.name') }}</option>
                <option value="CREATOR">{{ t('emoticon.list.searchType.creator') }}</option>
                <option value="TAG">{{ t('emoticon.list.searchType.tag') }}</option>
              </select>
              <div class="list-search-input-inner">
                <BaseInput
                  v-model="searchInput"
                  @keyup.enter="handleSearch"
                  :label="t('emoticon.picker.searchAria')"
                  :placeholder="t('search.query')"
                  inputClass="list-search-input"
                  hideLabel
                >
                  <template #prefix>
                    <Search class="h-5 w-5 nv-text-subtle" />
                  </template>
                  <template #suffix>
                    <button
                      v-if="isSearching"
                      type="button"
                      @click="clearSearch"
                      :aria-label="$t('emoticon.search.clear')"
                      class="nv-text-subtle hover:text-[var(--nv-text)] cursor-pointer"
                    >
                      <X class="h-5 w-5" />
                    </button>
                  </template>
                </BaseInput>
              </div>
              <BaseButton @click="handleSearch" variant="secondary" type="button" class="list-search-btn">
                {{ t('search.doSearch') }}
              </BaseButton>
            </div>
          </div>
        </div>

        <!-- 등록 버튼 (우측) -->
        <div class="flex-1 min-w-0 w-full sm:w-auto flex justify-end">
          <router-link
            v-if="authStore.isAuthenticated"
            to="/emoticons/register"
            class="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-[var(--nv-accent)] hover:brightness-95 nv-focus-ring whitespace-nowrap"
          >
            <PlusCircle class="-ml-1 mr-2 h-5 w-5" />
            {{ t('emoticon.list.register') }}
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>
