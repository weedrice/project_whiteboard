<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Eye, Video } from 'lucide-vue-next'
import type { FeedPost } from '@/types'
import { formatDateOnly } from '@/utils/date'
import { getOptimizedBoardIconUrl, getOptimizedPostImageUrl, handleImageError } from '@/utils/image'
import { buildPostDetailPath, getFeedBodyHtml, getFeedMediaPreview, isFeedSpoiler } from '@/utils/feedPreview'

const props = withDefaults(defineProps<{
  post: FeedPost
  variant?: 'featured' | 'compact' | 'grid'
}>(), {
  variant: 'grid',
})

const router = useRouter()
const { t } = useI18n()

const bodyHtml = computed(() => getFeedBodyHtml(props.post))
const mediaPreview = computed(() => getFeedMediaPreview(props.post))
const showFirstVideo = computed(() => mediaPreview.value.showFirstVideo)
const showFirstImageUrl = computed(() => mediaPreview.value.imageUrl)
const isSpoiler = computed(() => isFeedSpoiler(props.post))
const categoryName = computed(() => props.post.category?.name?.trim() || '')
const hasMedia = computed(() => showFirstVideo.value || !!showFirstImageUrl.value)

const cardClass = computed(() => {
  if (props.variant === 'featured') return 'nv-home-card nv-home-card-featured'
  if (props.variant === 'compact') return 'nv-home-card nv-home-card-compact'
  return 'nv-home-card nv-home-card-grid'
})

const navigateToPost = () => {
  router.push(buildPostDetailPath(props.post.boardUrl, props.post.postId))
}

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    navigateToPost()
  }
}
</script>

<template>
  <article
    :class="cardClass"
    role="link"
    tabindex="0"
    :aria-label="t('home.card.ariaLabel', { boardName: post.boardName, title: post.title })"
    @click="navigateToPost"
    @keydown="handleKeydown"
  >
    <div class="nv-home-card-top">
      <div class="flex min-w-0 items-center gap-3">
        <div class="flex-shrink-0">
          <img
            v-if="post.boardIconUrl"
            :src="getOptimizedBoardIconUrl(post.boardIconUrl)"
            alt=""
            class="h-9 w-9 rounded-full border border-[var(--nv-line)] object-cover"
            loading="lazy"
            @error="handleImageError($event)"
          />
          <div
            v-else
            class="flex h-9 w-9 items-center justify-center rounded-full border border-[var(--nv-line)] bg-[var(--nv-surface-2)] text-xs font-semibold text-[var(--nv-ink-soft)]"
          >
            {{ post.boardName.substring(0, 1) }}
          </div>
        </div>
        <div class="min-w-0">
          <p class="truncate text-sm font-semibold text-[var(--nv-ink)]">{{ post.boardName }}</p>
          <div class="flex items-center gap-2 text-xs text-[var(--nv-muted)]">
            <span>{{ formatDateOnly(post.createdAt) }}</span>
            <span class="inline-flex items-center gap-1">
              <Eye class="h-3 w-3" />
              {{ post.viewCount }}
            </span>
          </div>
        </div>
      </div>
      <span v-if="categoryName" class="nv-home-chip">
        {{ categoryName }}
      </span>
    </div>

    <div
      v-if="hasMedia"
      class="nv-home-media"
      :class="{ 'pointer-events-none select-none opacity-40 blur-[10px]': isSpoiler }"
    >
      <div v-if="showFirstVideo" class="relative overflow-hidden rounded-[inherit] bg-[var(--nv-surface-2)]">
        <div class="absolute left-3 top-3 z-10 inline-flex items-center gap-1 rounded-full bg-black/60 px-2 py-1 text-[10px] font-medium text-white">
          <Video class="h-3 w-3" />
          {{ $t('home.card.video') }}
        </div>
        <iframe
          :src="post.firstMediaUrl"
          :title="t('home.card.videoPreview')"
          frameborder="0"
          allowfullscreen
          loading="lazy"
          allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
          class="pointer-events-none aspect-video w-full"
        />
      </div>
      <img
        v-else-if="showFirstImageUrl"
        :src="getOptimizedPostImageUrl(showFirstImageUrl)"
        alt=""
        class="aspect-[16/9] w-full rounded-[inherit] object-cover"
        loading="lazy"
        @error="handleImageError($event)"
      />
    </div>

    <div class="space-y-3">
      <h2 class="nv-home-card-title">{{ post.title }}</h2>
      <div
        v-if="bodyHtml"
        class="nv-home-card-body prose-feed"
        :class="{ 'pointer-events-none select-none opacity-40 blur-[8px]': isSpoiler }"
        v-html="bodyHtml"
      />
      <p
        v-else-if="post.summary"
        class="nv-home-card-body"
        :class="{ 'pointer-events-none select-none opacity-40 blur-[8px]': isSpoiler }"
      >
        {{ post.summary }}
      </p>
      <p class="text-sm text-[var(--nv-muted)]">
        {{ post.authorName }}
      </p>
    </div>
  </article>
</template>
