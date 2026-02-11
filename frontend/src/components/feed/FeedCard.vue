<script setup lang="ts">
import { computed } from 'vue'
import { ThumbsUp, MessageSquare, Bookmark, User, Eye } from 'lucide-vue-next'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseCard from '@/components/common/ui/BaseCard.vue'
import { useRouter } from 'vue-router'
import type { FeedPost } from '@/types'
import { formatDateOnly } from '@/utils/date'
import { getOptimizedBoardIconUrl, getOptimizedPostImageUrl, handleImageError } from '@/utils/image'
import { sanitizeQuillHtml } from '@/utils/sanitize'

const router = useRouter()

const props = defineProps<{
  post: FeedPost
}>()

const emit = defineEmits<{
  (e: 'like', post: FeedPost): void
  (e: 'scrap', post: FeedPost): void
  (e: 'subscribe', post: FeedPost): void
}>()

if (!props.post || !props.post.postId) {
  console.warn('FeedCard: Invalid post data', props.post)
}

/** Feed용 본문: HTML 발췌에서 이미지·비디오 제거 후 sanitize (하단 본문에는 미디어 미노출) */
const bodyHtml = computed(() => {
  const excerpt = props.post?.contentsExcerpt
  if (!excerpt) return null
  let html = sanitizeQuillHtml(excerpt)
  html = html.replace(/<img[^>]*>/gi, '')
  html = html.replace(/<iframe[^>]*>[\s\S]*?<\/iframe>/gi, '')
  html = html.replace(/<div[^>]*\bclass="[^"]*tiptap-video-wrapper[^"]*"[^>]*>[\s\S]*?<\/div>/gi, '')
  return html
})

/** 최초 미디어가 비디오인지 */
const showFirstVideo = computed(() => props.post?.firstMediaType === 'video' && props.post?.firstMediaUrl)

/** 최초 미디어가 이미지이거나 thumbnailUrl이 있을 때 표시할 이미지 URL */
const showFirstImageUrl = computed(() => {
  if (props.post?.firstMediaType === 'image' && props.post?.firstMediaUrl) return props.post.firstMediaUrl
  return props.post?.thumbnailUrl
})

/** 스포일러 여부 (API가 spoiler 또는 isSpoiler로 올 수 있음) */
const isSpoiler = computed(() => Boolean(props.post?.isSpoiler ?? (props.post as { spoiler?: boolean })?.spoiler))
</script>

