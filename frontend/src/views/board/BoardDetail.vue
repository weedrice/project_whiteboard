<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ShieldCheck, User } from 'lucide-vue-next'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import BoardNoticeList from '@/components/board/BoardNoticeList.vue'
import BoardPostFilters from '@/components/board/BoardPostFilters.vue'
import BoardPostSearch from '@/components/board/BoardPostSearch.vue'
import PostList from '@/components/board/PostList.vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import UserMenu from '@/components/common/widgets/UserMenu.vue'
import { useBoardDetailNavigation } from '@/composables/useBoardDetailNavigation'
import { useBoardDetailResource } from '@/composables/useBoardDetailResource'
import { useBoardListState } from '@/composables/useBoardListState'
import { useBoardRecentVisit } from '@/composables/useBoardRecentVisit'
import { useBoardSubscriptionAction } from '@/composables/useBoardSubscriptionAction'
import { useAuthStore } from '@/stores/auth'
import { canWriteBoardPost } from '@/utils/board'
import { getOptimizedBoardIconUrl, handleImageError } from '@/utils/image'
import { isInquiryPostItem, resolvePostDetailRoute } from '@/utils/postNavigation'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const {
  page,
  searchQuery,
  searchType,
  isSearching,
  conceptOnly,
  selectedCategoryId,
  sort,
  queryParams,
  buildPaginationRoute,
  handleSearch,
  clearSearch,
  activateAllPostsFilter,
  toggleConceptPosts,
  toggleCategory,
  handleSortChange,
  handlePageChange,
  resetListState
} = useBoardListState(route, router)

const boardUrl = computed(() => route.params.boardUrl as string)
const currentPostId = computed(() => route.params.postId as string | undefined)

const {
  board,
  boardTitle,
  blockingError,
  categories,
  hasNoticeOverflow,
  isInitialLoading,
  isNoticesExpanded,
  isSubscribePending,
  posts,
  resetNoticeState,
  showPostListLoading,
  subscribeMutate,
  totalPages,
  transientListError,
  visibleNotices,
  notices
} = useBoardDetailResource({
  boardUrl,
  queryParams,
  isSearching,
  t
})

useBoardRecentVisit(board)

const canWrite = computed(() => (
  canWriteBoardPost(board.value, authStore.isAuthenticated, authStore.user?.role)
))
const isAllPostsActive = computed(() => (
  !conceptOnly.value
  && selectedCategoryId.value === null
))

const { handleSubscribe } = useBoardSubscriptionAction({
  board,
  isSubscribePending,
  subscribeMutate,
})

const {
  buildBoardListRoute,
  getNoticeRoute,
  highlightedPostId,
  listQuery,
  onPageChange,
  searchInputElementId
} = useBoardDetailNavigation({
  route,
  router,
  boardUrl,
  currentPostId,
  page,
  totalPages,
  board,
  canWrite,
  isSubscribePending,
  handleSubscribe,
  handlePageChange,
  resetListState,
  resetNoticeState
})

useHead({
  title: computed(() => (currentPostId.value ? undefined : boardTitle.value)),
  meta: [
    { name: 'description', content: computed(() => board.value?.description || t('board.seo.spaceDescriptionFallback')) },
    { property: 'og:title', content: computed(() => `${board.value?.boardName || t('board.seo.spaceTitleFallback')} - ${t('common.appName')}`) },
    { property: 'og:description', content: computed(() => board.value?.description || t('board.seo.spaceDescriptionFallback')) }
  ]
})

watch([() => route.name, boardTitle], ([routeName, title]) => {
  if (currentPostId.value || routeName !== 'board-detail' || typeof document === 'undefined') {
    return
  }
  document.title = `${title} - ${t('common.appName')}`
}, { immediate: true })
</script>

