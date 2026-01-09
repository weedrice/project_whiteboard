<script setup>
import { ref, onMounted } from 'vue'
import { userApi } from '@/api/user'
import { User, Mail, Calendar, FileText, CheckCircle, XCircle, Clock, MessageSquare } from 'lucide-vue-next'
import PostList from '@/components/board/PostList.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import ProfileEditor from '@/components/user/ProfileEditor.vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import logger from '@/utils/logger'

const profile = ref(null)

// Posts pagination state
const myPosts = ref([])
const myPostsTotalCount = ref(0)
const myPostsCurrentPage = ref(0)
const myPostsSize = ref(10) // 10 items per page
const myPostsSort = ref('createdAt,desc')

// Comments pagination state
const myComments = ref([])
const myCommentsTotalCount = ref(0)
const myCommentsCurrentPage = ref(0)
const myCommentsSize = ref(10) // 10 items per page

const isLoading = ref(true)
const error = ref('')
const isEditModalOpen = ref(false)

import { formatDate } from '@/utils/date'

async function fetchMyProfile() {
  try {
    const { data } = await userApi.getMyProfile()
    if (data.success) {
      profile.value = data.data
    }
  } catch (err) {
    logger.error('Failed to load my profile:', err)
    error.value = 'Failed to load profile details.'
  }
}

async function fetchMyPosts() {
  try {
    const { data } = await userApi.getMyPosts({
      page: myPostsCurrentPage.value,
      size: myPostsSize.value,
      sort: myPostsSort.value
    })
    if (data.success) {
      myPosts.value = data.data.content
      myPostsTotalCount.value = data.data.totalElements
    }
  } catch (err) {
    logger.error('Failed to load my posts:', err)
    error.value = 'Failed to load my posts.'
  }
}

async function fetchMyComments() {
  try {
    const { data } = await userApi.getMyComments({ page: myCommentsCurrentPage.value, size: myCommentsSize.value })
    if (data.success) {
      myComments.value = data.data.content
      myCommentsTotalCount.value = data.data.totalElements
    }
  } catch (err) {
    logger.error('Failed to load my comments:', err)
    error.value = 'Failed to load my comments.'
  }
}

function handleMyPostsPageChange(page) {
  myPostsCurrentPage.value = page
  fetchMyPosts()
}

function handleMyPostsSortChange(newSort) {
  myPostsSort.value = newSort
  fetchMyPosts()
}

function handleMyCommentsPageChange(page) {
  myCommentsCurrentPage.value = page
  fetchMyComments()
}

onMounted(async () => {
  isLoading.value = true
  await Promise.all([
    fetchMyProfile(),
    fetchMyPosts(),
    fetchMyComments()
  ])
  isLoading.value = false
})
</script>

