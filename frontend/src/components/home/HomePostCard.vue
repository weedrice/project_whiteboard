<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Eye, ThumbsUp, Video } from 'lucide-vue-next'
import SanitizedHtmlView from '@/components/common/SanitizedHtmlView.vue'
import type { FeedPost } from '@/types'
import { formatTimeAgo } from '@/utils/date'
import { getOptimizedBoardIconUrl, getOptimizedPostImageUrl, handleImageError } from '@/utils/image'
import { buildPostDetailPath, getFeedBodyHtml, getFeedMediaPreview, isFeedSpoiler } from '@/utils/feedPreview'
import { formatInteger } from '@/utils/numberFormat'

const props = withDefaults(defineProps<{
  post: FeedPost
  variant?: 'featured' | 'compact' | 'grid'
  showMediaPreview?: boolean
  showBody?: boolean
  showAuthor?: boolean
}>(), {
  variant: 'grid',
  showMediaPreview: true,
  showBody: true,
  showAuthor: true,
})

const router = useRouter()
const { t } = useI18n()

const isBlinded = computed(() => Boolean(props.post.isBlinded))
const bodyHtml = computed(() => (!props.showBody || isBlinded.value ? null : getFeedBodyHtml(props.post)))
const mediaPreview = computed(() => (!props.showMediaPreview || isBlinded.value
  ? { showFirstVideo: false, videoUrl: null, imageUrl: null }
  : getFeedMediaPreview(props.post)))
const isVideoPreviewLoaded = ref(false)
const showFirstVideo = computed(() => mediaPreview.value.showFirstVideo)
const firstVideoUrl = computed(() => mediaPreview.value.videoUrl)
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
  { 'select-none opacity-40 blur-[8px]': isSpoiler.value },
])
const bodyContentClass = computed(() =>
  isFeatured.value
    ? 'nv-rich-content prose prose-sm max-w-none dark:prose-invert nv-home-curation-body'
    : 'prose-feed',
)

const cardClass = computed(() => {
  if (props.variant === 'featured') return 'nv-home-card nv-elevated-surface nv-home-card-featured'
  if (props.variant === 'compact') return 'nv-home-card nv-elevated-surface nv-home-card-compact'
  return 'nv-home-card nv-elevated-surface nv-home-card-grid'
})
const postDetailPath = computed(() => buildPostDetailPath(props.post.boardUrl, props.post.postId))

const navigateToPost = (event: MouseEvent) => {
  if (
    event.defaultPrevented
    || event.button !== 0
    || event.metaKey
    || event.ctrlKey
    || event.shiftKey
    || event.altKey
  ) {
    return
  }

  event.preventDefault()
  router.push(postDetailPath.value)
}

const loadVideoPreview = () => {
  isVideoPreviewLoaded.value = true
}

watch(() => props.post.postId, () => {
  isVideoPreviewLoaded.value = false
})
</script>

<template>
  <article
    :class="cardClass"
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
            decoding="async"
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
          <div class="nv-home-card-meta">
            <template v-if="isFeatured">
              <span v-if="showAuthor" class="truncate">{{ post.authorName }}</span>
              <span v-if="showAuthor" aria-hidden="true">&middot;</span>
              <span class="whitespace-nowrap">{{ timeAgo }}</span>
              <span class="inline-flex items-center gap-1 whitespace-nowrap text-[var(--nv-ink-soft)]">
                <ThumbsUp class="h-3 w-3" />
                {{ formatInteger(post.likeCount) }}
              </span>
            </template>
            <span class="inline-flex items-center gap-1">
              <Eye class="h-3 w-3" />
              {{ formatInteger(post.viewCount) }}
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
        { 'select-none opacity-40 blur-[10px]': isSpoiler },
      ]"
    >
      <div
        v-if="showFirstVideo"
        class="relative overflow-hidden rounded-[inherit] bg-[var(--nv-surface-2)]"
        :class="[
          isFeatured ? 'w-fit max-w-full' : 'w-full',
          { 'pointer-events-none': isSpoiler },
        ]"
      >
        <div class="absolute left-3 top-3 z-10 inline-flex items-center gap-1 rounded-full bg-[color-mix(in_srgb,var(--nv-scrim)_60%,transparent)] px-2 py-1 text-xs font-medium text-[var(--nv-on-media)]">
          <Video class="h-3 w-3" />
          {{ t('home.card.video') }}
        </div>
        <iframe
          v-if="isVideoPreviewLoaded"
          :src="firstVideoUrl || undefined"
          :title="t('home.card.videoPreview')"
          frameborder="0"
          allowfullscreen
          loading="lazy"
          sandbox="allow-scripts allow-same-origin allow-presentation"
          referrerpolicy="strict-origin-when-cross-origin"
          allow="encrypted-media; picture-in-picture"
          class="aspect-video rounded-[inherit]"
          :class="isFeatured ? 'max-w-[32rem] w-full' : 'w-full'"
          @click.stop
        />
        <button
          v-else
          type="button"
          class="flex aspect-video items-center justify-center rounded-[inherit] text-[var(--nv-on-media)]"
          :class="isFeatured ? 'max-w-[32rem] w-full' : 'w-full'"
          :aria-label="t('home.card.videoPreview')"
          @click.stop="loadVideoPreview"
        >
          <span class="inline-flex h-12 w-12 items-center justify-center rounded-full bg-[var(--nv-media-control-bg)] shadow-lg">
            <Video class="h-5 w-5" aria-hidden="true" />
          </span>
        </button>
      </div>
      <a
        v-else-if="showFirstImageUrl"
        :href="postDetailPath"
        class="nv-home-card-image-link rounded-[inherit]"
        :class="isFeatured ? 'w-fit max-w-full' : 'w-full'"
        :aria-label="post.title"
        @click.stop="navigateToPost"
      >
        <img
          :src="getOptimizedPostImageUrl(showFirstImageUrl)"
          alt=""
          class="rounded-[inherit]"
          :class="isFeatured ? 'h-auto w-auto max-h-[18rem] max-w-full object-contain' : 'aspect-[16/9] w-full object-cover'"
          loading="lazy"
          decoding="async"
          @error="handleImageError($event)"
        />
      </a>
    </div>

    <div class="space-y-3">
      <h2 class="nv-home-card-title">
        <a
          :href="postDetailPath"
          class="nv-home-card-title-link"
          @click.stop="navigateToPost"
        >
          {{ post.title }}
        </a>
      </h2>
      <a
        v-if="bodyHtml"
        :class="bodyClass"
        :href="postDetailPath"
        :aria-label="post.title"
        @click.stop="navigateToPost"
      >
        <SanitizedHtmlView
          :class="bodyContentClass"
          :html="bodyHtml"
        />
      </a>
      <a
        v-else-if="showBody && post.summary"
        :class="bodyClass"
        :href="postDetailPath"
        :aria-label="post.title"
        @click.stop="navigateToPost"
      >
        {{ post.summary }}
      </a>
      <p
        v-if="!isFeatured && showAuthor"
        class="text-sm text-[var(--nv-muted)]"
      >
        {{ post.authorName }}
      </p>
    </div>

  </article>
</template>
