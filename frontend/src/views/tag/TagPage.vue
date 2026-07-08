<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Tag } from 'lucide-vue-next'
import { tagApi } from '@/api/tag'
import PostList from '@/components/board/PostList.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import { useApiPageQuery } from '@/composables/useApiQuery'
import { isInquiryPostItem, resolveBoardRoute, resolvePostDetailRoute } from '@/utils/postNavigation'
import { encodePathSegment } from '@/utils/urlPath'

const route = useRoute()
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

const posts = computed(() => data.value?.content ?? [])
const totalPages = computed(() => data.value?.totalPages ?? 0)

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
    </header>

    <div v-if="isLoading" class="py-12 text-center">
      <BaseSpinner size="lg" />
    </div>

    <EmptyState
      v-else-if="isError"
      title="태그 글을 불러오지 못했습니다."
      :icon="Tag"
      action-label="다시 시도"
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
        empty-description="아직 이 태그로 묶인 글이 없습니다."
      />

      <div v-if="totalPages > 1" class="mt-4 flex justify-center">
        <Pagination
          :current-page="pageIndex"
          :total-pages="totalPages"
          :link-builder="buildPageRoute"
        />
      </div>
    </template>
  </main>
</template>
