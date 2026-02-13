<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api/auth'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { useToastStore } from '@/stores/toast'
import { ChevronLeft, Mail } from 'lucide-vue-next'

const { t } = useI18n()
const router = useRouter()
const toastStore = useToastStore()

const email = ref('')
const isLoading = ref(false)
const isSent = ref(false)

async function handleSendResetLink() {
  if (!email.value.trim()) {
    toastStore.addToast(t('auth.placeholders.email'), 'error')
    return
  }

  isLoading.value = true
  try {
    const { data } = await authApi.sendPasswordResetLinkByEmail(email.value.trim())
    if (data.success) {
      isSent.value = true
      toastStore.addToast(t('auth.resetLinkSent'), 'success')
    }
  } catch (error) {
    if (error?.response?.data?.error?.code === 'A009') {
      toastStore.addToast(t('auth.userDeleted'), 'info')
      router.push(`/signup?email=${encodeURIComponent(email.value.trim())}`)
    } else {
      const message = error?.response?.data?.error?.message || t('auth.verificationFailed')
      toastStore.addToast(message, 'error')
    }
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="p-8 relative h-full flex flex-col justify-center">
    <div class="absolute top-4 left-4">
      <router-link
        to="/login"
        class="flex items-center text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200 transition-colors"
      >
        <ChevronLeft class="h-5 w-5 mr-1" />
        <span class="text-sm font-medium">{{ $t('common.back') }}</span>
      </router-link>
    </div>

    <div class="text-center mb-8">
      <h2 class="text-2xl font-bold text-gray-900 dark:text-white">
        {{ t('auth.forgotPassword') }}
      </h2>
      <p class="mt-2 text-sm text-gray-600 dark:text-gray-400 whitespace-pre-line">
        {{ t('auth.forgotPasswordDescription') }}
      </p>
    </div>

    <div v-if="!isSent" class="w-[80%] mx-auto space-y-6">
      <BaseInput
        id="email"
        v-model="email"
        type="email"
        :placeholder="t('auth.placeholders.email')"
        :label="t('auth.email')"
        hideLabel
      >
        <template #prefix>
          <Mail class="h-5 w-5 text-gray-400" />
        </template>
      </BaseInput>
      <BaseButton
        type="button"
        variant="primary"
        class="w-full"
        :loading="isLoading"
        :disabled="isLoading"
        @click="handleSendResetLink"
      >
        {{ t('auth.sendResetLink') }}
      </BaseButton>
    </div>

    <div v-else class="w-[80%] mx-auto text-center animate-fade-in">
      <BaseButton variant="primary" class="w-full" @click="router.push('/login')">
        {{ t('auth.login') }}
      </BaseButton>
    </div>
  </div>
</template>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.3s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
</style>
