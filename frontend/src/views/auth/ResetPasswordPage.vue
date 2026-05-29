<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { ChevronLeft, Lock } from 'lucide-vue-next'
import { usePasswordResetByTokenFlow } from '@/composables/usePasswordResetByTokenFlow'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()

const token = computed(() => {
  const t = route.query.token
  return typeof t === 'string' ? t : ''
})

const newPassword = ref('')
const confirmPassword = ref('')
const { isLoading, resetPassword: handleResetPassword } = usePasswordResetByTokenFlow({
  token,
  newPassword,
  confirmPassword,
})
</script>

<template>
  <div class="p-8 relative h-full flex flex-col justify-center">
    <div class="absolute top-4 left-4">
      <router-link
        to="/login"
        class="flex items-center nv-text-subtle hover:text-[var(--nv-text)] transition-colors"
      >
        <ChevronLeft class="h-5 w-5 mr-1" />
        <span class="text-sm font-medium">{{ $t('common.back') }}</span>
      </router-link>
    </div>

    <div v-if="!token" class="w-[80%] mx-auto text-center">
      <h1 class="mb-3 text-2xl font-bold nv-title">
        {{ t('auth.resetPasswordTitle') }}
      </h1>
      <p class="nv-text-muted">{{ t('auth.invalidResetLink') }}</p>
      <BaseButton variant="primary" class="mt-6 w-full" @click="router.push('/login')">
        {{ t('auth.login') }}
      </BaseButton>
    </div>

    <div v-else class="w-[80%] mx-auto space-y-6">
      <div class="text-center mb-8">
        <h1 class="text-2xl font-bold nv-title">
          {{ t('auth.resetPasswordTitle') }}
        </h1>
      </div>

      <BaseInput
        id="new-password"
        v-model="newPassword"
        type="password"
        name="new-password"
        autocomplete="new-password"
        :placeholder="t('auth.newPassword')"
        :label="t('auth.newPassword')"
        hideLabel
      >
        <template #prefix>
          <Lock class="h-5 w-5 text-gray-400" />
        </template>
      </BaseInput>

      <BaseInput
        id="confirm-password"
        v-model="confirmPassword"
        type="password"
        name="confirm-password"
        autocomplete="new-password"
        :placeholder="t('auth.newPasswordConfirm')"
        :label="t('auth.newPasswordConfirm')"
        hideLabel
      >
        <template #prefix>
          <Lock class="h-5 w-5 text-gray-400" />
        </template>
      </BaseInput>

      <BaseButton
        type="button"
        variant="primary"
        class="w-full"
        :loading="isLoading"
        :disabled="isLoading"
        @click="handleResetPassword"
      >
        {{ t('auth.resetPassword') }}
      </BaseButton>
    </div>
  </div>
</template>
