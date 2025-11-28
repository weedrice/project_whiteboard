<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { User, LogOut, Settings, CreditCard, FileText, Clock, AlertTriangle, PlusSquare, ChevronDown } from 'lucide-vue-next'
import axios from '@/api'

const router = useRouter()
const authStore = useAuthStore()

const props = defineProps({
  isOpen: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['toggle'])

const points = ref(0)
const dropdownRef = ref(null)

const toggleDropdown = () => {
  emit('toggle')
}

const fetchPoints = async () => {
  try {
    const { data } = await axios.get('/points/me')
    if (data.success) {
      points.value = data.data.currentPoint
    }
  } catch (error) {
    console.error('포인트 조회 실패:', error)
  }
}

const handleLogout = async () => {
  await authStore.logout()
  router.push('/')
}

onMounted(() => {
  fetchPoints()
})
</script>

<template>
  <div class="relative" ref="dropdownRef">
    <button
      @click.stop="toggleDropdown"
      class="flex items-center space-x-2 text-sm text-gray-700 hover:text-indigo-600 focus:outline-none"
    >
      <div class="h-8 w-8 rounded-full bg-indigo-100 flex items-center justify-center text-indigo-600 font-bold">
        {{ authStore.user?.displayName?.[0] || authStore.user?.loginId?.[0] || 'U' }}
      </div>
      <span class="hidden md:block font-medium">{{ authStore.user?.displayName || authStore.user?.loginId }}</span>
      <ChevronDown class="h-4 w-4 text-gray-500" />
    </button>

    <div
      v-if="isOpen"
      class="origin-top-right absolute right-0 mt-2 w-64 rounded-md shadow-lg py-1 bg-white ring-1 ring-black ring-opacity-5 focus:outline-none z-50"
    >
      <!-- User Info -->
      <div class="px-4 py-3 border-b border-gray-100">
        <p class="text-sm font-medium text-gray-900 truncate">
          {{ authStore.user?.displayName }}
        </p>
        <p class="text-xs text-gray-500 truncate mb-2">
          {{ authStore.user?.email }}
        </p>
        <div class="flex items-center text-sm text-indigo-600 font-medium">
          <CreditCard class="h-4 w-4 mr-1" />
          {{ points.toLocaleString() }} P
        </div>
      </div>

      <!-- Group 1 -->
      <div class="py-1 border-b border-gray-100">
        <router-link
          to="/mypage"
          class="group flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 hover:text-gray-900"
          @click="emit('toggle')"
        >
          <User class="mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500" />
          마이페이지
        </router-link>
        <router-link
          to="/mypage/settings"
          class="group flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 hover:text-gray-900"
          @click="emit('toggle')"
        >
          <Settings class="mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500" />
          표시 설정
        </router-link>
        <router-link
          to="/mypage/points"
          class="group flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 hover:text-gray-900"
          @click="emit('toggle')"
        >
          <CreditCard class="mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500" />
          포인트 내역
        </router-link>
        <router-link
          to="/mypage/scraps"
          class="group flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 hover:text-gray-900"
          @click="emit('toggle')"
        >
          <FileText class="mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500" />
          스크랩 목록
        </router-link>
      </div>

      <!-- Group 2 -->
      <div class="py-1 border-b border-gray-100">
        <router-link
          to="/search/recent"
          class="group flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 hover:text-gray-900"
          @click="emit('toggle')"
        >
          <Clock class="mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500" />
          최근 읽은 글
        </router-link>
        <router-link
          to="/mypage/reports"
          class="group flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 hover:text-gray-900"
          @click="emit('toggle')"
        >
          <AlertTriangle class="mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500" />
          신고 목록
        </router-link>
        <router-link
          to="/board/create"
          class="group flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 hover:text-gray-900"
          @click="emit('toggle')"
        >
          <PlusSquare class="mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500" />
          게시판 만들기
        </router-link>
      </div>

      <!-- Logout -->
      <div class="py-1">
        <button
          @click="handleLogout"
          class="w-full text-left group flex items-center px-4 py-2 text-sm text-red-600 hover:bg-red-50 hover:text-red-700"
        >
          <LogOut class="mr-3 h-4 w-4 text-red-500 group-hover:text-red-600" />
          로그아웃
        </button>
      </div>
    </div>
  </div>
</template>