<template>
  <div class="nv-board-shell">
    <div v-if="isInitialLoading" class="nv-board-stack">
      <section class="nv-board-panel nv-board-header-panel p-4 sm:p-6">
        <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div class="flex items-start gap-3">
            <BaseSkeleton width="5rem" height="5rem" rounded="rounded-[14px]" className="flex-shrink-0" />
            <div class="space-y-2.5">
              <BaseSkeleton width="220px" height="34px" />
              <div class="flex gap-3">
                <BaseSkeleton width="90px" height="18px" />
                <BaseSkeleton width="120px" height="18px" />
              </div>
              <BaseSkeleton width="320px" height="18px" />
            </div>
          </div>
          <div class="space-y-2 lg:w-32">
            <BaseSkeleton width="100%" height="34px" rounded="rounded-[10px]" />
            <BaseSkeleton width="100%" height="34px" rounded="rounded-[10px]" />
          </div>
        </div>
      </section>

      <section class="nv-board-panel nv-board-list-panel overflow-hidden">
        <div class="border-b border-[var(--nv-line)] px-4 py-3 sm:px-5">
          <div class="flex gap-2 overflow-hidden">
            <BaseSkeleton v-for="index in 4" :key="index" width="72px" height="34px" rounded="rounded-[10px]" />
          </div>
        </div>
        <div class="space-y-3 px-4 py-5 sm:px-5">
          <BaseSkeleton v-for="index in 5" :key="index" width="100%" height="54px" rounded="rounded-[12px]" />
        </div>
      </section>
    </div>

    <section v-else-if="blockingError" class="nv-board-panel nv-board-state-panel px-4 py-12 text-center sm:px-6">
      <p class="nv-board-state-kicker">NODE</p>
      <p class="mt-3 text-sm nv-form-error">{{ blockingError }}</p>
    </section>

    <template v-else-if="board">
      <section class="nv-board-panel nv-board-header-panel p-4 sm:p-6">
        <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div class="flex min-w-0 flex-1 items-start gap-3 sm:gap-4">
            <router-link :to="buildBoardListRoute()" class="nv-board-icon-wrap flex-shrink-0">
              <img
                v-if="board.iconUrl"
                :src="getOptimizedBoardIconUrl(board.iconUrl, 96)"
                class="nv-board-icon"
                alt=""
                @error="handleImageError($event)"
              />
              <div v-else class="nv-board-icon-fallback">
                <span>{{ board.boardName?.[0] || '#' }}</span>
              </div>
            </router-link>

            <div class="min-w-0 flex-1 space-y-2.5">
              <div class="flex flex-wrap items-center gap-2">
                <router-link :to="buildBoardListRoute()" class="inline-flex min-w-0 max-w-full items-center gap-2">
                  <h1 class="truncate text-2xl font-semibold tracking-[-0.04em] text-[var(--nv-ink)] sm:text-3xl">
                    {{ board.boardName }}
                  </h1>
                </router-link>

                <BaseButton
                  v-if="authStore.isAuthenticated"
                  @click="handleSubscribe"
                  size="sm"
                  :variant="board.isSubscribed ? 'secondary' : 'primary'"
                  :disabled="isSubscribePending"
                  :aria-busy="isSubscribePending ? 'true' : 'false'"
                  class="nv-board-subscribe-btn"
                >
                  {{ board.isSubscribed ? $t('common.unsubscribe') : $t('common.subscribe') }}
                </BaseButton>
              </div>

              <div class="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-[var(--nv-ink-soft)]">
                <span class="inline-flex items-center gap-1.5">
                  <User class="h-4 w-4" />
                  {{ $t('common.subscribers') }} {{ board.subscriberCount || 0 }}
                </span>
                <span class="inline-flex items-center gap-1.5">
                  <span class="font-medium text-[var(--nv-ink)]">{{ t('board.detail.defaultAdminName') }}</span>
                  <UserMenu
                    v-if="board.adminUserId"
                    :user-id="board.adminUserId"
                    :display-name="board.adminDisplayName || t('board.detail.defaultAdminName')"
                    size="inherit"
                  />
                  <span v-else>{{ board.adminDisplayName || t('board.detail.defaultAdminName') }}</span>
                </span>
              </div>

              <p class="max-w-3xl text-sm leading-6 text-[var(--nv-ink-soft)] sm:text-[15px]">
                {{ board.description || t('board.list.noDesc') }}
              </p>
            </div>
          </div>

          <div class="flex w-full flex-col gap-2 lg:w-auto lg:min-w-[7rem] lg:items-end lg:self-stretch">
            <div class="flex gap-2 lg:justify-end">
              <router-link
                v-if="board.isAdmin"
                :to="`/board/${board.boardUrl}/edit`"
                class="nv-board-manage-btn"
              >
                <ShieldCheck class="h-4 w-4" />
                {{ $t('common.manage') }}
              </router-link>
            </div>

            <router-link
              v-if="canWrite"
              :to="`/board/${board.boardUrl}/write`"
              class="nv-board-write-btn lg:mt-auto"
            >
              {{ $t('common.write') }}
            </router-link>
          </div>
        </div>
      </section>

      <div v-if="currentPostId" class="mb-3 sm:mb-6">
        <router-view />
      </div>

      <section id="board-post-list" class="nv-board-panel nv-board-list-panel overflow-hidden">
        <BoardNoticeList
          :notices="notices"
          :visible-notices="visibleNotices"
          :has-notice-overflow="hasNoticeOverflow"
          :is-expanded="isNoticesExpanded"
          :highlighted-post-id="highlightedPostId"
          :get-notice-route="getNoticeRoute"
          @update:is-expanded="isNoticesExpanded = $event"
        />

        <BoardPostFilters
          :categories="categories"
          :is-all-posts-active="isAllPostsActive"
          :concept-only="conceptOnly"
          :selected-category-id="selectedCategoryId"
          @activate-all="activateAllPostsFilter"
          @toggle-concept="toggleConceptPosts"
          @toggle-category="toggleCategory"
        />

        <PostList
          :posts="posts"
          :loading="showPostListLoading"
          :boardUrl="board.boardUrl"
          :current-sort="sort"
          :currentPostId="highlightedPostId"
          :linkQuery="listQuery"
          :resolve-post-route="resolvePostDetailRoute"
          :show-inquiry-status="isInquiryPostItem"
          @update:sort="handleSortChange"
        />

        <BoardPostSearch
          v-model:search-query="searchQuery"
          v-model:search-type="searchType"
          :search-input-element-id="searchInputElementId"
          :is-searching="isSearching"
          :can-write="canWrite"
          :board-url="board.boardUrl"
          :transient-list-error="transientListError"
          @search="handleSearch"
          @clear="clearSearch"
        />

        <div
          v-if="totalPages > 1"
          class="flex justify-center px-3 py-3 sm:px-4"
        >
          <Pagination
            :currentPage="page"
            :totalPages="totalPages"
            :linkBuilder="buildPaginationRoute"
            @page-change="onPageChange"
          />
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.nv-board-shell {
  color: var(--nv-ink);
}