<template>
  <BaseCard v-if="post && post.postId" noPadding class="mb-6">
    <!-- Header -->
    <div
      class="px-3 sm:px-6 py-3 sm:py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
      <div class="flex items-center space-x-2 sm:space-x-3">
        <div class="flex-shrink-0">
          <img v-if="post.boardIconUrl" :src="getOptimizedBoardIconUrl(post.boardIconUrl)" alt="Board Icon"
            class="h-7 w-7 sm:h-8 sm:w-8 rounded-full" loading="lazy" @error="handleImageError($event)" />
          <div v-else
            class="h-7 w-7 sm:h-8 sm:w-8 rounded-full bg-indigo-100 dark:bg-indigo-900 flex items-center justify-center">
            <span class="text-[10px] sm:text-xs font-bold text-indigo-600 dark:text-indigo-300">{{
              post.boardName?.substring(0, 1)
            }}</span>
          </div>
        </div>
        <div class="min-w-0">
          <h3 class="text-xs sm:text-sm font-medium text-gray-900 dark:text-white truncate">
            <router-link :to="`/board/${post.boardUrl}`" class="hover:underline">
              {{ post.boardName }}
            </router-link>
          </h3>
          <p class="text-[10px] sm:text-xs text-gray-500 dark:text-gray-400">{{ formatDateOnly(post.createdAt) }}</p>
        </div>
      </div>
      <div class="flex items-center space-x-2">
        <BaseButton @click="$emit('subscribe', post)" variant="ghost" size="sm"
          :class="{ 'text-indigo-600 dark:text-indigo-400': post.subscribed, 'text-gray-400 dark:text-gray-500': !post.subscribed }">
          {{ post.subscribed ? $t('common.subscribed') : $t('common.subscribe') }}
        </BaseButton>
      </div>
    </div>

    <!-- Content -->
    <div class="px-4 py-4 sm:px-6 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
      @click="router.push(`/board/${post.boardUrl}/post/${post.postId}`)">
      <h2 class="text-base sm:text-lg font-bold text-gray-900 dark:text-white mb-2">{{ post.title }}</h2>

      <!-- 스포일러면 최상단 이미지/비디오 + 본문 전체 블러 -->
      <div :class="{ 'feed-card-spoiler': isSpoiler }" class="mb-4">
        <!-- First media: video면 비디오, 아니면 이미지 -->
        <div v-if="showFirstVideo" class="feed-card-first-video mb-4 rounded-lg overflow-hidden">
          <iframe
            :src="post.firstMediaUrl"
            title="Video"
            frameborder="0"
            allowfullscreen
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
            class="feed-card-video-iframe"
          />
        </div>
        <div v-else-if="showFirstImageUrl" class="mb-4 rounded-lg overflow-hidden">
          <img :src="getOptimizedPostImageUrl(showFirstImageUrl)" alt="Post"
            class="max-w-full max-h-[300px] sm:max-h-[400px] w-auto h-auto object-contain" loading="lazy"
            @error="handleImageError($event)" />
        </div>

        <!-- Body: 본문 HTML 5줄 또는 summary -->
        <div v-if="bodyHtml" class="feed-card-body prose-feed line-clamp-5 text-xs sm:text-sm text-gray-600 dark:text-gray-300" v-html="bodyHtml" />
        <p v-else class="text-xs sm:text-sm text-gray-600 dark:text-gray-300 line-clamp-5">{{ post.summary }}</p>
      </div>

      <div class="flex items-center text-[11px] sm:text-xs text-gray-500 dark:text-gray-400 space-x-4">
        <div class="flex items-center">
          <User class="h-3 w-3 mr-1" />
          <span>{{ post.authorName }}</span>
        </div>
        <div class="flex items-center">
          <Eye class="h-3 w-3 mr-1" />
          <span>{{ post.viewCount }}</span>
        </div>
      </div>
    </div>

    <!-- Footer Actions -->
    <div
      class="px-3 sm:px-6 py-2.5 sm:py-3 bg-gray-50 dark:bg-gray-900/50 border-t border-gray-200 dark:border-gray-700 flex justify-around text-xs sm:text-sm">
      <BaseButton @click.stop="$emit('like', post)" variant="ghost" size="sm" class="flex-1 justify-center min-w-0"
        :class="{ 'text-indigo-600 dark:text-indigo-400': post.liked, 'text-gray-500 dark:text-gray-400': !post.liked }">
        <ThumbsUp class="h-4 w-4 mr-1.5" :class="{ 'fill-current': post.liked }" />
        <span>{{ post.likeCount }}</span>
      </BaseButton>

      <BaseButton @click.stop="router.push(`/board/${post.boardUrl}/post/${post.postId}#comments`)" variant="ghost"
        size="sm" class="flex-1 justify-center text-gray-500 dark:text-gray-400">
        <MessageSquare class="h-4 w-4 mr-1.5" />
        <span>{{ post.commentCount }}</span>
      </BaseButton>

      <BaseButton @click.stop="$emit('scrap', post)" variant="ghost" size="sm" class="flex-1 justify-center"
        :class="{ 'text-yellow-500': post.scrapped, 'text-gray-500 dark:text-gray-400': !post.scrapped }">
        <Bookmark class="h-4 w-4 mr-1.5" :class="{ 'fill-current': post.scrapped }" />
        <span>{{ $t('common.scrap') }}</span>
      </BaseButton>
    </div>
  </BaseCard>
</template>

<style scoped>
.feed-card-spoiler {
  filter: blur(10px);
  user-select: none;
  pointer-events: none;
  transform: translateZ(0);
  min-height: 4rem; /* 본문이 비어 있어도 블러 영역이 보이도록 */
}
</style>

<style>
/* Feed 본문 HTML: 리스트·비디오 등 (v-html) */
.feed-card-body.prose-feed ul {
  list-style-type: disc;
  padding-left: 1.5em;
  margin: 0.5em 0;
}
.feed-card-body.prose-feed ul ul {
  list-style-type: circle;
}
.feed-card-body.prose-feed ol {
  list-style-type: decimal;
  padding-left: 1.5em;
  margin: 0.5em 0;
}
.feed-card-body.prose-feed ol ol {
  list-style-type: lower-alpha;
}
.feed-card-body.prose-feed li {
  display: list-item;
  margin: 0.25em 0;
}
.feed-card-body.prose-feed :deep(.tiptap-video-wrapper) {
  position: relative;
  padding-bottom: 56.25%;
  height: 0;
  overflow: hidden;
  max-width: 100%;
  margin: 0.5em 0;
}
.feed-card-body.prose-feed :deep(.tiptap-video-wrapper iframe) {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}
/* Feed 첫 비디오(최초 미디어가 비디오일 때) */
.feed-card-first-video {
  position: relative;
  padding-bottom: 56.25%;
  height: 0;
  overflow: hidden;
  max-width: 100%;
}
.feed-card-video-iframe {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}
</style>
