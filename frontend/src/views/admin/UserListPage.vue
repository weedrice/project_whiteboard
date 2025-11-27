<template>
  <div class="space-y-6">
    <div class="flex justify-between items-center">
      <h2 class="text-2xl font-bold text-gray-900">User Management</h2>
      <div class="flex space-x-2">
        <input
          v-model="searchQuery"
          type="text"
          placeholder="Search users..."
          class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block sm:text-sm border-gray-300 rounded-md"
        />
        <BaseButton @click="searchUsers">Search</BaseButton>
      </div>
    </div>

    <div v-if="loading" class="text-center py-8">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else class="bg-white shadow overflow-hidden sm:rounded-lg">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              Name
            </th>
            <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              Email
            </th>
            <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              Role
            </th>
            <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              Status
            </th>
            <th scope="col" class="relative px-6 py-3">
              <span class="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-200">
          <tr v-for="user in users" :key="user.id">
            <td class="px-6 py-4 whitespace-nowrap">
              <div class="flex items-center">
                <div class="flex-shrink-0 h-10 w-10">
                  <img class="h-10 w-10 rounded-full" :src="`https://ui-avatars.com/api/?name=${user.name}&background=random`" alt="" />
                </div>
                <div class="ml-4">
                  <div class="text-sm font-medium text-gray-900">{{ user.name }}</div>
                  <div class="text-sm text-gray-500">ID: {{ user.id }}</div>
                </div>
              </div>
            </td>
            <td class="px-6 py-4 whitespace-nowrap">
              <div class="text-sm text-gray-900">{{ user.email }}</div>
            </td>
            <td class="px-6 py-4 whitespace-nowrap">
              <span class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full" 
                :class="user.role === 'ADMIN' ? 'bg-purple-100 text-purple-800' : 'bg-green-100 text-green-800'">
                {{ user.role }}
              </span>
            </td>
            <td class="px-6 py-4 whitespace-nowrap">
              <span class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full"
                :class="user.status === 'ACTIVE' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'">
                {{ user.status }}
              </span>
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
              <button @click="toggleStatus(user)" class="text-indigo-600 hover:text-indigo-900 mr-4">
                {{ user.status === 'ACTIVE' ? 'Ban' : 'Activate' }}
              </button>
              <button class="text-gray-600 hover:text-gray-900">Edit</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import BaseButton from '@/components/common/BaseButton.vue'

const users = ref([])
const loading = ref(false)
const searchQuery = ref('')

const mockUsers = [
  { id: 1, name: 'Admin User', email: 'admin@example.com', role: 'ADMIN', status: 'ACTIVE' },
  { id: 2, name: 'John Doe', email: 'john@example.com', role: 'USER', status: 'ACTIVE' },
  { id: 3, name: 'Jane Smith', email: 'jane@example.com', role: 'USER', status: 'SANCTIONED' },
  { id: 4, name: 'Bob Wilson', email: 'bob@example.com', role: 'USER', status: 'ACTIVE' },
]

const fetchUsers = async () => {
  loading.value = true
  try {
    // Mock API call
    await new Promise(resolve => setTimeout(resolve, 500))
    users.value = mockUsers
  } finally {
    loading.value = false
  }
}

const searchUsers = () => {
  if (!searchQuery.value) {
    users.value = mockUsers
    return
  }
  users.value = mockUsers.filter(u => 
    u.name.toLowerCase().includes(searchQuery.value.toLowerCase()) || 
    u.email.toLowerCase().includes(searchQuery.value.toLowerCase())
  )
}

const toggleStatus = async (user) => {
  const newStatus = user.status === 'ACTIVE' ? 'SANCTIONED' : 'ACTIVE'
  if (!confirm(`Change status of ${user.name} to ${newStatus}?`)) return

  // Mock API update
  user.status = newStatus
}

onMounted(() => {
  fetchUsers()
})
</script>
