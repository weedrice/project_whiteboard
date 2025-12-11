<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import BaseInput from '@/components/common/BaseInput.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import { Mail, ChevronLeft } from 'lucide-vue-next'

const { t } = useI18n()
const toastStore = useToastStore()
const router = useRouter()

const email = ref('')
const isLoading = ref(false)

async function handleFindAccount() {
    if (!email.value) {
        toastStore.addToast(t('auth.placeholders.email'), 'error')
        return
    }

    // Placeholder logic
    toastStore.addToast('Find account functionality not implemented yet', 'info')
}
</script>

<template>
    <div class="p-8 relative h-full flex flex-col justify-center">
        <div class="absolute top-4 left-4">
            <router-link to="/login"
                class="flex items-center text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200 transition-colors">
                <ChevronLeft class="h-5 w-5 mr-1" />
                <span class="text-sm font-medium">{{ $t('common.back') || 'Back' }}</span>
            </router-link>
        </div>
        <div class="text-center mb-8">
            <h2 class="text-2xl font-bold text-gray-900 dark:text-white">
                {{ $t('auth.findAccount') || 'Find ID/Password' }}
            </h2>
        </div>

        <form @submit.prevent="handleFindAccount" class="space-y-6">
            <div class="w-[80%] mx-auto">
                <BaseInput id="email" v-model="email" type="email" required :placeholder="$t('auth.placeholders.email')"
                    :label="$t('common.email')" hideLabel>
                    <template #prefix>
                        <Mail class="h-5 w-5 text-gray-400" />
                    </template>
                </BaseInput>
            </div>

            <div class="flex justify-center mt-8">
                <BaseButton type="submit" :loading="isLoading" class="w-[80%]" variant="primary">
                    {{ $t('common.submit') || 'Submit' }}
                </BaseButton>
            </div>
        </form>
    </div>
</template>
