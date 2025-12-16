<template>
  <div class="relative inline-block text-left">
    <div>
      <button ref="buttonRef" type="button"
        class="inline-flex justify-center w-full rounded-md text-sm font-medium text-gray-700 dark:text-gray-200 hover:text-indigo-600 dark:hover:text-indigo-400 focus:outline-none"
        :class="{ 'cursor-default hover:text-gray-700 dark:hover:text-gray-200': isSelf }" @click="toggleDropdown">
        {{ displayName }}
      </button>
    </div>

    <Teleport to="body">
      <div v-if="isDropdownOpen" :style="dropdownStyle"
        class="absolute w-56 rounded-md shadow-lg bg-white dark:bg-gray-800 ring-1 ring-black ring-opacity-5 dark:ring-gray-700 focus:outline-none z-50 transition-colors duration-200"
        role="menu" id="user-menu-dropdown" aria-orientation="vertical" aria-labelledby="menu-button">
        <div class="py-1" role="none">
          <button v-if="!isSelf" @click="openMessageModal"
            class="text-gray-700 dark:text-gray-200 block px-4 py-2 text-sm w-full text-left hover:bg-gray-100 dark:hover:bg-gray-700"
            role="menuitem">
            {{ $t('user.menu.sendMessage') }}
          </button>
          <button v-if="!isSelf" @click="openReportModal"
            class="text-gray-700 dark:text-gray-200 block px-4 py-2 text-sm w-full text-left hover:bg-gray-100 dark:hover:bg-gray-700"
            role="menuitem">
            {{ $t('user.menu.report') }}
          </button>
          <button v-if="!isSelf" @click="handleBlockUser"
            class="text-gray-700 dark:text-gray-200 block px-4 py-2 text-sm w-full text-left hover:bg-gray-100 dark:hover:bg-gray-700"
            role="menuitem">
            {{ $t('user.menu.block') }}
          </button>
        </div>
      </div>
    </Teleport>

    <!-- Message Modal -->
    <MessageModal :isOpen="isMessageModalOpen" :userId="userId" :displayName="displayName" @close="closeMessageModal" />

    <!-- Report Modal -->
    <ReportModal :isOpen="isReportModalOpen" :userId="userId" :displayName="displayName" @close="closeReportModal" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, type CSSProperties } from 'vue'
import { userApi } from '@/api/user'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import logger from '@/utils/logger'
import { useToastStore } from '@/stores/toast'
import { useConfirm } from '@/composables/useConfirm'
import MessageModal from '@/components/user/MessageModal.vue'
import ReportModal from '@/components/report/ReportModal.vue'

const { t } = useI18n()
const authStore = useAuthStore()
const toastStore = useToastStore()
const { confirm } = useConfirm()

const props = defineProps<{
  userId: number
  displayName: string
}>()

const isDropdownOpen = ref(false)
const isMessageModalOpen = ref(false)
const isReportModalOpen = ref(false)

const isSelf = computed(() => {
  return authStore.user && authStore.user.userId === props.userId
})

const buttonRef = ref<HTMLButtonElement | null>(null)
const dropdownStyle = ref<CSSProperties>({})

const toggleDropdown = () => {
  if (!authStore.user) return // Disable for guests
  if (isSelf.value) return // Disable dropdown for self

  if (isDropdownOpen.value) {
    closeDropdown()
  } else {
    openDropdown()
  }
}

const openDropdown = () => {
  if (buttonRef.value) {
    const rect = buttonRef.value.getBoundingClientRect()
    dropdownStyle.value = {
      top: `${rect.bottom + window.scrollY + 5}px`,
      left: `${rect.left + window.scrollX}px`
    }
  }
  isDropdownOpen.value = true
}

const closeDropdown = () => {
  isDropdownOpen.value = false
}

const openMessageModal = () => {
  closeDropdown()
  if (isSelf.value) return
  isMessageModalOpen.value = true
}

const closeMessageModal = () => {
  isMessageModalOpen.value = false
}

const openReportModal = () => {
  closeDropdown()
  if (isSelf.value) return
  isReportModalOpen.value = true
}

const closeReportModal = () => {
  isReportModalOpen.value = false
}

const handleBlockUser = async () => {
  closeDropdown()
  if (isSelf.value) return
  const isConfirmed = await confirm(t('user.block.confirm', { name: props.displayName }))
  if (!isConfirmed) {
    return
  }
  try {
    const { data } = await userApi.blockUser(props.userId)
    if (data.success) {
      toastStore.addToast(t('user.block.success', { name: props.displayName }), 'success')
    }
  } catch (error) {
    logger.error('Failed to block user:', error)
    toastStore.addToast(t('user.block.failed'), 'error')
  }
}

// Close dropdown when clicking outside
const handleClickOutside = (event: Event) => {
  if (isDropdownOpen.value) {
    // Check if click is inside the button
    if (buttonRef.value && buttonRef.value.contains(event.target as Node)) {
      return
    }
    // Check if click is inside the dropdown content (we need a ref for it)
    const dropdownEl = document.getElementById('user-menu-dropdown')
    if (dropdownEl && dropdownEl.contains(event.target as Node)) {
      return
    }
    closeDropdown()
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>
