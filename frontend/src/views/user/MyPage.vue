<script setup>
import { ref, onMounted } from 'vue'
import { userApi } from '@/api/user'
import { User, Mail, Calendar, FileText } from 'lucide-vue-next'
import PostList from '@/components/board/PostList.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import ProfileEditor from '@/components/user/ProfileEditor.vue'

const profile = ref(null)
const posts = ref([])
const isLoading = ref(true)
const error = ref('')
const isEditModalOpen = ref(false)

function formatDate(dateString) {
  return new Date(dateString).toLocaleDateString()
}

onMounted(async () => {
  isLoading.value = true
  try {
    const [profileRes, postsRes] = await Promise.all([
      userApi.getMyProfile(),
      userApi.getMyPosts()
    ])

    if (profileRes.data.success) {
      profile.value = profileRes.data.data
    }
    if (postsRes.data.success) {
      posts.value = postsRes.data.data.content
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
          <div>
            <h3 class="text-lg leading-6 font-medium text-gray-900">My Profile</h3>
            <p class="mt-1 max-w-2xl text-sm text-gray-500">Personal details and activity.</p>
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
              <dd class="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">{{ profile.email }}</dd>
            </div>
            <div class="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
              <dt class="text-sm font-medium text-gray-500 flex items-center">
                <Calendar class="h-4 w-4 mr-2" />
                Joined
              </dt>
              <dd class="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">{{ formatDate(profile.createdAt) }}</dd>
            </div>
          </dl>
        </div>
      </div>

      <!-- My Posts Section -->
      <div class="bg-white shadow overflow-hidden sm:rounded-lg">
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
      <BaseModal :isOpen="isEditModalOpen" title="Edit Profile" @close="isEditModalOpen = false">
        <ProfileEditor @close="isEditModalOpen = false" />
      </BaseModal>
    </div> <!-- Closing div for v-else -->
  </div> <!-- Closing div for max-w-4xl mx-auto -->
</template>
