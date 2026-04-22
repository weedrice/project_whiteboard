<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Bookmark, Eye, MessageSquare, ThumbsUp, User } from 'lucide-vue-next'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseCard from '@/components/common/ui/BaseCard.vue'
import type { FeedPost } from '@/types'
import { formatDateOnly } from '@/utils/date'
import { getOptimizedBoardIconUrl, getOptimizedPostImageUrl, handleImageError } from '@/utils/image'
import logger from '@/utils/logger'
import { buildPostDetailPath, getFeedBodyHtml, getFeedMediaPreview, isFeedSpoiler } from '@/utils/feedPreview'

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
  logger.warn('FeedCard: Invalid post data', props.post)
}

const bodyHtml = computed(() => getFeedBodyHtml(props.post))
const mediaPreview = computed(() => getFeedMediaPreview(props.post))
const showFirstVideo = computed(() => mediaPreview.value.showFirstVideo)
const showFirstImageUrl = computed(() => mediaPreview.value.imageUrl)
const isSpoiler = computed(() => isFeedSpoiler(props.post))
</script>

<template>
  <BaseCard v-if="post && post.postId" noPadding class="mb-6">
    <div
      class="flex items-center justify-between border-b border-gray-200 px-3 py-3 sm:px-6 sm:py-4 dark:border-gray-700"
    >
      <div class="flex items-center space-x-2 sm:space-x-3">
        <div class="flex-shrink-0">
          <img
            v-if="post.boardIconUrl"
            :src="getOptimizedBoardIconUrl(post.boardIconUrl)"
            alt="Board Icon"
            class="h-7 w-7 rounded-full sm:h-8 sm:w-8"
            loading="lazy"
            @error="handleImageError($event)"
          />
          <div
            v-else
            class="flex h-7 w-7 items-center justify-center rounded-full bg-indigo-100 sm:h-8 sm:w-8 dark:bg-indigo-900"
          >
            <span class="text-[10px] font-bold text-indigo-600 sm:text-xs dark:text-indigo-300">
              {{ post.boardName?.substring(0, 1) }}
            </span>
          </div>
        </div>
        <div class="min-w-0">
          <h3 class="truncate text-xs font-medium text-gray-900 sm:text-sm dark:text-white">
            <router-link :to="`/board/${post.boardUrl}`" class="hover:underline">
              {{ post.boardName }}
            </router-link>
          </h3>
          <p class="text-[10px] text-gray-500 sm:text-xs dark:text-gray-400">{{ formatDateOnly(post.createdAt) }}</p>
        </div>
      </div>
      <div class="flex items-center space-x-2">
        <BaseButton
          @click="$emit('subscribe', post)"
          variant="ghost"
          size="sm"
          :class="{ 'text-indigo-600 dark:text-indigo-400': post.subscribed, 'text-gray-400 dark:text-gray-500': !post.subscribed }"
        >
          {{ post.subscribed ? $t('common.subscribed') : $t('common.subscribe') }}
        </BaseButton>
      </div>
    </div>

    <router-link
      :to="buildPostDetailPath(post.boardUrl, post.postId)"
      class="block px-4 py-4 transition-colors hover:bg-gray-50 sm:px-6 dark:hover:bg-gray-700/50"
    >
      <h2 class="mb-2 text-base font-bold text-gray-900 sm:text-lg dark:text-white">{{ post.title }}</h2>

      <div :class="{ 'feed-card-spoiler': isSpoiler }" class="mb-4">
        <div v-if="showFirstVideo" class="feed-card-first-video mb-4 overflow-hidden rounded-lg">
          <iframe
            :src="post.firstMediaUrl"
            title="Video"
            frameborder="0"
            allowfullscreen
            loading="lazy"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
            class="feed-card-video-iframe"
          />
        </div>
        <div v-else-if="showFirstImageUrl" class="mb-4 overflow-hidden rounded-lg">
          <img
            :src="getOptimizedPostImageUrl(showFirstImageUrl)"
            alt="Post"
            class="h-auto max-h-[300px] w-auto max-w-full object-contain sm:max-h-[400px]"
            loading="lazy"
            @error="handleImageError($event)"
          />
        </div>

        <div
          v-if="bodyHtml"
          class="feed-card-body prose-feed line-clamp-5 text-xs text-gray-600 sm:text-sm dark:text-gray-300"
          v-html="bodyHtml"
        />
        <p v-else class="line-clamp-5 text-xs text-gray-600 sm:text-sm dark:text-gray-300">{{ post.summary }}</p>
      </div>

      <div class="flex items-center space-x-4 text-[11px] text-gray-500 sm:text-xs dark:text-gray-400">
        <div class="flex items-center">
          <User class="mr-1 h-3 w-3" />
          <span>{{ post.authorName }}</span>
        </div>
        <div class="flex items-center">
          <Eye class="mr-1 h-3 w-3" />
          <span>{{ post.viewCount }}</span>
        </div>
      </div>
    </router-link>

    <div
      class="flex justify-around border-t border-gray-200 bg-gray-50 px-3 py-2.5 text-xs sm:px-6 sm:py-3 sm:text-sm dark:border-gray-700 dark:bg-gray-900/50"
    >
      <BaseButton
        @click.stop="$emit('like', post)"
        variant="ghost"
        size="sm"
        class="min-w-0 flex-1 justify-center"
        :class="{ 'text-indigo-600 dark:text-indigo-400': post.liked, 'text-gray-500 dark:text-gray-400': !post.liked }"
      >
        <ThumbsUp class="mr-1.5 h-4 w-4" :class="{ 'fill-current': post.liked }" />
        <span>{{ post.likeCount }}</span>
      </BaseButton>

      <BaseButton
        @click.stop="router.push(buildPostDetailPath(post.boardUrl, post.postId, '#comments'))"
        variant="ghost"
        size="sm"
        class="flex-1 justify-center text-gray-500 dark:text-gray-400"
      >
        <MessageSquare class="mr-1.5 h-4 w-4" />
        <span>{{ post.commentCount }}</span>
      </BaseButton>

      <BaseButton
        @click.stop="$emit('scrap', post)"
        variant="ghost"
        size="sm"
        class="flex-1 justify-center"
        :class="{ 'text-yellow-500': post.scrapped, 'text-gray-500 dark:text-gray-400': !post.scrapped }"
      >
        <Bookmark class="mr-1.5 h-4 w-4" :class="{ 'fill-current': post.scrapped }" />
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
  min-height: 4rem;
}
</style>

<style>
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
