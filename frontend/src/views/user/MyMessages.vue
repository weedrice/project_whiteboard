<template>
    <div class="bg-white dark:bg-gray-800 shadow rounded-lg overflow-hidden transition-colors duration-200">
        <div class="px-4 py-5 sm:px-6 flex justify-between items-center">
            <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white">{{ $t('user.message.boxTitle') }}
            </h3>
            <div class="flex items-center space-x-4">
                <PageSizeSelector v-model="size" @change="handleSizeChange" />
                <BaseButton v-if="selectedMessages.length > 0" @click="deleteSelectedMessages" variant="danger"
                    size="sm" class="mr-2">
                    {{ $t('common.delete') }} ({{ selectedMessages.length }})
                </BaseButton>
                <div class="space-x-2">
                    <div class="space-x-2">
                        <BaseButton @click="changeViewType('received')" size="sm"
                            :variant="viewType === 'received' ? 'primary' : 'ghost'"
                            :class="viewType === 'received' ? '!bg-indigo-100 !text-indigo-700 dark:!bg-indigo-900/50 dark:!text-indigo-400' : 'text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200'">
                            {{ $t('user.message.received') }}
                        </BaseButton>
                        <BaseButton @click="changeViewType('sent')" size="sm"
                            :variant="viewType === 'sent' ? 'primary' : 'ghost'"
                            :class="viewType === 'sent' ? '!bg-indigo-100 !text-indigo-700 dark:!bg-indigo-900/50 dark:!text-indigo-400' : 'text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200'">
                            {{ $t('user.message.sent') }}
                        </BaseButton>
                    </div>
                </div>
            </div>
        </div>

        <div v-if="loading" class="divide-y divide-gray-200 dark:divide-gray-700">
            <div v-for="i in 5" :key="i" class="p-4 flex items-start">
                <BaseSkeleton width="20px" height="20px" className="mr-4 mt-1" />
                <div class="flex-1">
                    <div class="flex justify-between mb-1">
                        <BaseSkeleton width="100px" height="16px" />
                        <BaseSkeleton width="80px" height="12px" />
                    </div>
                    <BaseSkeleton width="80%" height="16px" />
                </div>
            </div>
        </div>

        <div v-else-if="messages.length === 0" class="text-center py-10 text-gray-500 dark:text-gray-400">
            {{ $t('user.message.empty') }}
        </div>

        <ul v-else class="divide-y divide-gray-200 dark:divide-gray-700">
            <li v-for="msg in messages" :key="msg.messageId"
                class="p-4 hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer flex items-start transition-colors duration-200"
                @click="openMessage(msg)">
                <div class="flex items-center justify-center h-full mr-4 p-2 -ml-2 cursor-pointer" @click.stop>
                    <BaseCheckbox :value="msg.messageId" v-model="selectedMessages" />
                </div>
                <div class="flex-1 min-w-0">
                    <div class="flex justify-between">
                        <div class="text-sm font-medium text-indigo-600 dark:text-indigo-400">
                            {{ msg.partner.displayName }}
                        </div>
                        <div class="text-xs text-gray-500 dark:text-gray-400">
                            {{ new Date(msg.createdAt).toLocaleString() }}
                        </div>
                    </div>
                    <p class="mt-1 text-sm text-gray-900 dark:text-gray-100 line-clamp-1"
                        :class="{ 'font-bold': viewType === 'received' && !msg.read }">
                        {{ msg.content }}
                    </p>
                </div>
            </li>
        </ul>

        <div v-if="messages.length > 0" class="mt-4 flex justify-center pb-6">
            <Pagination :current-page="page" :total-pages="totalPages" @page-change="handlePageChange" />
        </div>

        <!-- Message Detail Modal -->
        <BaseModal :isOpen="!!selectedMessage" :title="$t('user.message.title')" @close="selectedMessage = null">
            <div v-if="selectedMessage" class="p-4 space-y-4">
                <div class="flex justify-between items-start border-b dark:border-gray-700 pb-2">
                    <div>
                        <span class="block text-xs text-gray-500 dark:text-gray-400">{{ viewType === 'received' ?
                            $t('user.message.from') : $t('user.message.to') }}</span>
                        <span class="text-sm font-medium text-gray-900 dark:text-white">{{
                            selectedMessage.partner.displayName }}</span>
                    </div>
                    <span class="text-xs text-gray-500 dark:text-gray-400">{{ new
                        Date(selectedMessage.createdAt).toLocaleString() }}</span>
                </div>
                <div class="text-sm text-gray-900 dark:text-gray-100 whitespace-pre-wrap min-h-[100px]">
                    {{ selectedMessage.content }}
                </div>

                <div class="flex justify-end space-x-2 pt-2">
                    <BaseButton @click="selectedMessage = null" variant="secondary">{{ $t('common.close') }}
                    </BaseButton>
                    <BaseButton v-if="viewType === 'received'" @click="startReply(selectedMessage)">
                        {{ $t('user.message.reply') }}
                    </BaseButton>
                </div>
            </div>
        </BaseModal>

        <!-- Reply Modal -->
        <BaseModal :isOpen="isReplyModalOpen" :title="$t('user.message.replyTitle')" @close="closeReplyModal">
            <div class="p-4 space-y-4">
                <div>
                    <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('user.message.to')
                    }}</label>
                    <div class="mt-1 p-2 bg-gray-50 dark:bg-gray-700 rounded-md text-sm text-gray-900 dark:text-white">
                        {{ replyTarget?.partner.displayName }}
                    </div>
                </div>
                <div>
                    <BaseTextarea v-model="replyContent" :label="$t('user.message.content')" rows="4" />
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
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import BaseTextarea from '@/components/common/ui/BaseTextarea.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import PageSizeSelector from '@/components/common/widgets/PageSizeSelector.vue'
import { useI18n } from 'vue-i18n'
import { useNotificationStore } from '@/stores/notification'
import { useToastStore } from '@/stores/toast'
import { useConfirm } from '@/composables/useConfirm'
import logger from '@/utils/logger'

const { t } = useI18n()
const notificationStore = useNotificationStore()
const toastStore = useToastStore()
const { confirm } = useConfirm()

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
        logger.error('Failed to fetch messages:', error)
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
            logger.error('Failed to mark as read:', error)
        }
    }
}

async function deleteSelectedMessages() {
    const isConfirmed = await confirm(t('common.messages.confirmDelete'))
    if (!isConfirmed) return
    try {
        const { data } = await messageApi.deleteMessages(selectedMessages.value)
        if (data.success) {
            toastStore.addToast(t('common.messages.deleteSuccess'), 'success')
            fetchMessages()
        }
    } catch (error) {
        logger.error('Failed to delete messages:', error)
        toastStore.addToast(t('common.messages.deleteFailed'), 'error')
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
            toastStore.addToast(t('user.message.sendSuccess'), 'success')
            closeReplyModal()
        }
    } catch (error) {
        logger.error('Failed to send reply:', error)
        toastStore.addToast(t('user.message.sendFailed'), 'error')
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
