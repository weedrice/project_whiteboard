<template>
  <div class="space-y-6">
    <div class="flex justify-between items-center">
      <h2 class="text-2xl font-bold text-gray-900">Report Management</h2>
    </div>

    <div v-if="loading" class="text-center py-8">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else-if="reports.length === 0" class="text-center py-8 bg-white shadow rounded-lg">
      <p class="text-gray-500">No pending reports.</p>
    </div>

    <div v-else class="bg-white shadow overflow-hidden sm:rounded-md">
      <ul role="list" class="divide-y divide-gray-200">
        <li v-for="report in reports" :key="report.id" class="px-4 py-4 sm:px-6">
          <div class="flex items-center justify-between">
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium text-indigo-600 truncate">
                {{ report.type }} #{{ report.targetId }}
              </p>
              <p class="mt-1 text-sm text-gray-500">
                Reported by: {{ report.reporterName }}
              </p>
              <p class="mt-1 text-sm text-gray-900">
                Reason: {{ report.reason }}
              </p>
              <p class="mt-1 text-xs text-gray-400">
                {{ new Date(report.createdAt).toLocaleString() }}
              </p>
            </div>
            <div class="ml-4 flex-shrink-0 flex space-x-2">
              <BaseButton size="sm" @click="openSanctionModal(report.targetUser)">
                Sanction User
              </BaseButton>
              <BaseButton size="sm" variant="secondary" @click="dismissReport(report.id)">
                Dismiss
              </BaseButton>
            </div>
          </div>
        </li>
      </ul>
    </div>

    <SanctionModal
      :isOpen="isModalOpen"
      :user="selectedUser"
      @close="isModalOpen = false"
      @sanctioned="refreshList"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import BaseButton from '@/components/common/BaseButton.vue'
import SanctionModal from '@/components/admin/SanctionModal.vue'
import axios from '@/api'

const reports = ref([])
const loading = ref(false)
const isModalOpen = ref(false)
const selectedUser = ref(null)

// Mock data
const mockReports = [
  {
    id: 1,
    type: 'POST',
    targetId: 101,
    reporterName: 'UserA',
    reason: 'Spam content',
    createdAt: '2023-11-26T10:00:00',
    targetUser: { userId: 1, nickname: 'Spammer1', email: 'spam1@example.com' }
  },
  {
    id: 2,
    type: 'COMMENT',
    targetId: 505,
    reporterName: 'UserB',
    reason: 'Abusive language',
    createdAt: '2023-11-26T11:30:00',
    targetUser: { userId: 2, nickname: 'Troll2', email: 'troll2@example.com' }
  }
]

const fetchReports = async () => {
  loading.value = true
  try {
    // const { data } = await axios.get('/admin/reports')
    // if (data.success) {
    //   reports.value = data.data
    // }
    
    await new Promise(resolve => setTimeout(resolve, 500))
    reports.value = mockReports
  } catch (error) {
    console.error('Failed to fetch reports:', error)
  } finally {
    loading.value = false
  }
}

const openSanctionModal = (user) => {
  selectedUser.value = user
  isModalOpen.value = true
}

const dismissReport = async (id) => {
  if (!confirm('Dismiss this report?')) return
  
  // Call API to dismiss
  reports.value = reports.value.filter(r => r.id !== id)
}

const refreshList = () => {
  fetchReports()
}

onMounted(() => {
  fetchReports()
})
</script>
