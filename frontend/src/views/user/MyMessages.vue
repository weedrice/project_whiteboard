<template>
  <div class="bg-white shadow rounded-lg overflow-hidden">
    <div class="px-4 py-5 sm:px-6 flex justify-between items-center">
        <h3 class="text-lg font-medium leading-6 text-gray-900">{{ $t('user.message.boxTitle') }}</h3>
        <div class="flex items-center space-x-4">
            <PageSizeSelector v-model="size" @change="handleSizeChange" />
            <button 
                v-if="selectedMessages.length > 0"
                @click="deleteSelectedMessages"
                class="px-3 py-1 text-sm font-medium rounded-md text-red-700 bg-red-100 hover:bg-red-200 mr-2"
            >
                {{ $t('common.delete') }} ({{ selectedMessages.length }})
            </button>
            <div class="space-x-2">
                <button 
                    @click="changeViewType('received')" 
                    class="px-3 py-1 text-sm font-medium rounded-md"
                    :class="viewType === 'received' ? 'bg-indigo-100 text-indigo-700' : 'text-gray-500 hover:text-gray-700'"
                >
                    {{ $t('user.message.received') }}
                </button>
                 <button 
                    @click="changeViewType('sent')" 
                    class="px-3 py-1 text-sm font-medium rounded-md"
                    :class="viewType === 'sent' ? 'bg-indigo-100 text-indigo-700' : 'text-gray-500 hover:text-gray-700'"
                >
                    {{ $t('user.message.sent') }}
                </button>
            </div>
        </div>
    </div>

    <div v-if="loading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else-if="messages.length === 0" class="text-center py-10 text-gray-500">
        {{ $t('user.message.empty') }}
    </div>

    <ul v-else class="divide-y divide-gray-200">
      <li 
        v-for="msg in messages" 
        :key="msg.messageId" 
        class="p-4 hover:bg-gray-50 cursor-pointer flex items-start"
        @click="openMessage(msg)"
      >
        <div class="flex items-center justify-center h-full mr-4 p-2 -ml-2 cursor-pointer" @click.stop="toggleSelection(msg.messageId)">
            <input 
                type="checkbox" 
                :value="msg.messageId" 
                v-model="selectedMessages"
                class="focus:ring-indigo-500 h-5 w-5 text-indigo-600 border-gray-300 rounded cursor-pointer"
                @click.stop
            >
        </div>
        <div class="flex-1 min-w-0">
            <div class="flex justify-between">
                <div class="text-sm font-medium text-indigo-600">
                    {{ msg.partner.displayName }}
                </div>
                <div class="text-xs text-gray-500">
                    {{ new Date(msg.createdAt).toLocaleString() }}
                </div>
            </div>
            <p 
                class="mt-1 text-sm text-gray-900 line-clamp-1"
                :class="{ 'font-bold': viewType === 'received' && !msg.read }"
            >
                {{ msg.content }}
            </p>
        </div>
      </li>
    </ul>

    <div v-if="messages.length > 0" class="mt-4 flex justify-center pb-6">
        <Pagination 
            :current-page="page" 
            :total-pages="totalPages"
            @page-change="handlePageChange" 
        />
    </div>

    <!-- Message Detail Modal -->
    <BaseModal :isOpen="!!selectedMessage" :title="$t('user.message.title')" @close="selectedMessage = null">
        <div v-if="selectedMessage" class="p-4 space-y-4">
             <div class="flex justify-between items-start border-b pb-2">
                 <div>
                     <span class="block text-xs text-gray-500">{{ viewType === 'received' ? $t('user.message.from') : $t('user.message.to') }}</span>
                     <span class="text-sm font-medium">{{ selectedMessage.partner.displayName }}</span>
                 </div>
                 <span class="text-xs text-gray-500">{{ new Date(selectedMessage.createdAt).toLocaleString() }}</span>
             </div>
             <div class="text-sm text-gray-900 whitespace-pre-wrap min-h-[100px]">
                 {{ selectedMessage.content }}
             </div>
             
             <div class="flex justify-end space-x-2 pt-2">
                 <BaseButton @click="selectedMessage = null" variant="secondary">{{ $t('common.close') }}</BaseButton>
                 <BaseButton 
                    v-if="viewType === 'received'" 
                    @click="startReply(selectedMessage)"
                 >
                    {{ $t('user.message.reply') }}
                 </BaseButton>
             </div>
        </div>
    </BaseModal>

    <!-- Reply Modal -->
    <BaseModal :isOpen="isReplyModalOpen" :title="$t('user.message.replyTitle')" @close="closeReplyModal">
        <div class="p-4 space-y-4">
             <div>
                 <label class="block text-sm font-medium text-gray-700">{{ $t('user.message.to') }}</label>
                 <div class="mt-1 p-2 bg-gray-50 rounded-md text-sm text-gray-900">
                     {{ replyTarget?.partner.displayName }}
                 </div>
             </div>
             <div>
                 <label class="block text-sm font-medium text-gray-700">{{ $t('user.message.content') }}</label>
                 <textarea 
                    v-model="replyContent" 
                    rows="4" 
                    class="mt-1 shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md"
                 ></textarea>
             </div>
             <div class="flex justify-end space-x-2">
                 <BaseButton @click="closeReplyModal" variant="secondary">{{ $t('common.cancel') }}</BaseButton>
                 <BaseButton @click="sendReply" :disabled="isSending">
                     {{ isSending ? $t('common.messages.sending') : $t('common.send') }}
                 </BaseButton>
             </div>
        </div>
    </BaseModal>

  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { messageApi } from '@/api/message'
