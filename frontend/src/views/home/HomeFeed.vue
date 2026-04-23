<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { CircleDot, FileText, TrendingUp as TrendingUpIcon } from 'lucide-vue-next'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import PostListSkeleton from '@/components/common/ui/PostListSkeleton.vue'
import HomePostCard from '@/components/home/HomePostCard.vue'
import HomeActivityList from '@/components/home/HomeActivityList.vue'
import { useHomeLanding } from '@/composables/useHomeLanding'
import type { HomeLandingPeriod } from '@/types'

const { t, locale } = useI18n()
const homeTitle = computed(() => t('common.appName'))

const {
  featured,
  editorPicks,
  trending,
  liveActivity,
  spotlightBoards,
  boards,
  stats,
  selectedPeriod,
  setPeriod,
  isLoading,
  isFetching,
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
const boardStrip = computed(() => spotlightBoards.value.slice(0, 6))
const numberFormatter = computed(() => new Intl.NumberFormat(locale.value === 'ko' ? 'ko-KR' : 'en-US'))
const formatNumber = (value: number) => numberFormatter.value.format(value)
const formatSignedNumber = (value: number) => (value > 0 ? `+${formatNumber(value)}` : formatNumber(value))
const formatSignedPercent = (value: number) => (value > 0 ? `+${value}%` : `${value}%`)
const formatPostsTodayDelta = (value: number | null) => {
  if (value == null) {
    return t('home.landing.statsCards.noComparisonData')
  }
  const percent = formatSignedPercent(value)
  return t('home.landing.statsCards.postsTodayDeltaVsYesterday', { value: percent })
}

const siteStatsCards = computed(() => [
  {
    label: t('home.landing.statsCards.postsToday'),
    value: formatNumber(stats.value.postsToday),
    meta: formatPostsTodayDelta(stats.value.postsTodayDeltaPercent),
  },
  {
    label: t('home.landing.statsCards.activeBoards'),
    value: formatNumber(stats.value.activeBoardCount),
    meta: t('home.landing.statsCards.activeBoardsMeta'),
  },
  {
    label: t('home.landing.statsCards.newMembers'),
    value: formatSignedNumber(stats.value.newMembersLast24Hours),
    meta: t('home.landing.statsCards.newMembersMeta'),
  },
  {
    label: t('home.landing.statsCards.comments'),
    value: formatNumber(stats.value.commentsToday),
    meta: t('home.landing.statsCards.commentsMeta'),
  },
])

const trendingPeriods = computed<{ value: HomeLandingPeriod; label: string }[]>(() => [
  { value: '24h', label: t('home.landing.trendingPeriods.last24Hours') },
  { value: '7d', label: t('home.landing.trendingPeriods.last7Days') },
  { value: '30d', label: t('home.landing.trendingPeriods.last30Days') },
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
        <h1 class="sr-only">{{ $t('home.landing.curatedToday') }}</h1>
        <div class="flex items-center justify-between gap-4">
          <div>
            <p class="nv-home-section-kicker">{{ $t('home.landing.curatedToday') }}</p>
            <div class="mt-3 inline-flex items-center gap-2 rounded-full border border-[var(--nv-line)] bg-[var(--nv-surface)] px-4 py-2 text-[11px] font-medium uppercase tracking-[0.18em] text-[var(--nv-ink-soft)] shadow-[var(--nv-shadow-soft)]">
              <CircleDot class="h-3.5 w-3.5 text-[var(--nv-accent)]" />
              <span>{{ $t('home.landing.liveNow') }}</span>
              <span>&middot;</span>
              <strong class="text-[var(--nv-ink)]">{{ formatNumber(stats.onlineCount) }}</strong>
              <span>{{ $t('home.landing.online') }}</span>
            </div>
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
              <TrendingUpIcon class="h-4 w-4 text-[var(--nv-accent)]" />
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

      <section class="space-y-4" :aria-busy="isFetching ? 'true' : 'false'">
        <div class="flex items-center justify-between gap-3">
          <div>
            <p class="nv-home-section-kicker">{{ $t('home.landing.discover') }}</p>
            <h2 class="text-xl font-semibold tracking-[-0.04em] text-[var(--nv-ink)]">{{ $t('home.landing.topBoards') }}</h2>
          </div>
          <RouterLink to="/boards" class="text-sm font-medium text-[var(--nv-accent)] hover:underline">
            {{ $t('common.viewAll') }}
          </RouterLink>
        </div>

        <div v-if="isBoardsLoading" class="grid grid-cols-2 gap-3 xl:grid-cols-6">
          <div
            v-for="index in 6"
            :key="index"
            class="rounded-[18px] border border-dashed border-[var(--nv-line)] px-4 py-4 text-sm text-[var(--nv-muted)]"
          >
            {{ $t('home.landing.loadingBoards') }}
          </div>
        </div>
        <div v-else-if="boardStrip.length" class="grid grid-cols-2 gap-3 xl:grid-cols-6">
          <RouterLink
            v-for="board in boardStrip"
            :key="board.boardId"
            :to="`/board/${board.boardUrl}`"
            class="group flex min-h-[112px] flex-col justify-between rounded-[18px] border border-[var(--nv-line)] bg-[var(--nv-surface)] px-4 py-4 shadow-[var(--nv-shadow-soft)] transition-all duration-150 hover:-translate-y-0.5 hover:border-[var(--nv-accent)] hover:shadow-[var(--nv-shadow-card)]"
          >
            <div class="min-w-0">
              <p class="truncate text-sm font-semibold uppercase tracking-[0.14em] text-[var(--nv-muted)]">
                / {{ board.boardUrl }}
              </p>
              <p class="mt-3 line-clamp-2 text-base font-semibold text-[var(--nv-ink)] group-hover:text-[var(--nv-accent)]">
                {{ board.boardName }}
              </p>
            </div>
            <div class="mt-4 flex items-center justify-between gap-2 text-right">
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
          <div class="flex items-center gap-1 rounded-full border border-[var(--nv-line)] bg-[var(--nv-surface)] p-1">
            <button
              v-for="period in trendingPeriods"
              :key="period.value"
              type="button"
              class="rounded-full px-3 py-1.5 text-[11px] font-medium uppercase tracking-[0.12em] transition-colors"
              :class="selectedPeriod === period.value ? 'bg-[var(--nv-ink)] text-[var(--nv-bg)]' : 'text-[var(--nv-ink-soft)] hover:text-[var(--nv-ink)]'"
              :aria-pressed="selectedPeriod === period.value"
              :disabled="isFetching"
              @click="setPeriod(period.value)"
            >
              {{ period.label }}
            </button>
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
