<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import HomePostCard from '@/components/home/HomePostCard.vue'
import { useIntersectionObserver } from '@/composables/useIntersectionObserver'
import { usePost } from '@/features/board/posts/queries/usePost'
import { toFeedPosts } from '@/utils/postViewModel'
import ErrorState from '@/components/common/ui/ErrorState.vue'

const props = defineProps<{
  postId: string | number
}>()

const shouldLoad = ref(false)
const postIdRef = computed(() => props.postId)
const { useRelatedPosts } = usePost()
const { data: relatedPosts, isLoading, isError, refetch } = useRelatedPosts(postIdRef, {
  enabled: shouldLoad,
  size: 3,
})
const feedPosts = computed(() => toFeedPosts(relatedPosts.value))

const { targetRef } = useIntersectionObserver((isIntersecting) => {
  if (isIntersecting) {
    shouldLoad.value = true
  }
}, { rootMargin: '240px 0px' })

watch(() => props.postId, () => {
  shouldLoad.value = false
})
</script>

<template>
  <section ref="targetRef" class="nv-related-posts" :aria-busy="isLoading ? 'true' : 'false'">
    <ErrorState
      v-if="isError"
      title-tag="h2"
      :message="$t('common.messages.loadFailed')"
      :show-icon="false"
      show-retry
      @retry="refetch()"
    />
    <template v-else-if="feedPosts.length > 0">
      <div>
        <p class="nv-kicker">{{ $t('board.postDetail.relatedKicker') }}</p>
        <h2 class="mt-1 text-lg font-semibold text-[var(--nv-ink)]">
          {{ $t('board.postDetail.relatedTitle') }}
        </h2>
      </div>
      <div class="grid gap-3 md:grid-cols-2 lg:grid-cols-3">
        <HomePostCard
          v-for="post in feedPosts"
          :key="post.postId"
          :post="post"
          variant="compact"
          :show-media-preview="false"
          :show-body="false"
        />
      </div>
    </template>
  </section>
</template>

<style scoped>
.nv-related-posts {
  display: grid;
  gap: 1rem;
  min-height: 1px;
}
</style>
