<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import { Tag } from 'lucide-vue-next'
import { tagApi } from '@/api/tag'
import PostList from '@/components/board/PostList.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import { useApiPageQuery, useApiQuery } from '@/composables/useApiQuery'
import { isInquiryPostItem, resolveBoardRoute, resolvePostDetailRoute } from '@/utils/postNavigation'
import { encodePathSegment } from '@/utils/urlPath'

const route = useRoute()
const { t } = useI18n()
const tagName = computed(() => String(route.params.name ?? '').trim())
const page = computed(() => {
  const rawPage = Number(route.query.page ?? 1)
  return Number.isFinite(rawPage) && rawPage > 0 ? Math.floor(rawPage) : 1
})
const pageIndex = computed(() => page.value - 1)
const params = computed(() => ({
  page: pageIndex.value,
  size: 20,
  sort: 'createdAt,desc',
}))

const { data, isLoading, isError, refetch } = useApiPageQuery({
  queryKey: computed(() => ['tags', tagName.value, 'posts', params.value]),
  request: (context) => tagApi.getPostsByTagName(tagName.value, params.value, { signal: context.signal }),
  enabled: computed(() => tagName.value.length > 0),
})

const { data: popularTagData } = useApiQuery({
  queryKey: ['tags', 'popular'],
  request: () => tagApi.getPopularTags(),
  staleTime: 300_000,
})

const posts = computed(() => data.value?.content ?? [])
const totalPages = computed(() => data.value?.totalPages ?? 0)
const totalElements = computed(() => data.value?.totalElements ?? 0)
const relatedTags = computed(() => (popularTagData.value?.tags ?? [])
  .filter((tag) => tag.tagName.toLowerCase() !== tagName.value.toLowerCase())
  .slice(0, 8))

useHead({
  title: computed(() => t('search.tagSeoTitle', { tag: tagName.value })),
  meta: [
    {
      name: 'description',
      content: computed(() => t('search.tagSeoDescription', { tag: tagName.value, count: totalElements.value })),
    },
    {
      property: 'og:title',
      content: computed(() => t('search.tagSeoTitle', { tag: tagName.value })),
    },
    {
      property: 'og:description',
      content: computed(() => t('search.tagSeoDescription', { tag: tagName.value, count: totalElements.value })),
    },
  ],
})

function buildPageRoute(nextPageIndex: number) {
  const nextPage = nextPageIndex + 1
  return {
    path: `/tag/${encodePathSegment(tagName.value)}`,
    query: nextPage > 1 ? { page: String(nextPage) } : {},
  }
}
</script>

<template>
  <main class="mx-auto max-w-6xl px-4 py-6 sm:px-6 lg:px-8">
    <header class="mb-5">
      <p class="nv-kicker">TAG</p>
      <h1 class="mt-2 text-2xl font-semibold nv-title">#{{ tagName }}</h1>
      <p class="mt-2 text-sm nv-text-subtle">
        {{ $t('search.tagPostCount', { count: totalElements }) }}
      </p>
    </header>

    <div v-if="isLoading" class="divide-y divide-[var(--nv-border)] rounded-lg border nv-border nv-surface">
      <div v-for="index in 5" :key="index" class="px-4 py-4 sm:px-6">
        <BaseSkeleton width="70%" height="24px" className="mb-2" />
        <div class="flex gap-2">
          <BaseSkeleton width="40px" height="16px" />
          <BaseSkeleton width="60px" height="16px" />
        </div>
      </div>
    </div>

    <EmptyState
      v-else-if="isError"
      :title="$t('search.tagLoadFailed')"
      :icon="Tag"
      :action-label="$t('common.retry')"
      @action="refetch"
    />

    <template v-else>
      <PostList
        :posts="posts"
        :show-board-name="true"
        :hide-no-column="true"
        :resolve-post-route="resolvePostDetailRoute"
        :resolve-board-route="resolveBoardRoute"
        :show-inquiry-status="isInquiryPostItem"
        :empty-description="$t('search.tagEmpty')"
      />

      <div v-if="totalPages > 1" class="mt-4 flex justify-center">
        <Pagination
          :current-page="pageIndex"
          :total-pages="totalPages"
          :link-builder="buildPageRoute"
        />
      </div>

      <section v-if="relatedTags.length > 0" class="mt-8 rounded-lg border nv-border nv-surface p-4">
        <h2 class="text-sm font-semibold nv-title">{{ $t('search.relatedTags') }}</h2>
        <div class="mt-3 flex flex-wrap gap-2">
          <RouterLink
            v-for="tag in relatedTags"
            :key="tag.tagId"
            :to="{ name: 'tag-posts', params: { name: tag.tagName } }"
            class="rounded-full border nv-border px-3 py-1.5 text-sm nv-text nv-hover-surface no-underline"
          >
            #{{ tag.tagName }}
          </RouterLink>
        </div>
      </section>
    </template>
  </main>
</template>
