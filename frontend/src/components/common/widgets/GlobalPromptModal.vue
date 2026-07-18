<script setup lang="ts">
import { usePromptStore } from '@/stores/prompt'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import { isComposingKeyboardEvent } from '@/utils/keyboard'

const promptStore = usePromptStore()

const handleConfirmKeyup = (event: KeyboardEvent) => {
    if (isComposingKeyboardEvent(event)) return
    promptStore.confirm()
}
</script>

<template>
    <BaseModal :isOpen="promptStore.isOpen" :title="promptStore.title" @close="promptStore.cancel">
        <div class="space-y-4">
            <p class="text-sm nv-text-subtle">
                {{ promptStore.message }}
            </p>
            <BaseInput v-model="promptStore.inputValue" :label="promptStore.placeholder || promptStore.title" :placeholder="promptStore.placeholder" hideLabel
                :maxlength="promptStore.maxLength"
                :error="promptStore.required && !promptStore.inputValue.trim() ? promptStore.errorMessage : ''"
                @keyup.enter="handleConfirmKeyup" />
        </div>
        <template #footer>
            <div class="flex justify-end space-x-3">
                <BaseButton @click="promptStore.cancel" variant="secondary">
                    {{ promptStore.cancelText }}
                </BaseButton>
                <BaseButton @click="promptStore.confirm" :variant="promptStore.confirmVariant"
                    :disabled="promptStore.required && !promptStore.inputValue.trim()">
                    {{ promptStore.confirmText }}
                </BaseButton>
            </div>
        </template>
    </BaseModal>
</template>

