<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AuthPasswordPairFields from '@/components/auth/AuthPasswordPairFields.vue'
import AuthFormShell from '@/components/auth/AuthFormShell.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { usePasswordResetByTokenFlow } from '@/composables/usePasswordResetByTokenFlow'
import { getSingleQueryValue } from '@/utils/routeQueryValue'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()

const token = computed(() => getSingleQueryValue(route.query.token) ?? '')

const newPassword = ref('')
const confirmPassword = ref('')
const { isLoading, resetPassword: handleResetPassword } = usePasswordResetByTokenFlow({
  token,
  newPassword,
  confirmPassword,
})
</script>

<template>
  <AuthFormShell back-to="/login">
    <template #header>
      <h1 class="text-2xl font-bold nv-title">
        {{ t('auth.resetPasswordTitle') }}
      </h1>
    </template>

    <div v-if="!token" class="text-center">
      <p class="nv-text-muted">{{ t('auth.invalidResetLink') }}</p>
      <BaseButton variant="primary" class="mt-6 w-full" @click="router.push('/login')">
        {{ t('auth.login') }}
      </BaseButton>
    </div>

    <div v-else class="space-y-6">
      <AuthPasswordPairFields
        v-model:password="newPassword"
        v-model:confirm-password="confirmPassword"
        password-id="new-password"
        confirm-password-id="confirm-password"
        password-name="new-password"
        confirm-password-name="confirm-password"
        :password-label="t('auth.newPassword')"
        :confirm-password-label="t('auth.newPasswordConfirm')"
        :password-placeholder="t('auth.newPassword')"
        :confirm-password-placeholder="t('auth.newPasswordConfirm')"
        hide-labels
      />

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
  </AuthFormShell>
</template>
