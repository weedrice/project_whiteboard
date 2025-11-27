<script setup>
import { ref, onMounted } from 'vue'
import { userApi } from '@/api/user'
import { User, Mail, Calendar, FileText, CheckCircle, XCircle, Clock, MessageSquare } from 'lucide-vue-next'
import PostList from '@/components/board/PostList.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import ProfileEditor from '@/components/user/ProfileEditor.vue'

const profile = ref(null)
const posts = ref([])
const comments = ref([]) // 추가: 내가 작성한 댓글 목록
const isLoading = ref(true)
const error = ref('')
const isEditModalOpen = ref(false)

function formatDate(dateString) {
  if (!dateString) return 'N/A'
  return new Date(dateString).toLocaleString()
}

onMounted(async () => {
  isLoading.value = true
  try {
    const [profileRes, postsRes, commentsRes] = await Promise.all([ // commentsRes 추가
      userApi.getMyProfile(),
      userApi.getMyPosts(),
      userApi.getMyComments() // 추가: 내가 작성한 댓글 목록 가져오기
    ])

    if (profileRes.data.success) {
      profile.value = profileRes.data.data
    }
    if (postsRes.data.success) {
      posts.value = postsRes.data.data.content
    }
    if (commentsRes.data.success) { // 추가: 댓글 목록 할당
        comments.value = commentsRes.data.data.content
    }
  } catch (err) {
    console.error('Failed to load my page data:', err)
    error.value = 'Failed to load profile information.'
  } finally {
    isLoading.value = false
  }
})
</script>

<template>
  <div class="max-w-4xl mx-auto">
    <div v-if="isLoading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else-if="error" class="text-center py-10 text-red-500">
      {{ error }}
    </div>

    <div v-else>
      <!-- Profile Section -->
      <div class="bg-white shadow overflow-hidden sm:rounded-lg mb-6">
        <div class="px-4 py-5 sm:px-6 flex justify-between items-center">
          <div class="flex items-center">
            <img v-if="profile.profileImageUrl" :src="profile.profileImageUrl" class="h-16 w-16 rounded-full mr-4" alt="Profile" />
            <div v-else class="h-16 w-16 rounded-full bg-indigo-100 flex items-center justify-center text-indigo-600 font-bold text-2xl mr-4">
              {{ profile.displayName?.[0] || 'U' }}
            </div>
            <div>
              <h3 class="text-lg leading-6 font-medium text-gray-900">{{ profile.displayName }}</h3>
              <p class="mt-1 max-w-2xl text-sm text-gray-500">Personal details and activity.</p>
            </div>
          </div>
          <BaseButton @click="isEditModalOpen = true">Edit Profile</BaseButton>
        </div>
        <div class="border-t border-gray-200 px-4 py-5 sm:p-0">
          <dl class="sm:divide-y sm:divide-gray-200">
            <div class="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
              <dt class="text-sm font-medium text-gray-500 flex items-center">
                <User class="h-4 w-4 mr-2" />
                Display Name
              </dt>
              <dd class="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">{{ profile.displayName }}</dd>
            </div>
            <div class="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
              <dt class="text-sm font-medium text-gray-500 flex items-center">
                <Mail class="h-4 w-4 mr-2" />
                Email
              </dt>
              <dd class="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">
                {{ profile.email }}
                <span v-if="profile.isEmailVerified" class="ml-2 inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
                  <CheckCircle class="h-3 w-3 mr-1" /> Verified
                </span>
                <span v-else class="ml-2 inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-800">
                  <XCircle class="h-3 w-3 mr-1" /> Not Verified
                </span>
              </dd>
            </div>
            <div class="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
              <dt class="text-sm font-medium text-gray-500 flex items-center">
                <Calendar class="h-4 w-4 mr-2" />
                Joined
              </dt>
              <dd class="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">{{ formatDate(profile.createdAt) }}</dd>
            </div>
            <div class="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
              <dt class="text-sm font-medium text-gray-500 flex items-center">
                <Clock class="h-4 w-4 mr-2" />
                Last Login
              </dt>
              <dd class="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">{{ formatDate(profile.lastLoginAt) }}</dd>
            </div>
          </dl>
        </div>
      </div>

      <!-- My Posts Section -->
      <div class="bg-white shadow overflow-hidden sm:rounded-lg mb-6">
        <div class="px-4 py-5 sm:px-6 border-b border-gray-200 flex items-center">
          <FileText class="h-5 w-5 text-gray-500 mr-2" />
          <h3 class="text-lg leading-6 font-medium text-gray-900">My Posts</h3>
        </div>
        
        <div v-if="posts.length > 0">
          <PostList :posts="posts" />
        </div>
        <div v-else class="text-center py-10 text-gray-500">
          You haven't posted anything yet.
        </div>
      </div>

      <!-- My Comments Section -->
      <div class="bg-white shadow overflow-hidden sm:rounded-lg">
        <div class="px-4 py-5 sm:px-6 border-b border-gray-200 flex items-center">
          <MessageSquare class="h-5 w-5 text-gray-500 mr-2" />
          <h3 class="text-lg leading-6 font-medium text-gray-900">My Comments</h3>
        </div>
        
        <div v-if="comments.length > 0">
          <ul role="list" class="divide-y divide-gray-200">
            <li v-for="comment in comments" :key="comment.commentId" class="px-4 py-4 sm:px-6 hover:bg-gray-50">
              <router-link :to="`/board/${comment.post.boardUrl}/post/${comment.post.postId}`" class="block">
                <div class="flex items-center justify-between">
                  <p class="text-sm font-medium text-indigo-600 truncate">{{ comment.post.title }}</p>
                  <p class="ml-2 flex-shrink-0 font-normal text-gray-500 text-xs">{{ formatDate(comment.createdAt) }}</p>
                </div>
                <div class="mt-1 sm:flex sm:justify-between">
                  <p class="text-sm text-gray-900 line-clamp-2">{{ comment.content }}</p>
                </div>
              </router-link>
            </li>
          </ul>
        </div>
        <div v-else class="text-center py-10 text-gray-500">
          You haven't commented on anything yet.
        </div>
      </div>

      <BaseModal :isOpen="isEditModalOpen" title="Edit Profile" @close="isEditModalOpen = false">
        <ProfileEditor @close="isEditModalOpen = false" />
      </BaseModal>
    </div> <!-- Closing div for v-else -->
  </div> <!-- Closing div for max-w-4xl mx-auto -->
</template>
