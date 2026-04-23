<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { FileText, TrendingUp } from 'lucide-vue-next'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import PostListSkeleton from '@/components/common/ui/PostListSkeleton.vue'
import HomePostCard from '@/components/home/HomePostCard.vue'
import HomeActivityList from '@/components/home/HomeActivityList.vue'
import { useHomeLanding } from '@/composables/useHomeLanding'

const { t } = useI18n()
const homeTitle = computed(() => t('common.appName'))

const {
  featured,
  editorPicks,
  trending,
  liveActivity,
  spotlightBoards,
  posts,
  boards,
  stats,
  isLoading,
  isError,
  isBoardsLoading,
  isBoardsError,
  refetch,
} = useHomeLanding()

const hasLandingContent = computed(() =>
  Boolean(
    featured.value
    || editorPicks.value.length
    || trending.value.length
    || liveActivity.value.length
    || boards.value.length,
  ),
)
const heroPost = computed(() =>
  featured.value
  ?? editorPicks.value[0]
  ?? trending.value[0]
  ?? liveActivity.value[0]
  ?? null,
)
const heroPostId = computed(() => heroPost.value?.postId ?? null)
const visibleTrending = computed(() => trending.value.filter((post) => post.postId !== heroPostId.value))
const visibleLiveActivity = computed(() => liveActivity.value.filter((post) => post.postId !== heroPostId.value))
const siteStatsCards = computed(() => [
  {
    label: t('home.landing.statsCards.postsToday'),
    value: '312',
    meta: t('home.landing.statsCards.postsTodayDelta'),
  },
  {
    label: t('home.landing.statsCards.activeBoards'),
    value: '11',
    meta: t('home.landing.statsCards.activeBoardsMeta'),
  },
  {
    label: t('home.landing.statsCards.newMembers'),
    value: '+47',
    meta: t('home.landing.statsCards.newMembersMeta'),
  },
  {
    label: t('home.landing.statsCards.comments'),
    value: '1,824',
    meta: t('home.landing.statsCards.commentsMeta'),
  },
])

useHead({
  titleTemplate: '%s',
  title: homeTitle,
  meta: [
    {
      name: 'description',
      content: computed(() => t('home.landing.seoDescription', { appName: t('common.appName') })),
    },
    {
      property: 'og:title',
      content: computed(() => t('common.appName')),
    },
    {
      property: 'og:description',
      content: computed(() => t('home.landing.seoDescription', { appName: t('common.appName') })),
    },
  ],
})
</script>

