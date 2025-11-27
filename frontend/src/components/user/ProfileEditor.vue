<template>
  <div class="bg-white shadow rounded-lg p-6">
    <h3 class="text-lg font-medium leading-6 text-gray-900 mb-4">Edit Profile</h3>
    <form @submit.prevent="updateProfile" class="space-y-4">
      <BaseInput
        label="Nickname"
        v-model="form.nickname"
        :error="errors.nickname"
        placeholder="Enter your nickname"
      />
      
      <div class="flex justify-end">
        <BaseButton type="submit" :disabled="loading">
          {{ loading ? 'Saving...' : 'Save Changes' }}
        </BaseButton>
      </div>
    </form>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useAuthStore } from '@/stores/auth'
import BaseInput from '@/components/common/BaseInput.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import { userApi } from '@/api/user'

const authStore = useAuthStore()
const loading = ref(false)
const errors = reactive({})

const form = reactive({
  nickname: authStore.user?.nickname || ''
})

const updateProfile = async () => {
  loading.value = true
  errors.nickname = ''
  
  try {
    const { data } = await userApi.updateMyProfile({
      nickname: form.nickname
    })
    
    // Update store with new user data
    if (data.success) {
      await authStore.fetchUser()
      alert('Profile updated successfully')
    }
  } catch (error) {
    console.error('Failed to update profile:', error)
    if (error.response?.data?.message) {
      errors.nickname = error.response.data.message
    } else {
      alert('Failed to update profile')
    }
  } finally {
    loading.value = false
  }
}
</script>
