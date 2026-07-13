<script setup lang="ts">
import { computed } from 'vue'
import { Rss } from 'lucide-vue-next'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import HomePostCard from '@/components/home/HomePostCard.vue'
import { useIntersectionObserver } from '@/composables/useIntersectionObserver'
import { usePersonalFeed } from '@/features/feed/usePersonalFeed'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const userId = computed(() => authStore.user?.userId)
const enabled = computed(() => authStore.isAuthenticated && userId.value != null)
const {
  posts,
  isLoading,
  isError,
  isFetchingNextPage,
  hasNextPage,
  fetchNextPage,
  refetch,
} = usePersonalFeed(userId, enabled)

function loadMore() {
  if (!hasNextPage.value || isFetchingNextPage.value) return
  void fetchNextPage()
}

const { targetRef } = useIntersectionObserver((isIntersecting) => {
  if (isIntersecting) loadMore()
}, { rootMargin: '240px 0px' })

defineExpose({ refresh: refetch })
</script>

<template>
  <section
    id="home-personal-feed-panel"
    role="tabpanel"
    aria-labelledby="home-personal-feed-tab"
    class="space-y-5"
  >
    <div>
      <p class="nv-kicker">{{ $t('home.personal.kicker') }}</p>
      <h2 class="mt-2 text-2xl font-semibold tracking-[-0.04em] text-[var(--nv-ink)]">
        {{ $t('home.personal.title') }}
      </h2>
      <p class="mt-2 text-sm leading-6 text-[var(--nv-muted)]">
        {{ $t('home.personal.description') }}
      </p>
    </div>

    <div v-if="isLoading" class="flex min-h-48 items-center justify-center" role="status">
      <BaseSpinner size="lg" />
      <span class="sr-only">{{ $t('home.personal.loading') }}</span>
    </div>

    <ErrorState
      v-else-if="isError"
      title-tag="h3"
      :message="$t('home.personal.loadFailed')"
      show-retry
      @retry="refetch"
    />

    <EmptyState
      v-else-if="posts.length === 0"
      title-tag="h3"
      :title="$t('home.personal.emptyTitle')"
      :description="$t('home.personal.emptyDescription')"
      :icon="Rss"
      container-class="nv-surface nv-elevated-surface rounded-[24px] border border-[var(--nv-line)]"
    >
      <template #action>
        <div class="flex flex-wrap justify-center gap-2">
          <BaseButton to="/boards">{{ $t('home.personal.browseSpaces') }}</BaseButton>
          <BaseButton to="/mypage/subscriptions" variant="secondary">
            {{ $t('home.personal.manageSubscriptions') }}
          </BaseButton>
        </div>
      </template>
    </EmptyState>

    <template v-else>
      <div class="grid gap-4 lg:grid-cols-2">
        <HomePostCard
          v-for="post in posts"
          :key="post.postId"
          :post="post"
          variant="grid"
        />
      </div>

      <div ref="targetRef" class="h-px" aria-hidden="true" />
      <div v-if="hasNextPage" class="flex justify-center">
        <BaseButton
          type="button"
          variant="secondary"
          :disabled="isFetchingNextPage"
          @click="loadMore"
        >
          {{ isFetchingNextPage ? $t('home.personal.loadingMore') : $t('home.personal.loadMore') }}
        </BaseButton>
      </div>
    </template>
  </section>
</template>