<template>
  <div class="space-y-8 pb-8">
    <PostListSkeleton v-if="isLoading" :count="4" />

    <ErrorState
      v-else-if="isError"
      :message="$t('common.messages.loadFailed')"
      :show-retry="true"
      @retry="refetch"
    />

    <EmptyState
      v-else-if="!hasLandingContent"
      :title="$t('common.noData')"
      :description="$t('board.list.noPosts')"
      :icon="FileText"
      container-class="nv-home-empty"
    />

    <template v-else>
      <section class="nv-home-hero">
        <div class="flex items-center justify-between gap-4">
          <div>
            <p class="nv-home-section-kicker">{{ $t('home.landing.curatedToday') }}</p>
            <h1 class="text-3xl font-semibold tracking-[-0.05em] text-[var(--nv-ink)] sm:text-4xl">
              {{ $t('home.landing.storiesWorthReading') }}
            </h1>
          </div>
          <div class="hidden grid-cols-3 gap-3 text-right sm:grid">
            <div class="nv-home-stat">
              <span class="nv-home-stat-label">{{ $t('home.landing.live') }}</span>
              <strong>{{ stats.liveCount }}</strong>
            </div>
            <div class="nv-home-stat">
              <span class="nv-home-stat-label">{{ $t('home.landing.posts') }}</span>
              <strong>{{ stats.postCount }}</strong>
            </div>
            <div class="nv-home-stat">
              <span class="nv-home-stat-label">{{ $t('home.landing.boards') }}</span>
              <strong>{{ isBoardsLoading || isBoardsError ? '...' : stats.boardCount }}</strong>
            </div>
          </div>
        </div>

        <div class="grid gap-5 lg:grid-cols-[1.45fr_0.95fr]">
          <HomePostCard v-if="heroPost" :post="heroPost" variant="featured" />
          <div
            v-else
            class="rounded-[28px] border border-dashed border-[var(--nv-line)] px-6 py-8 text-sm text-[var(--nv-muted)]"
          >
            {{ $t('home.landing.featuredLoading') }}
          </div>

          <div class="space-y-3">
            <div class="flex items-center gap-2">
              <TrendingUp class="h-4 w-4 text-[var(--nv-accent)]" />
              <p class="nv-home-section-kicker">{{ $t('home.landing.siteStats') }}</p>
            </div>
            <div class="grid grid-cols-2 gap-3">
              <article
                v-for="card in siteStatsCards"
                :key="card.label"
                class="rounded-[24px] border border-[var(--nv-line)] bg-[var(--nv-surface)] px-4 py-4 shadow-[var(--nv-shadow-soft)]"
              >
                <p class="text-[11px] font-medium uppercase tracking-[0.18em] text-[var(--nv-muted)]">
                  {{ card.label }}
                </p>
                <p class="mt-3 text-2xl font-semibold tracking-[-0.05em] text-[var(--nv-ink)]">
                  {{ card.value }}
                </p>
                <p class="mt-2 text-xs text-[var(--nv-ink-soft)]">
                  {{ card.meta }}
                </p>
              </article>
            </div>
          </div>
        </div>
      </section>

      <section class="space-y-4">
        <div class="flex items-center justify-between gap-3">
          <div>
            <p class="nv-home-section-kicker">{{ $t('home.landing.discover') }}</p>
            <h2 class="text-xl font-semibold tracking-[-0.04em] text-[var(--nv-ink)]">{{ $t('home.landing.browseBoards') }}</h2>
          </div>
          <RouterLink to="/boards" class="text-sm font-medium text-[var(--nv-accent)] hover:underline">
            {{ $t('common.viewAll') }}
          </RouterLink>
        </div>

        <div v-if="isBoardsLoading" class="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          <div
            v-for="index in 3"
            :key="index"
            class="rounded-[24px] border border-dashed border-[var(--nv-line)] px-5 py-6 text-sm text-[var(--nv-muted)]"
          >
            {{ $t('home.landing.loadingBoards') }}
          </div>
        </div>
        <div v-else-if="spotlightBoards.length" class="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          <RouterLink
            v-for="board in spotlightBoards"
            :key="board.boardId"
            :to="`/board/${board.boardUrl}`"
            class="nv-home-board-link"
          >
            <div class="min-w-0">
              <p class="truncate text-base font-semibold text-[var(--nv-ink)]">{{ board.boardName }}</p>
              <p class="line-clamp-2 text-sm text-[var(--nv-ink-soft)]">{{ board.description }}</p>
            </div>
            <div class="text-right">
              <p class="nv-home-stat-label">{{ $t('home.landing.subscribers') }}</p>
              <p class="text-sm font-semibold text-[var(--nv-ink)]">{{ board.subscriberCount }}</p>
            </div>
          </RouterLink>
        </div>
        <div
          v-else
          class="rounded-[24px] border border-dashed border-[var(--nv-line)] px-5 py-6 text-sm text-[var(--nv-muted)]"
        >
          {{ isBoardsError ? $t('home.landing.boardsUnavailable') : $t('home.landing.emptyBoards') }}
        </div>
      </section>

      <section class="space-y-4">
        <div class="flex items-center justify-between gap-3">
          <div>
            <p class="nv-home-section-kicker">{{ $t('home.landing.trending') }}</p>
            <h2 class="text-xl font-semibold tracking-[-0.04em] text-[var(--nv-ink)]">{{ $t('home.landing.trendingNow') }}</h2>
          </div>
        </div>

        <div v-if="visibleTrending.length" class="grid gap-4 lg:grid-cols-2">
          <HomePostCard
            v-for="post in visibleTrending"
            :key="post.postId"
            :post="post"
            variant="grid"
          />
        </div>
        <div
          v-else
          class="rounded-[24px] border border-dashed border-[var(--nv-line)] px-5 py-6 text-sm text-[var(--nv-muted)]"
        >
          {{ $t('home.landing.trendingEmpty') }}
        </div>
      </section>

      <section class="space-y-4">
        <div class="flex items-center justify-between gap-3">
          <div>
            <p class="nv-home-section-kicker">{{ $t('home.landing.liveActivity') }}</p>
            <h2 class="text-xl font-semibold tracking-[-0.04em] text-[var(--nv-ink)]">{{ $t('home.landing.liveActivityTitle') }}</h2>
          </div>
        </div>
        <HomeActivityList :posts="visibleLiveActivity" />
      </section>
    </template>
  </div>
</template>
