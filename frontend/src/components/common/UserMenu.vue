<template>
  <div class="relative inline-block text-left">
    <div>
      <button
        type="button"
        class="inline-flex justify-center w-full rounded-md text-sm font-medium text-gray-700 hover:text-indigo-600 focus:outline-none"
        @click="toggleDropdown"
      >
        {{ displayName }}
      </button>
    </div>

    <transition
      enter-active-class="transition ease-out duration-100"
      enter-from-class="transform opacity-0 scale-95"
      enter-to-class="transform opacity-100 scale-100"
      leave-active-class="transition ease-in duration-75"
      leave-from-class="transform opacity-100 scale-100"
      leave-to-class="transform opacity-0 scale-95"
    >
      <div
        v-if="isDropdownOpen"
        class="origin-top-right absolute right-0 mt-2 w-56 rounded-md shadow-lg bg-white ring-1 ring-black ring-opacity-5 focus:outline-none z-50"
        role="menu"
        aria-orientation="vertical"
        aria-labelledby="menu-button"
      >
        <div class="py-1" role="none">
          <button
            @click="openMessageModal"
            class="text-gray-700 block px-4 py-2 text-sm w-full text-left hover:bg-gray-100"
            role="menuitem"
          >
            쪽지 보내기
          </button>
          <button
            @click="openReportModal"
            class="text-gray-700 block px-4 py-2 text-sm w-full text-left hover:bg-gray-100"
            role="menuitem"
          >
            신고하기
          </button>
          <button
            @click="handleBlockUser"
            class="text-gray-700 block px-4 py-2 text-sm w-full text-left hover:bg-gray-100"
            role="menuitem"
          >
            차단하기
          </button>
        </div>
      </div>
    </transition>

    <!-- Message Modal -->
    <BaseModal :isOpen="isMessageModalOpen" title="쪽지 보내기" @close="closeMessageModal">
      <div class="p-4">
        <BaseInput
          label="받는 사람"
          :modelValue="displayName"
          :disabled="true"
          class="mb-4"
        />
        <label for="messageContent" class="block text-sm font-medium text-gray-700 mb-1">내용</label>
        <textarea
          id="messageContent"
          v-model="messageContent"
          rows="4"
          class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
        ></textarea>
        <div class="mt-4 flex justify-end">
          <BaseButton @click="closeMessageModal" variant="secondary" class="mr-2">취소</BaseButton>
          <BaseButton @click="handleSendMessage" :disabled="isSendingMessage">
            {{ isSendingMessage ? '전송 중...' : '전송' }}
          </BaseButton>
        </div>
      </div>
    </BaseModal>

    <!-- Report Modal -->
    <BaseModal :isOpen="isReportModalOpen" title="사용자 신고" @close="closeReportModal">
      <div class="p-4">
        <BaseInput
          label="신고 대상"
          :modelValue="displayName"
          :disabled="true"
          class="mb-4"
        />
        <label for="reportReason" class="block text-sm font-medium text-gray-700 mb-1">신고 사유</label>
        <textarea
          id="reportReason"
          v-model="reportReason"
          rows="4"
          class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
        ></textarea>
        <div class="mt-4 flex justify-end">
          <BaseButton @click="closeReportModal" variant="secondary" class="mr-2">취소</BaseButton>
          <BaseButton @click="handleReportUser" :disabled="isReporting">
            {{ isReporting ? '신고 중...' : '신고' }}
          </BaseButton>
        </div>
      </div>
    </BaseModal>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { userApi } from '@/api/user'
import { messageApi } from '@/api/message'
import { reportApi } from '@/api/report'
import BaseModal from '@/components/common/BaseModal.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import BaseButton from '@/components/common/BaseButton.vue'

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

const toggleDropdown = () => {
  isDropdownOpen.value = !isDropdownOpen.value
}

const closeDropdown = () => {
  isDropdownOpen.value = false
}

const openMessageModal = () => {
  closeDropdown()
  isMessageModalOpen.value = true
}

const closeMessageModal = () => {
  isMessageModalOpen.value = false
  messageContent.value = ''
  isSendingMessage.value = false
}

const openReportModal = () => {
  closeDropdown()
  isReportModalOpen.value = true
}

const closeReportModal = () => {
  isReportModalOpen.value = false
  reportReason.value = ''
  isReporting.value = false
}

const handleBlockUser = async () => {
  closeDropdown()
  if (!confirm(`${props.displayName} 님을 차단하시겠습니까?`)) {
    return
  }
  try {
    const { data } = await userApi.blockUser(props.userId)
    if (data.success) {
      alert(`${props.displayName} 님을 차단했습니다.`)
    }
  } catch (error) {
    console.error('Failed to block user:', error)
    alert('사용자 차단에 실패했습니다.')
  }
}

const handleSendMessage = async () => {
  if (!messageContent.value.trim()) {
    alert('메시지 내용을 입력해주세요.')
    return
  }
  isSendingMessage.value = true
  try {
    const { data } = await messageApi.sendMessage(props.userId, messageContent.value)
    if (data.success) {
      alert('쪽지를 성공적으로 전송했습니다.')
      closeMessageModal()
    }
  } catch (error) {
    console.error('Failed to send message:', error)
    alert('쪽지 전송에 실패했습니다.')
  } finally {
    isSendingMessage.value = false
  }
}

const handleReportUser = async () => {
  if (!reportReason.value.trim()) {
    alert('신고 사유를 입력해주세요.')
    return
  }
  isReporting.value = true
  try {
    const { data } = await reportApi.reportUser(props.userId, reportReason.value)
    if (data.success) {
      alert('사용자를 성공적으로 신고했습니다.')
      closeReportModal()
    }
  } catch (error) {
    console.error('Failed to report user:', error)
    alert('사용자 신고에 실패했습니다.')
  } finally {
    isReporting.value = false
  }
}

// Close dropdown when clicking outside
const handleClickOutside = (event) => {
  if (isDropdownOpen.value && event.target && !event.target.closest('.inline-block.text-left')) {
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
