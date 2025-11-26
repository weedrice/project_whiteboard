<template>
  <div class="space-y-6">
    <div class="flex justify-between items-center">
      <h2 class="text-2xl font-bold text-gray-900">IP Block Management</h2>
      <div class="flex space-x-2">
        <input
          v-model="newIp"
          type="text"
          placeholder="Enter IP address"
          class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block sm:text-sm border-gray-300 rounded-md"
        />
        <BaseButton @click="blockIp" :disabled="!newIp || loading">Block IP</BaseButton>
      </div>
    </div>

    <div v-if="loading" class="text-center py-8">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else-if="blockedIps.length === 0" class="text-center py-8 bg-white shadow rounded-lg">
      <p class="text-gray-500">No blocked IPs.</p>
    </div>

    <div v-else class="bg-white shadow overflow-hidden sm:rounded-md">
      <ul role="list" class="divide-y divide-gray-200">
        <li v-for="ip in blockedIps" :key="ip.id" class="px-4 py-4 sm:px-6 flex justify-between items-center">
          <div>
            <p class="text-sm font-medium text-gray-900">{{ ip.address }}</p>
            <p class="text-xs text-gray-500">Blocked on: {{ new Date(ip.createdAt).toLocaleDateString() }}</p>
          </div>
          <BaseButton size="sm" variant="danger" @click="unblockIp(ip.id)">Unblock</BaseButton>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import BaseButton from '@/components/common/BaseButton.vue'
import axios from '@/api'

const blockedIps = ref([])
const loading = ref(false)
const newIp = ref('')

// Mock data
const mockIps = [
  { id: 1, address: '192.168.1.100', createdAt: '2023-11-01' },
  { id: 2, address: '10.0.0.55', createdAt: '2023-11-15' }
]

const fetchBlockedIps = async () => {
  loading.value = true
  try {
    // const { data } = await axios.get('/api/admin/ip-blocks')
    // if (data.success) {
    //   blockedIps.value = data.data
    // }
    
    await new Promise(resolve => setTimeout(resolve, 500))
    blockedIps.value = mockIps
  } catch (error) {
    console.error('Failed to fetch blocked IPs:', error)
  } finally {
    loading.value = false
  }
}

const blockIp = async () => {
  if (!newIp.value) return
  
  loading.value = true
  try {
    // await axios.post('/api/admin/ip-blocks', { address: newIp.value })
    
    // Mock add
    blockedIps.value.push({
      id: Date.now(),
      address: newIp.value,
      createdAt: new Date().toISOString()
    })
    newIp.value = ''
  } catch (error) {
    console.error('Failed to block IP:', error)
    alert('Failed to block IP.')
  } finally {
    loading.value = false
  }
}

const unblockIp = async (id) => {
  if (!confirm('Unblock this IP?')) return

  loading.value = true
  try {
    // await axios.delete(`/api/admin/ip-blocks/${id}`)
    
    // Mock remove
    blockedIps.value = blockedIps.value.filter(ip => ip.id !== id)
  } catch (error) {
    console.error('Failed to unblock IP:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchBlockedIps()
})
</script>
