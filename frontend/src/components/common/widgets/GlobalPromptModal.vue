<script setup lang="ts">
import { usePromptStore } from '@/stores/prompt'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'

const promptStore = usePromptStore()
</script>

<template>
    <BaseModal :isOpen="promptStore.isOpen" :title="promptStore.title" @close="promptStore.cancel">
        <div class="space-y-4">
            <p class="text-sm text-gray-500 dark:text-gray-400">
                {{ promptStore.message }}
            </p>
            <BaseInput v-model="promptStore.inputValue" :placeholder="promptStore.placeholder" hideLabel
                @keyup.enter="promptStore.confirm" />
        </div>
        <template #footer>
            <div class="flex justify-end space-x-3">
                <BaseButton @click="promptStore.cancel" variant="secondary">
                    {{ promptStore.cancelText }}
                </BaseButton>
                <BaseButton @click="promptStore.confirm" variant="primary">
                    {{ promptStore.confirmText }}
                </BaseButton>
            </div>
        </template>
    </BaseModal>
</template>

