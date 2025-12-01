<script setup>
import { ref, onMounted } from 'vue'
import { userApi } from '@/api/user'
import { User, Mail, Calendar, FileText, CheckCircle, XCircle, Clock, MessageSquare } from 'lucide-vue-next'
import PostList from '@/components/board/PostList.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import ProfileEditor from '@/components/user/ProfileEditor.vue'
import Pagination from '@/components/common/Pagination.vue'

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

function formatDate(dateString) {
  if (!dateString) return 'N/A'
  return new Date(dateString).toLocaleString()
}

async function fetchMyProfile() {
  try {
    const { data } = await userApi.getMyProfile()
    if (data.success) {
      profile.value = data.data
    }
  } catch (err) {
    console.error('Failed to load my profile:', err)
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
    console.error('Failed to load my posts:', err)
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
    console.error('Failed to load my comments:', err)
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
    <div v-if="isLoading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else-if="error" class="text-center py-10 text-red-500">
      {{ error }}
    </div>

    <div v-else>
      <!-- Profile Section -->
      <div class="max-w-[80%] mx-auto">
        <div class="bg-white shadow overflow-hidden sm:rounded-lg mb-6">
          <div class="px-4 py-5 sm:px-6 flex justify-between items-center">
            <div class="flex items-center">
            <img v-if="profile.profileImageUrl" :src="profile.profileImageUrl" class="h-16 w-16 rounded-full mr-4" alt="Profile" />
            <div v-else class="h-16 w-16 rounded-full bg-indigo-100 flex items-center justify-center text-indigo-600 font-bold text-2xl mr-4">
              {{ profile.displayName?.[0] || 'U' }}
            </div>
            <div>
              <h3 class="text-lg leading-6 font-medium text-gray-900">{{ profile.displayName }}</h3>
              <p class="mt-1 max-w-2xl text-sm text-gray-500">{{ $t('user.profile.personalDetails') }}</p>
            </div>
          </div>
          <BaseButton @click="isEditModalOpen = true">{{ $t('user.profile.edit') }}</BaseButton>
        </div>
        <div class="border-t border-gray-200 px-4 py-5 sm:p-0">
          <dl class="sm:divide-y sm:divide-gray-200">
            <div class="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
              <dt class="text-sm font-medium text-gray-500 flex items-center">
                <User class="h-4 w-4 mr-2" />
                {{ $t('user.profile.displayName') }}
              </dt>
              <dd class="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">{{ profile.displayName }}</dd>
            </div>
            <div class="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
              <dt class="text-sm font-medium text-gray-500 flex items-center">
                <Mail class="h-4 w-4 mr-2" />
                {{ $t('user.profile.email') }}
              </dt>
              <dd class="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">
                {{ profile.email }}
                <span v-if="profile.isEmailVerified" class="ml-2 inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
                  <CheckCircle class="h-3 w-3 mr-1" /> {{ $t('user.profile.verified') }}
                </span>
                <span v-else class="ml-2 inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-800">
                  <XCircle class="h-3 w-3 mr-1" /> {{ $t('user.profile.notVerified') }}
                </span>
              </dd>
            </div>
            <div class="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
              <dt class="text-sm font-medium text-gray-500 flex items-center">
                <Calendar class="h-4 w-4 mr-2" />
                {{ $t('user.profile.joined') }}
              </dt>
              <dd class="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">{{ formatDate(profile.createdAt) }}</dd>
            </div>
            <div class="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
              <dt class="text-sm font-medium text-gray-500 flex items-center">
                <Clock class="h-4 w-4 mr-2" />
                {{ $t('user.profile.lastLogin') }}
              </dt>
              <dd class="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">{{ formatDate(profile.lastLoginAt) }}</dd>
            </div>
          </dl>
        </div>
      </div>
      </div>

      <!-- My Posts Section -->
      <div class="bg-white shadow overflow-hidden sm:rounded-lg mb-6 pb-6">
        <div class="px-4 py-5 sm:px-6 border-b border-gray-200 flex items-center">
          <FileText class="h-5 w-5 text-gray-500 mr-2" />
          <h3 class="text-lg leading-6 font-medium text-gray-900">{{ $t('user.myPosts') }}</h3>
        </div>
        
        <div v-if="myPosts.length > 0">
          <PostList 
            :posts="myPosts"
            :totalCount="myPostsTotalCount"
            :page="myPostsCurrentPage"
            :size="myPostsSize"
            :current-sort="myPostsSort"
            :show-board-name="true"
            @update:sort="handleMyPostsSortChange"
          />
          <div class="mt-4 flex justify-center">
            <Pagination 
              :current-page="myPostsCurrentPage" 
              :total-pages="Math.ceil(myPostsTotalCount / myPostsSize)"
              @page-change="handleMyPostsPageChange" 
            />
          </div>
        </div>
        <div v-else class="text-center py-10 text-gray-500">
          {{ $t('common.noData') }}
        </div>
      </div>

      <!-- My Comments Section -->
      <div class="bg-white shadow overflow-hidden sm:rounded-lg">
        <div class="px-4 py-5 sm:px-6 border-b border-gray-200 flex items-center">
          <MessageSquare class="h-5 w-5 text-gray-500 mr-2" />
          <h3 class="text-lg leading-6 font-medium text-gray-900">{{ $t('user.myComments') }}</h3>
        </div>
        
        <div v-if="myComments.length > 0">
          <ul role="list" class="divide-y divide-gray-200">
            <li v-for="(comment, index) in myComments" :key="comment.commentId" class="px-4 py-4 sm:px-6 hover:bg-gray-50">
              <router-link :to="`/board/${comment.boardUrl}/post/${comment.postId}`" class="block">
                <div class="flex items-center justify-between">
                  <p class="text-sm font-medium text-indigo-600 truncate">
                    <span class="mr-2 text-gray-500 text-xs">{{ myCommentsCurrentPage * myCommentsSize + index + 1 }}.</span>
                    {{ comment.postTitle }}
                  </p>
                  <p class="ml-2 flex-shrink-0 font-normal text-gray-500 text-xs">{{ formatDate(comment.createdAt) }}</p>
                </div>
                <div class="mt-1 sm:flex sm:justify-between">
                  <p class="text-sm text-gray-900 line-clamp-2">{{ comment.content }}</p>
                </div>
              </router-link>
            </li>
          </ul>
          <div class="mt-4 flex justify-center">
            <Pagination 
              :current-page="myCommentsCurrentPage" 
              :total-pages="Math.ceil(myCommentsTotalCount / myCommentsSize)"
              @page-change="handleMyCommentsPageChange" 
            />
          </div>
        </div>
        <div v-else class="text-center py-10 text-gray-500">
          {{ $t('common.noData') }}
        </div>
      </div>

      <BaseModal :isOpen="isEditModalOpen" :title="$t('user.profile.edit')" @close="isEditModalOpen = false">
        <ProfileEditor @close="isEditModalOpen = false" />
      </BaseModal>
    </div>
  </div>
</template>
