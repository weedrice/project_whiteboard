<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { CircleDot, FileText, TrendingUp as TrendingUpIcon } from 'lucide-vue-next'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import HomeLandingSkeleton from '@/components/home/HomeLandingSkeleton.vue'
import HomePostCard from '@/components/home/HomePostCard.vue'
import HomeActivityList from '@/components/home/HomeActivityList.vue'
import { useHomeLanding } from '@/composables/useHomeLanding'
import type { HomeLandingPeriod } from '@/types'

const BOARD_STRIP_LIMIT = 7
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
  ?? null,
)
const heroPostId = computed(() => heroPost.value?.postId ?? null)
const visibleTrending = computed(() => trending.value.filter((post) => post.postId !== heroPostId.value))
const visibleLiveActivity = computed(() => liveActivity.value.filter((post) => post.postId !== heroPostId.value))
const boardStrip = computed(() => spotlightBoards.value.slice(0, BOARD_STRIP_LIMIT))
const remainingBoardSlots = computed(() => Math.max(0, BOARD_STRIP_LIMIT - boardStrip.value.length))
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
    <HomeLandingSkeleton v-if="isLoading" />

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
          <div class="nv-home-live-rollup">
            <CircleDot class="nv-home-live-dot h-3.5 w-3.5 flex-shrink-0 text-[var(--nv-accent)]" />
            <span class="nv-home-section-kicker">{{ $t('home.landing.curatedToday') }}</span>
            <span class="text-[var(--nv-muted)]">-</span>
            <strong class="text-[var(--nv-ink)]">{{ formatNumber(stats.onlineCount) }}</strong>
            <span>{{ $t('home.landing.online') }}</span>
          </div>
          <p class="hidden text-right text-[11px] font-medium uppercase tracking-[0.18em] text-[var(--nv-ink-soft)] sm:block">
            {{ formatNumber(stats.postCount) }} {{ $t('home.landing.totalPosts') }}
          </p>
        </div>

        <div class="mt-5 grid gap-5 lg:grid-cols-[1.45fr_0.95fr]">
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
            <div class="overflow-hidden rounded-[16px] border border-[var(--nv-line)] bg-[var(--nv-line)] shadow-[var(--nv-shadow-soft)]">
              <div class="grid grid-cols-2 gap-px">
              <article
                v-for="card in siteStatsCards"
                :key="card.label"
                class="bg-[var(--nv-surface)] px-4 py-4"
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
        </div>
      </section>

      <section class="space-y-4" :aria-busy="isFetching ? 'true' : 'false'">
        <div class="flex items-center justify-between gap-3">
          <div>
            <h2 class="text-xl font-semibold tracking-[-0.04em] text-[var(--nv-ink)]">{{ $t('home.landing.topBoards') }}</h2>
          </div>
          <RouterLink to="/boards" class="text-sm font-medium text-[var(--nv-accent)] hover:underline">
            {{ $t('common.viewAll') }}
          </RouterLink>
        </div>

        <div v-if="isBoardsLoading" class="overflow-hidden rounded-[16px] border border-[var(--nv-line)] bg-[var(--nv-line)]">
          <div class="grid grid-cols-2 gap-px xl:grid-cols-7">
          <div
            v-for="index in 7"
            :key="index"
            class="bg-[var(--nv-surface)] px-4 py-4 text-sm text-[var(--nv-muted)]"
          >
            {{ $t('home.landing.loadingBoards') }}
          </div>
          </div>
        </div>
        <div
          v-else-if="boardStrip.length"
          class="overflow-hidden rounded-[16px] border border-[var(--nv-line)] bg-[var(--nv-line)] shadow-[var(--nv-shadow-soft)]"
        >
          <div class="grid grid-cols-2 gap-px xl:grid-cols-7">
          <RouterLink
            v-for="board in boardStrip"
            :key="board.boardId"
            :to="`/board/${board.boardUrl}`"
            class="group flex min-h-[68px] flex-col bg-[var(--nv-surface)] px-3.5 pt-3 pb-2 transition-all duration-150 hover:bg-[var(--nv-surface-2)]"
          >
            <div class="min-w-0">
              <p class="line-clamp-2 text-[15px] font-semibold leading-5 text-[var(--nv-ink)] group-hover:text-[var(--nv-accent)]">
                {{ board.boardName }}
              </p>
            </div>
            <div class="mt-0.5 flex items-center gap-2 text-left">
              <p class="text-[12px] font-medium tracking-[0.02em] text-[var(--nv-ink-soft)]">{{ formatNumber(board.postCount ?? 0) }}</p>
            </div>
          </RouterLink>
          <RouterLink
            v-if="remainingBoardSlots > 0"
            to="/boards"
            class="nv-home-board-view-all group flex min-h-[68px] flex-col justify-center bg-[var(--nv-bg)] px-3.5 pt-3 pb-2 transition-all duration-150 hover:bg-[var(--nv-surface-2)]"
            :style="{ '--remaining-board-slots': remainingBoardSlots }"
          >
            <span class="text-[15px] font-semibold leading-5 text-[var(--nv-ink)] group-hover:text-[var(--nv-accent)]">
              {{ $t('common.viewAll') }}
            </span>
            <span class="mt-0.5 text-[12px] font-medium tracking-[0.02em] text-[var(--nv-ink-soft)]">
              {{ $t('home.landing.topBoards') }}
            </span>
          </RouterLink>
          </div>
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
            <h2 class="text-xl font-semibold tracking-[-0.04em] text-[var(--nv-ink)]">{{ $t('home.landing.liveActivityTitle') }}</h2>
          </div>
        </div>
        <HomeActivityList :posts="visibleLiveActivity" />
      </section>
    </template>
  </div>
</template>

<style scoped>
@media (min-width: 1280px) {
  .nv-home-board-view-all {
    grid-column: span var(--remaining-board-slots, 1) / span var(--remaining-board-slots, 1);
  }
}
</style>
