<script setup lang="ts">
import { computed } from 'vue'
import { CircleDot, FileText, TrendingUp as TrendingUpIcon } from 'lucide-vue-next'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import BaseSegmentedControl from '@/components/common/ui/BaseSegmentedControl.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import HomeBoardStrip from '@/components/home/HomeBoardStrip.vue'
import HomeAttendancePanel from '@/components/home/HomeAttendancePanel.vue'
import HomeLandingSkeleton from '@/components/home/HomeLandingSkeleton.vue'
import HomePostCard from '@/components/home/HomePostCard.vue'
import HomeActivityList from '@/components/home/HomeActivityList.vue'
import { useHomeLanding } from '@/composables/useHomeLanding'
import type { HomeLandingPeriod } from '@/types'
import {
  createHomeStatsCards,
  createHomeTrendingPeriods,
  filterHomePostsExcludingHero,
  getHomeBoardStrip,
  getHomeRemainingBoardSlots,
  selectHomeHeroPost,
} from '@/views/home/homeFeedModel'

const { t, locale } = useI18n()
const homeTitle = computed(() => t('common.appName'))

const {
  featured,
  editorPicks,
  trending,
  liveActivity,
  spotlightBoards,
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

const heroPost = computed(() => selectHomeHeroPost(featured.value, editorPicks.value, trending.value))
const heroPostId = computed(() => heroPost.value?.postId ?? null)
const visibleTrending = computed(() => filterHomePostsExcludingHero(trending.value, heroPostId.value))
const visibleLiveActivity = computed(() => filterHomePostsExcludingHero(liveActivity.value, heroPostId.value))
const boardStrip = computed(() => getHomeBoardStrip(spotlightBoards.value))
const remainingBoardSlots = computed(() => getHomeRemainingBoardSlots(boardStrip.value.length))
const numberFormatter = computed(() => new Intl.NumberFormat(locale.value === 'ko' ? 'ko-KR' : 'en-US'))
const formatNumber = (value: number) => numberFormatter.value.format(value)

const siteStatsCards = computed(() => createHomeStatsCards(stats.value, {
  postsToday: t('home.landing.statsCards.postsToday'),
  postsTodayDeltaVsYesterday: t('home.landing.statsCards.postsTodayDeltaVsYesterday', { value: '{value}' }),
  noComparisonData: t('home.landing.statsCards.noComparisonData'),
  activeBoards: t('home.landing.statsCards.activeBoards'),
  activeBoardsMeta: t('home.landing.statsCards.activeBoardsMeta'),
  newMembers: t('home.landing.statsCards.newMembers'),
  newMembersMeta: t('home.landing.statsCards.newMembersMeta'),
  comments: t('home.landing.statsCards.comments'),
  commentsMeta: t('home.landing.statsCards.commentsMeta'),
}, formatNumber))

const trendingPeriods = computed<{ value: HomeLandingPeriod; label: string }[]>(() => createHomeTrendingPeriods({
  last24Hours: t('home.landing.trendingPeriods.last24Hours'),
  last7Days: t('home.landing.trendingPeriods.last7Days'),
  last30Days: t('home.landing.trendingPeriods.last30Days'),
}))

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
            class="flex min-h-[15rem] flex-col justify-center rounded-[28px] border border-dashed border-[var(--nv-line)] px-6 py-8 text-sm text-[var(--nv-muted)]"
          >
            <FileText class="mb-4 h-8 w-8 text-[var(--nv-ink-soft)]" />
            <p class="text-base font-semibold text-[var(--nv-ink)]">
              {{ isFetching ? $t('home.landing.featuredLoading') : $t('home.landing.emptyTitle') }}
            </p>
            <p v-if="!isFetching" class="mt-2 max-w-md text-sm leading-6 text-[var(--nv-muted)]">
              {{ $t('home.landing.emptyDescription') }}
            </p>
            <div v-if="!isFetching" class="mt-5 flex flex-wrap gap-2">
              <BaseButton
                to="/board/create"
              >
                {{ $t('home.landing.emptyPrimaryAction') }}
              </BaseButton>
              <BaseButton
                to="/boards"
                variant="secondary"
              >
                {{ $t('home.landing.emptySecondaryAction') }}
              </BaseButton>
            </div>
          </div>

          <div class="space-y-3">
            <HomeAttendancePanel />
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

      <HomeBoardStrip
        :boards="boardStrip"
        :remaining-slots="remainingBoardSlots"
        :is-loading="isBoardsLoading"
        :is-error="isBoardsError"
        :aria-busy="isFetching ? 'true' : 'false'"
      />

      <section class="space-y-4">
        <div class="flex items-center justify-between gap-3">
          <div>
            <h2 class="text-xl font-semibold tracking-[-0.04em] text-[var(--nv-ink)]">{{ $t('home.landing.trendingNow') }}</h2>
          </div>
          <BaseSegmentedControl
            :model-value="selectedPeriod"
            :options="trendingPeriods"
            :label="$t('home.landing.trendingNow')"
            variant="pill"
            :disabled="isFetching"
            @update:model-value="setPeriod($event as HomeLandingPeriod)"
          />
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
        <HomeActivityList v-if="visibleLiveActivity.length" :posts="visibleLiveActivity" />
        <div
          v-else
          class="rounded-[24px] border border-dashed border-[var(--nv-line)] px-5 py-6 text-sm text-[var(--nv-muted)]"
        >
          {{ $t('home.landing.liveActivityEmpty') }}
        </div>
      </section>
    </template>
  </div>
</template>