import BaseModal from '@/components/common/BaseModal.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import Pagination from '@/components/common/Pagination.vue'
import PageSizeSelector from '@/components/common/PageSizeSelector.vue'
import { useI18n } from 'vue-i18n'
import { useNotificationStore } from '@/stores/notification'

const { t } = useI18n()
const notificationStore = useNotificationStore()

const viewType = ref('received') // 'received' | 'sent'
const messages = ref([])
const loading = ref(false)
const selectedMessage = ref(null)
const selectedMessages = ref([])

const page = ref(0)
const size = ref(15)
const totalPages = ref(0)

const isReplyModalOpen = ref(false)
const replyTarget = ref(null)
const replyContent = ref('')
const isSending = ref(false)

async function fetchMessages() {
    loading.value = true
    messages.value = []
    selectedMessages.value = []
    try {
        const params = {
            page: page.value,
            size: size.value
        }
        const { data } = viewType.value === 'received' 
            ? await messageApi.getReceivedMessages(params) 
            : await messageApi.getSentMessages(params)
        
        if (data.success) {
            messages.value = data.data?.content || [] 
            totalPages.value = data.data?.totalPages || 0
        }
    } catch (error) {
        console.error('Failed to fetch messages:', error)
    } finally {
        loading.value = false
    }
}

function handlePageChange(newPage) {
    page.value = newPage
    fetchMessages()
}

function handleSizeChange() {
    page.value = 0
    fetchMessages()
}

function toggleSelection(messageId) {
    const index = selectedMessages.value.indexOf(messageId)
    if (index === -1) {
        selectedMessages.value.push(messageId)
    } else {
        selectedMessages.value.splice(index, 1)
    }
}

function changeViewType(type) {
    viewType.value = type
    page.value = 0
    fetchMessages()
}

async function openMessage(msg) {
    selectedMessage.value = msg
    if (viewType.value === 'received' && !msg.read) {
        try {
            await messageApi.getMessage(msg.messageId)
            msg.read = true
            notificationStore.fetchUnreadCount()
        } catch (error) {
            console.error('Failed to mark as read:', error)
        }
    }
}

async function deleteSelectedMessages() {
    if (!confirm(t('common.messages.confirmDelete'))) return
    try {
        const { data } = await messageApi.deleteMessages(selectedMessages.value)
        if (data.success) {
            alert(t('common.messages.deleteSuccess'))
            fetchMessages()
        }
    } catch (error) {
        console.error('Failed to delete messages:', error)
        alert(t('common.messages.deleteFailed'))
    }
}

function startReply(msg) {
    replyTarget.value = msg
    selectedMessage.value = null
    isReplyModalOpen.value = true
}

function closeReplyModal() {
    isReplyModalOpen.value = false
    replyTarget.value = null
    replyContent.value = ''
}

async function sendReply() {
    if (!replyContent.value.trim()) return
    isSending.value = true
    try {
        const { data } = await messageApi.sendMessage(replyTarget.value.partner.userId, replyContent.value)
        if (data.success) {
            alert(t('user.message.sendSuccess'))
            closeReplyModal()
        }
    } catch (error) {
        console.error('Failed to send reply:', error)
        alert(t('user.message.sendFailed'))
    } finally {
        isSending.value = false
    }
}

watch(viewType, () => {
    fetchMessages()
})

onMounted(() => {
    fetchMessages()
})
</script>
