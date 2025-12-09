<script setup>
import { ref, onMounted, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { postApi } from '@/api/post'
import { useAuthStore } from '@/stores/auth'
import { User, Clock, ThumbsUp, MessageSquare, Eye, ArrowLeft, MoreHorizontal, Bookmark } from 'lucide-vue-next'
import CommentList from '@/components/comment/CommentList.vue'
import PostTags from '@/components/tag/PostTags.vue'
import UserMenu from '@/components/common/UserMenu.vue'

import { useI18n } from 'vue-i18n'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()

const post = ref(null)
const isLoading = ref(true)
const error = ref('')

const isAuthor = computed(() => {
  return authStore.user && post.value && authStore.user.userId === post.value.author.userId
})

const isAdmin = computed(() => authStore.isAdmin)

function formatDate(dateString) {
  return new Date(dateString).toLocaleString()
}

const isBlurred = ref(false)
const blurTimer = ref(null)
const timeLeft = ref(5)

const titleRef = ref(null)

async function fetchPost() {
  isLoading.value = true
  error.value = ''
  try {
    const { data } = await postApi.getPost(route.params.postId, { redirectOnError: true })
    if (data.success) {
      post.value = data.data
      // Ensure numeric counts
      post.value.likeCount = post.value.likeCount || 0
      post.value.commentCount = post.value.commentCount || 0
      post.value.viewCount = post.value.viewCount || 0

      // Spoiler Logic
      if (post.value.isSpoiler) {
        isBlurred.value = true
        timeLeft.value = 5
        startBlurTimer()
      }

      // Scroll to title
      nextTick(() => {
        window.scrollTo(0, 0)
        if (route.hash) {
            const element = document.querySelector(route.hash)
            if (element) {
                element.scrollIntoView({ behavior: 'smooth' })
            }
        } else if (titleRef.value) {
            titleRef.value.scrollIntoView({ behavior: 'smooth' })
        }
      })
    }
  } catch (err) {
    logger.error('Failed to load post:', err)
    error.value = t('board.postDetail.loadFailed')
  } finally {
    isLoading.value = false
  }
}


async function handleDelete() {
  if (!confirm(t('common.messages.confirmDelete'))) return

  try {
    const { data } = await postApi.deletePost(route.params.postId)
    if (data.success) {
      router.push(`/board/${post.value.board.boardUrl}`)
    }
  } catch (err) {
    logger.error('Failed to delete post:', err)
    alert(t('board.postDetail.deleteFailed'))
  }
}

async function handleLike() {
  if (!authStore.isAuthenticated) return
  try {
    if (post.value.isLiked) {
      const { data } = await postApi.unlikePost(route.params.postId)
      if (data.success) {
        post.value.isLiked = false
        post.value.likeCount = data.data
      }
    } else {
      const { data } = await postApi.likePost(route.params.postId)
      if (data.success) {
        post.value.isLiked = true
        post.value.likeCount = data.data
      }
    }
  } catch (err) {
    logger.error(t('board.postDetail.likeFailed'), err)
  }
}

async function handleScrap() {
  if (!authStore.isAuthenticated) return
  try {
    if (post.value.isScrapped) {
      const { data } = await postApi.unscrapPost(route.params.postId)
      if (data.success) {
        post.value.isScrapped = false
      }
    } else {
      const { data } = await postApi.scrapPost(route.params.postId)
      if (data.success) {
        post.value.isScrapped = true
      }
    }
  } catch (err) {
    logger.error(t('board.postDetail.scrapFailed'), err)
  }
}

function startBlurTimer() {
  blurTimer.value = setInterval(() => {
    timeLeft.value--
    if (timeLeft.value <= 0) {
      revealSpoiler()
    }
  }, 1000)
}

function revealSpoiler() {
  isBlurred.value = false
  if (blurTimer.value) {
    clearInterval(blurTimer.value)
    blurTimer.value = null
  }
}

onMounted(fetchPost)

watch(() => route.params.postId, (newId) => {
    if (newId) fetchPost()
})

watch(() => route.hash, (newHash) => {
    if (newHash) {
        nextTick(() => {
            const element = document.querySelector(newHash)
            if (element) {
                element.scrollIntoView({ behavior: 'smooth' })
            }
        })
    }
})
</script>

<template>
  <div class="w-full bg-white shadow overflow-hidden sm:rounded-lg">
    <div v-if="isLoading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else-if="error" class="text-center py-10 text-red-500">
      {{ error }}
      <div class="mt-4">
        <button @click="router.back()" class="text-indigo-600 hover:text-indigo-500">
          {{ $t('common.back') }}
        </button>
      </div>
    </div>

    <div v-else-if="post">
      <!-- Header -->
      <div class="px-4 py-5 sm:px-6 border-b border-gray-200">
        <div class="flex items-center justify-between">
          <button 
            @click="router.push(`/board/${post.board.boardUrl}`)" 
            class="inline-flex items-center text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft class="h-4 w-4 mr-1" />
            {{ $t('board.postDetail.toList') }}
          </button>
          
          <div class="flex space-x-2">
             <router-link 
              v-if="isAuthor"
              :to="`/board/${post.board.boardUrl}/post/${post.postId}/edit`"
              class="inline-flex items-center px-3 py-1.5 border border-gray-300 shadow-sm text-xs font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 cursor-pointer"
            >
              {{ $t('common.edit') }}
            </router-link>
            <button 
              v-if="isAuthor || isAdmin"
              @click="handleDelete"
              class="inline-flex items-center px-3 py-1.5 border border-transparent shadow-sm text-xs font-medium rounded-md text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500 cursor-pointer"
            >
              {{ $t('common.delete') }}
            </button>
          </div>
        </div>
        
        <div class="mt-4">
            <h1 class="text-2xl font-bold text-gray-900" ref="titleRef">{{ post.title }}</h1>
            <div class="mt-2 flex items-center text-sm text-gray-500 space-x-4">
                <div class="flex items-center">
                    <User class="h-4 w-4 mr-1" />
                    <UserMenu :user-id="post.author.userId" :display-name="post.author.displayName" />
                </div>
                <div class="flex items-center">
                    <Clock class="h-4 w-4 mr-1" />
                    {{ formatDate(post.createdAt) }}
                </div>
                <div class="flex items-center">
                    <Eye class="h-4 w-4 mr-1" />
                    {{ post.viewCount }}
                </div>
                <div class="flex items-center">
                    <MessageSquare class="h-4 w-4 mr-1" />
                    {{ post.commentCount }}
                </div>
            </div>
        </div>
      </div>

      <!-- Images -->
      <div v-if="post.imageUrls && post.imageUrls.length > 0" class="px-4 py-5 sm:px-6">
        <div class="space-y-4">
          <img 
            v-for="(url, index) in post.imageUrls" 
            :key="index" 
            :src="url" 
            class="w-full h-auto rounded-lg shadow-sm"
            alt="Post Image"
          />
        </div>
      </div>

      <!-- Content -->
      <div class="px-4 py-5 sm:p-6 min-h-[200px] prose max-w-none relative">
        <div 
          v-html="post.contents" 
          class="ql-editor transition-all duration-500"
          :class="{ 'blur-md select-none': isBlurred }"
        ></div>
        
        <!-- Spoiler Overlay -->
        <div 
          v-if="isBlurred" 
          class="absolute inset-0 flex flex-col items-center justify-center z-10 bg-white/50"
        >
          <div class="bg-white p-6 rounded-lg shadow-lg text-center border border-gray-200">
            <h3 class="text-lg font-bold text-gray-900 mb-2">{{ $t('board.postDetail.spoilerWarning') }}</h3>
            <p class="text-gray-600 mb-4">{{ $t('board.postDetail.spoilerTimer', { time: timeLeft }) }}</p>
            <button 
              @click="revealSpoiler"
              class="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
            >
              {{ $t('board.postDetail.revealSpoiler') }}
            </button>
          </div>
        </div>
      </div>

      <!-- Tags -->
      <div v-if="post.tags && post.tags.length > 0" class="px-4 py-4 sm:px-6 border-t border-gray-200 bg-gray-50">
        <PostTags :modelValue="post.tags" :readOnly="true" />
      </div>

      <!-- Stats & Actions -->
      <div class="px-4 py-4 sm:px-6 border-t border-gray-200 bg-white flex items-center justify-center space-x-8">
          <button 
            @click="handleLike" 
            :disabled="!authStore.isAuthenticated"
            class="flex flex-col items-center group cursor-pointer"
            :class="{'text-indigo-600': post.isLiked, 'text-gray-500': !post.isLiked, 'opacity-50 cursor-not-allowed': !authStore.isAuthenticated}"
          >
              <div class="p-2 rounded-full group-hover:bg-indigo-50 transition-colors">
                  <ThumbsUp class="h-6 w-6" :class="{'fill-current': post.isLiked}" />
              </div>
              <span class="text-sm font-medium mt-1">{{ post.likeCount }}</span>
          </button>
          
          <button 
            @click="handleScrap"
            :disabled="!authStore.isAuthenticated"
            class="flex flex-col items-center group cursor-pointer"
            :class="{'text-yellow-500': post.isScrapped, 'text-gray-500': !post.isScrapped, 'opacity-50 cursor-not-allowed': !authStore.isAuthenticated}"
          >
              <div class="p-2 rounded-full group-hover:bg-yellow-50 transition-colors">
                  <Bookmark class="h-6 w-6" :class="{'fill-current': post.isScrapped}" />
              </div>
              <span class="text-sm font-medium mt-1">{{ $t('common.scrap') }}</span>
          </button>
      </div>

      <!-- Comments Section -->
      <div class="border-t border-gray-200 mt-8 p-4">
        <CommentList :postId="post.postId" :boardUrl="post.board.boardUrl" />
      </div>
    </div>
  </div>
</template>
