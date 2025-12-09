<template>
  <div class="relative inline-block text-left">
    <div>
      <button
        ref="buttonRef"
        type="button"
        class="inline-flex justify-center w-full rounded-md text-sm font-medium text-gray-700 dark:text-gray-200 hover:text-indigo-600 dark:hover:text-indigo-400 focus:outline-none"
        :class="{ 'cursor-default hover:text-gray-700 dark:hover:text-gray-200': isSelf }"
        @click="toggleDropdown"
      >
        {{ displayName }}
      </button>
    </div>

    <Teleport to="body">
      <div
        v-if="isDropdownOpen"
        :style="dropdownStyle"
        class="absolute w-56 rounded-md shadow-lg bg-white dark:bg-gray-800 ring-1 ring-black ring-opacity-5 dark:ring-gray-700 focus:outline-none z-50 transition-colors duration-200"
        role="menu"
        id="user-menu-dropdown"
        aria-orientation="vertical"
        aria-labelledby="menu-button"
      >
        <div class="py-1" role="none">
          <button
            v-if="!isSelf"
            @click="openMessageModal"
            class="text-gray-700 dark:text-gray-200 block px-4 py-2 text-sm w-full text-left hover:bg-gray-100 dark:hover:bg-gray-700"
            role="menuitem"
          >
            {{ $t('user.menu.sendMessage') }}
          </button>
          <button
            v-if="!isSelf"
            @click="openReportModal"
            class="text-gray-700 dark:text-gray-200 block px-4 py-2 text-sm w-full text-left hover:bg-gray-100 dark:hover:bg-gray-700"
            role="menuitem"
          >
            {{ $t('user.menu.report') }}
          </button>
          <button
            v-if="!isSelf"
            @click="handleBlockUser"
            class="text-gray-700 dark:text-gray-200 block px-4 py-2 text-sm w-full text-left hover:bg-gray-100 dark:hover:bg-gray-700"
            role="menuitem"
          >
            {{ $t('user.menu.block') }}
          </button>
        </div>
      </div>
    </Teleport>

    <!-- Message Modal -->
    <BaseModal :isOpen="isMessageModalOpen" :title="$t('user.message.title')" @close="closeMessageModal">
      <div class="p-4">
        <BaseInput
          :label="$t('user.message.receiver')"
          :modelValue="displayName"
          :disabled="true"
          class="mb-4"
        />
        <label for="messageContent" class="block text-sm font-medium text-gray-700 mb-1">{{ $t('user.message.content') }}</label>
        <textarea
          id="messageContent"
          v-model="messageContent"
          rows="4"
          class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md p-2"
        ></textarea>
        <div class="mt-4 flex justify-end">
          <BaseButton @click="closeMessageModal" variant="secondary" class="mr-2">{{ $t('common.cancel') }}</BaseButton>
          <BaseButton @click="handleSendMessage" :disabled="isSendingMessage">
            {{ isSendingMessage ? $t('common.messages.sending') : $t('common.send') }}
          </BaseButton>
        </div>
      </div>
    </BaseModal>

    <!-- Report Modal -->
    <BaseModal :isOpen="isReportModalOpen" :title="$t('user.report.title')" @close="closeReportModal">
      <div class="p-4">
        <BaseInput
          :label="$t('user.report.target')"
          :modelValue="displayName"
          :disabled="true"
          class="mb-4"
        />
        <label for="reportReason" class="block text-sm font-medium text-gray-700 mb-1">{{ $t('user.report.reason') }}</label>
        <textarea
          id="reportReason"
          v-model="reportReason"
          rows="4"
          class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
        ></textarea>
        <div class="mt-4 flex justify-end">
          <BaseButton @click="closeReportModal" variant="secondary" class="mr-2">{{ $t('common.cancel') }}</BaseButton>
          <BaseButton @click="handleReportUser" :disabled="isReporting">
            {{ isReporting ? $t('common.messages.reporting') : $t('common.report') }}
          </BaseButton>
        </div>
      </div>
    </BaseModal>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { userApi } from '@/api/user'
import { messageApi } from '@/api/message'
import { reportApi } from '@/api/report'
import BaseModal from '@/components/common/BaseModal.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import logger from '@/utils/logger'

const { t } = useI18n()
const authStore = useAuthStore()

const props = defineProps({
  userId: {
    type: Number,
    required: true
  },
  displayName: {
    type: String,
    required: true
  }
})

const isDropdownOpen = ref(false)
const isMessageModalOpen = ref(false)
const isReportModalOpen = ref(false)
const messageContent = ref('')
const reportReason = ref('')
const isSendingMessage = ref(false)
const isReporting = ref(false)

const isSelf = computed(() => {
    return authStore.user && authStore.user.userId === props.userId
})

const buttonRef = ref(null)
const dropdownStyle = ref({})

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
  messageContent.value = ''
  isSendingMessage.value = false
}

const openReportModal = () => {
  closeDropdown()
  if (isSelf.value) return
  isReportModalOpen.value = true
}

const closeReportModal = () => {
  isReportModalOpen.value = false
  reportReason.value = ''
  isReporting.value = false
}

const handleBlockUser = async () => {
  closeDropdown()
  if (isSelf.value) return
  if (!confirm(t('user.block.confirm', { name: props.displayName }))) {
    return
  }
  try {
    const { data } = await userApi.blockUser(props.userId)
    if (data.success) {
      alert(t('user.block.success', { name: props.displayName }))
    }
  } catch (error) {
    logger.error('Failed to block user:', error)
    alert(t('user.block.failed'))
  }
}

const handleSendMessage = async () => {
  if (!messageContent.value.trim()) {
    alert(t('user.message.inputContent'))
    return
  }
  isSendingMessage.value = true
  try {
    const { data } = await messageApi.sendMessage(props.userId, messageContent.value)
    if (data.success) {
      alert(t('user.message.sendSuccess'))
      closeMessageModal()
    }
  } catch (error) {
    logger.error('Failed to send message:', error)
    alert(t('user.message.sendFailed'))
  } finally {
    isSendingMessage.value = false
  }
}

const handleReportUser = async () => {
  if (!reportReason.value.trim()) {
    alert(t('user.report.inputReason'))
    return
  }
  isReporting.value = true
  try {
    const { data } = await reportApi.reportUser(props.userId, reportReason.value)
    if (data.success) {
      alert(t('user.report.reportSuccess'))
      closeReportModal()
    }
  } catch (error) {
    logger.error('Failed to report user:', error)
    alert(t('user.report.reportFailed'))
  } finally {
    isReporting.value = false
  }
}

// Close dropdown when clicking outside
const handleClickOutside = (event) => {
  if (isDropdownOpen.value) {
    // Check if click is inside the button
    if (buttonRef.value && buttonRef.value.contains(event.target)) {
      return
    }
    // Check if click is inside the dropdown content (we need a ref for it)
    const dropdownEl = document.getElementById('user-menu-dropdown')
    if (dropdownEl && dropdownEl.contains(event.target)) {
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