.nv-board-stack {
  display: grid;
  gap: 0;
}

.nv-board-panel {
  background: color-mix(in srgb, var(--nv-surface) 94%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 1rem;
  box-shadow: var(--nv-shadow-card);
}

.nv-board-header-panel {
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--nv-surface-2) 45%, transparent), transparent),
    color-mix(in srgb, var(--nv-surface) 96%, transparent);
  border-bottom: 0;
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
}

.nv-board-list-panel {
  border-top-left-radius: 0;
  border-top-right-radius: 0;
}

.nv-board-icon-wrap {
  border-radius: 0.9rem;
  display: inline-flex;
}

.nv-board-icon,
.nv-board-icon-fallback {
  border-radius: 0.9rem;
  height: 5.5rem;
  width: 5.5rem;
}

.nv-board-icon {
  border: 1px solid var(--nv-line);
  object-fit: cover;
}

.nv-board-icon-fallback {
  align-items: center;
  background: var(--nv-accent-bg);
  color: var(--nv-accent);
  display: inline-flex;
  font-size: 2rem;
  font-weight: 700;
  justify-content: center;
}

.nv-board-state-kicker {
  color: var(--nv-muted);
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.68rem;
  font-weight: 500;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.nv-board-manage-btn,
.nv-board-subscribe-btn,
.nv-board-write-btn {
  align-items: center;
  border-radius: 0.55rem;
  display: inline-flex;
  font-size: 0.82rem;
  font-weight: 600;
  justify-content: center;
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease, filter 0.2s ease, box-shadow 0.2s ease;
}

.nv-board-subscribe-btn {
  min-height: 1.95rem;
  padding: 0.35rem 0.65rem;
}

.nv-board-manage-btn {
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  color: var(--nv-ink);
  gap: 0.375rem;
  min-height: 1.95rem;
  padding: 0.35rem 0.65rem;
}

.nv-board-manage-btn:hover {
  background: var(--nv-surface-2);
}

.nv-board-write-btn {
  background: var(--nv-accent);
  border: 1px solid var(--nv-accent);
  color: #fff;
  height: 2.2rem;
  justify-content: center;
  line-height: 1;
  min-height: 2.2rem;
  min-width: 4.75rem;
  padding: 0 0.8rem;
}

.nv-board-write-btn:hover {
  filter: brightness(0.94);
}

.nv-board-subscribe-btn.btn-secondary:not(:disabled):hover {
  background: var(--nv-surface-2);
}

.nv-board-subscribe-btn.btn-primary:not(:disabled):hover {
  filter: brightness(0.94);
}

.nv-board-subscribe-btn:not(:disabled),
.nv-board-write-btn,
.nv-board-search-btn:not(:disabled) {
  cursor: pointer;
}

.nv-board-subscribe-btn:not(:disabled):active,
.nv-board-write-btn:active,
.nv-board-search-btn:not(:disabled):active {
  filter: brightness(0.9);
}

.nv-board-subscribe-btn:focus-visible,
.nv-board-write-btn:focus-visible,
.nv-board-search-btn:focus-visible {
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--nv-accent) 18%, transparent);
  outline: none;
}

.nv-board-subscribe-btn:disabled,
.nv-board-search-btn:disabled {
  cursor: not-allowed;
}

.nv-board-state-panel {
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--nv-danger) 8%, transparent), transparent),
    color-mix(in srgb, var(--nv-surface) 94%, transparent);
}

@media (max-width: 639px) {
  .nv-board-panel {
    border-radius: 0.85rem;
  }

  .nv-board-icon,
  .nv-board-icon-fallback {
    border-radius: 0.8rem;
    height: 4.5rem;
    width: 4.5rem;
  }
}
</style>
