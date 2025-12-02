<template>
  <div class="space-y-6">
    <div class="flex justify-between items-center">
      <h2 class="text-2xl font-bold text-gray-900">{{ $t('user.blockList.title') }}</h2>
    </div>

    <div v-if="loading" class="text-center py-8">
      <p class="text-gray-500">{{ $t('common.loading') }}</p>
    </div>

    <div v-else-if="blockedUsers.length === 0" class="text-center py-8 bg-gray-50 rounded-lg">
      <p class="text-gray-500">{{ $t('user.blockList.empty') }}</p>
    </div>

    <div v-else class="bg-white shadow overflow-hidden sm:rounded-md">
      <ul role="list" class="divide-y divide-gray-200">
        <li v-for="user in blockedUsers" :key="user.id" class="px-4 py-4 sm:px-6 flex items-center justify-between">
          <div class="flex items-center">
            <div class="flex-shrink-0 h-10 w-10 rounded-full bg-gray-200 flex items-center justify-center">
              <span class="text-gray-500 font-medium">{{ user.nickname.charAt(0).toUpperCase() }}</span>
            </div>
            <div class="ml-4">
              <p class="text-sm font-medium text-gray-900">{{ user.nickname }}</p>
              <p class="text-sm text-gray-500">{{ user.email }}</p>
            </div>
          </div>
          <div>
            <BlockButton :userId="user.id" :initialBlocked="true" @block-change="refreshList" />
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { userApi } from '@/api/user'
import BlockButton from '@/components/user/BlockButton.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const blockedUsers = ref([])
const loading = ref(false)

const fetchBlockedUsers = async () => {
  loading.value = true
  try {
    const { data } = await userApi.getBlockedUsers()
    if (data.success) {
      blockedUsers.value = data.data
    }
  } catch (error) {
    console.error('Failed to fetch blocked users:', error)
  } finally {
    loading.value = false
  }
}

const refreshList = () => {
  fetchBlockedUsers()
}

onMounted(() => {
  fetchBlockedUsers()
})
</script>