<template>
  <div>
    <div v-if="isLoading" class="space-y-6">
      <!-- Profile Skeleton -->
      <div class="max-w-[80%] mx-auto bg-white dark:bg-gray-800 shadow rounded-lg p-6">
        <div class="flex items-center mb-6">
          <BaseSkeleton width="4rem" height="4rem" rounded="rounded-full" className="mr-4" />
          <div class="flex-1">
            <BaseSkeleton width="150px" height="24px" className="mb-2" />
            <BaseSkeleton width="200px" height="16px" />
          </div>
        </div>
        <div class="space-y-4">
          <BaseSkeleton v-for="i in 4" :key="i" width="100%" height="40px" />
        </div>
      </div>
      <!-- Posts Skeleton -->
      <div class="max-w-[80%] mx-auto bg-white dark:bg-gray-800 shadow rounded-lg p-6">
        <BaseSkeleton width="120px" height="24px" className="mb-4" />
        <div class="space-y-4">
          <BaseSkeleton v-for="i in 3" :key="i" width="100%" height="60px" />
        </div>
      </div>
    </div>

    <div v-else-if="error" class="text-center py-10 text-red-500 dark:text-red-400">
      {{ error }}
    </div>

    <div v-else>
      <!-- Profile Section -->
      <div class="max-w-[80%] mx-auto">
        <div class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg mb-6 transition-colors duration-200">
          <div class="px-4 py-5 sm:px-6 flex justify-between items-center">
            <div class="flex items-center">
              <img v-if="profile.profileImageUrl" :src="profile.profileImageUrl" class="h-16 w-16 rounded-full mr-4"
                alt="Profile" />
              <div v-else
                class="h-16 w-16 rounded-full bg-indigo-100 dark:bg-indigo-900/50 flex items-center justify-center text-indigo-600 dark:text-indigo-400 font-bold text-2xl mr-4">
                {{ profile.displayName?.[0] || 'U' }}
              </div>
              <div>
                <h3 class="text-lg leading-6 font-medium text-gray-900 dark:text-white">{{ profile.displayName }}</h3>
                <p class="mt-1 max-w-2xl text-sm text-gray-500 dark:text-gray-400">{{ $t('user.profile.personalDetails')
                }}</p>
              </div>
            </div>
            <BaseButton @click="isEditModalOpen = true">{{ $t('user.profile.edit') }}</BaseButton>
          </div>
          <div class="border-t border-gray-200 dark:border-gray-700 px-4 py-5 sm:p-0">
            <dl class="sm:divide-y sm:divide-gray-200 dark:sm:divide-gray-700">
              <div class="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
                <dt class="text-sm font-medium text-gray-500 dark:text-gray-400 flex items-center">
                  <User class="h-4 w-4 mr-2" />
                  {{ $t('user.profile.displayName') }}
                </dt>
                <dd class="mt-1 text-sm text-gray-900 dark:text-gray-100 sm:mt-0 sm:col-span-2">{{ profile.displayName
                }}</dd>
              </div>
              <div class="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
                <dt class="text-sm font-medium text-gray-500 dark:text-gray-400 flex items-center">
                  <Mail class="h-4 w-4 mr-2" />
                  {{ $t('user.profile.email') }}
                </dt>
                <dd class="mt-1 text-sm text-gray-900 dark:text-gray-100 sm:mt-0 sm:col-span-2">
                  {{ profile.email }}
                  <span v-if="profile.isEmailVerified"
                    class="ml-2 inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 dark:bg-green-900/50 text-green-800 dark:text-green-400">
                    <CheckCircle class="h-3 w-3 mr-1" /> {{ $t('user.profile.verified') }}
                  </span>
                  <span v-else
                    class="ml-2 inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-red-100 dark:bg-red-900/50 text-red-800 dark:text-red-400">
                    <XCircle class="h-3 w-3 mr-1" /> {{ $t('user.profile.notVerified') }}
                  </span>
                </dd>
              </div>
              <div class="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
                <dt class="text-sm font-medium text-gray-500 dark:text-gray-400 flex items-center">
                  <Calendar class="h-4 w-4 mr-2" />
                  {{ $t('user.profile.joined') }}
                </dt>
                <dd class="mt-1 text-sm text-gray-900 dark:text-gray-100 sm:mt-0 sm:col-span-2">{{
                  formatDate(profile.createdAt) }}</dd>
              </div>
              <div class="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
                <dt class="text-sm font-medium text-gray-500 dark:text-gray-400 flex items-center">
                  <Clock class="h-4 w-4 mr-2" />
                  {{ $t('user.profile.lastLogin') }}
                </dt>
                <dd class="mt-1 text-sm text-gray-900 dark:text-gray-100 sm:mt-0 sm:col-span-2">{{
                  formatDate(profile.lastLoginAt) }}</dd>
              </div>
            </dl>
          </div>
        </div>
      </div>

      <!-- My Posts Section -->
      <div class="max-w-[80%] mx-auto">
        <div
          class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg mb-6 pb-6 transition-colors duration-200">
          <div class="px-4 py-5 sm:px-6 border-b border-gray-200 dark:border-gray-700 flex items-center">
            <FileText class="h-5 w-5 text-gray-500 dark:text-gray-400 mr-2" />
            <h3 class="text-lg leading-6 font-medium text-gray-900 dark:text-white">{{ $t('user.myPosts') }}</h3>
          </div>

          <div v-if="myPosts.length > 0">
            <PostList :posts="myPosts" :totalCount="myPostsTotalCount" :page="myPostsCurrentPage" :size="myPostsSize"
              :current-sort="myPostsSort" :show-board-name="true" @update:sort="handleMyPostsSortChange" />
            <div class="mt-4 flex justify-center">
              <Pagination :current-page="myPostsCurrentPage" :total-pages="Math.ceil(myPostsTotalCount / myPostsSize)"
                @page-change="handleMyPostsPageChange" />
            </div>
          </div>
          <EmptyState 
            v-else
            :title="$t('common.noData')"
            :icon="FileText"
          />
        </div>

        <!-- My Comments Section -->
        <div class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
          <div class="px-4 py-5 sm:px-6 border-b border-gray-200 dark:border-gray-700 flex items-center">
            <MessageSquare class="h-5 w-5 text-gray-500 dark:text-gray-400 mr-2" />
            <h3 class="text-lg leading-6 font-medium text-gray-900 dark:text-white">{{ $t('user.myComments') }}</h3>
          </div>

          <div v-if="myComments.length > 0">
            <ul role="list" class="divide-y divide-gray-200 dark:divide-gray-700">
              <li v-for="comment in myComments" :key="comment.commentId"
                class="px-4 py-4 sm:px-6 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors duration-200">
                <router-link :to="`/board/${comment.post.boardUrl}/post/${comment.post.postId}`" class="block">
                  <div class="flex items-center justify-between">
                    <div class="flex items-center">
                      <span
                        class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-200 mr-2">
                        {{ comment.post.boardName }}
                      </span>
                      <p class="text-sm font-medium text-indigo-600 dark:text-indigo-400 truncate">
                        {{ comment.post.title }}
                      </p>
                    </div>
                    <p class="ml-2 flex-shrink-0 font-normal text-gray-500 dark:text-gray-400 text-xs">{{
                      formatDate(comment.createdAt) }}</p>
                  </div>
                  <div class="mt-1 sm:flex sm:justify-between">
                    <p class="text-sm text-gray-900 dark:text-gray-300 line-clamp-2">{{ comment.content }}</p>
                  </div>
                </router-link>
              </li>
            </ul>
            <div class="mt-4 flex justify-center pb-6">
              <Pagination :current-page="myCommentsCurrentPage"
                :total-pages="Math.ceil(myCommentsTotalCount / myCommentsSize)"
                @page-change="handleMyCommentsPageChange" />
            </div>
          </div>
          <EmptyState 
            v-else
            :title="$t('common.noData')"
            :icon="MessageSquare"
          />
        </div>
      </div>

      <BaseModal :isOpen="isEditModalOpen" :title="$t('user.profile.edit')" @close="isEditModalOpen = false">
        <ProfileEditor @close="isEditModalOpen = false" />
      </BaseModal>
    </div>
  </div>
</template>
