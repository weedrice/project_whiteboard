<script setup lang="ts">
import { useConfirmStore } from '@/stores/confirm'
import BaseModal from '@/components/common/BaseModal.vue'
import BaseButton from '@/components/common/BaseButton.vue'

const confirmStore = useConfirmStore()
</script>

<template>
    <BaseModal :isOpen="confirmStore.isOpen" :title="confirmStore.title" @close="confirmStore.cancel">
        <div class="text-sm text-gray-500 dark:text-gray-400">
            {{ confirmStore.message }}
        </div>
        <template #footer>
            <div class="flex justify-end space-x-3">
                <BaseButton @click="confirmStore.cancel" variant="secondary">
                    {{ confirmStore.cancelText }}
                </BaseButton>
                <BaseButton @click="confirmStore.confirm" variant="primary">
                    {{ confirmStore.confirmText }}
                </BaseButton>
            </div>
        </template>
    </BaseModal>
</template>
