<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Eye, ThumbsUp, Video } from 'lucide-vue-next'
import type { FeedPost } from '@/types'
import { formatTimeAgo } from '@/utils/date'
import { getOptimizedBoardIconUrl, getOptimizedPostImageUrl, handleImageError } from '@/utils/image'
import { buildPostDetailPath, getFeedBodyHtml, getFeedMediaPreview, isFeedSpoiler } from '@/utils/feedPreview'
import { formatInteger } from '@/utils/numberFormat'

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
const isFeatured = computed(() => props.variant === 'featured')
const timeAgo = computed(() => formatTimeAgo(props.post.createdAt, t))
const bodyClampClass = computed(() => {
  if (isFeatured.value) {
    return hasMedia.value ? 'nv-home-card-body-featured-with-media' : 'nv-home-card-body-featured-no-media'
  }
  return hasMedia.value ? 'nv-home-card-body-with-media' : 'nv-home-card-body-no-media'
})
const bodyClass = computed(() => [
  'nv-home-card-body',
  bodyClampClass.value,
  { 'pointer-events-none select-none opacity-40 blur-[8px]': isSpoiler.value },
])
const bodyContentClass = computed(() =>
  isFeatured.value
    ? 'nv-rich-content prose prose-sm max-w-none dark:prose-invert nv-home-curation-body'
    : 'prose-feed',
)

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
      :class="[
        isFeatured ? 'justify-start bg-transparent' : '',
        { 'pointer-events-none select-none opacity-40 blur-[10px]': isSpoiler },
      ]"
    >
      <div
        v-if="showFirstVideo"
        class="relative overflow-hidden rounded-[inherit] bg-[var(--nv-surface-2)]"
        :class="isFeatured ? 'w-fit max-w-full' : 'w-full'"
      >
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
          class="pointer-events-none aspect-video rounded-[inherit]"
          :class="isFeatured ? 'max-w-[38rem] w-full' : 'w-full'"
        />
      </div>
      <img
        v-else-if="showFirstImageUrl"
        :src="getOptimizedPostImageUrl(showFirstImageUrl)"
        alt=""
        class="rounded-[inherit]"
        :class="isFeatured ? 'h-auto w-auto max-h-[26rem] max-w-full object-contain' : 'aspect-[16/9] w-full object-cover'"
        loading="lazy"
        @error="handleImageError($event)"
      />
    </div>

    <div class="space-y-3">
      <h2 class="nv-home-card-title">{{ post.title }}</h2>
      <div
        v-if="bodyHtml"
        :class="bodyClass"
      >
        <div
          :class="bodyContentClass"
          v-html="bodyHtml"
        />
      </div>
      <p
        v-else-if="post.summary"
        :class="bodyClass"
      >
        {{ post.summary }}
      </p>
      <p
        v-if="!isFeatured"
        class="text-sm text-[var(--nv-muted)]"
      >
        {{ post.authorName }}
      </p>
    </div>

    <div
      v-if="isFeatured"
      class="flex items-center gap-2 text-sm text-[var(--nv-muted)]"
    >
      <span class="truncate">{{ post.authorName }}</span>
      <span>&middot;</span>
      <span>{{ timeAgo }}</span>
      <span class="ml-auto inline-flex items-center gap-1 text-[var(--nv-ink-soft)]">
        <ThumbsUp class="h-3.5 w-3.5" />
        {{ formatInteger(post.likeCount) }}
      </span>
    </div>
  </article>
</template>
