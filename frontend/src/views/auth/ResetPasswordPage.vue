<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AuthFormShell from '@/components/auth/AuthFormShell.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { Lock } from 'lucide-vue-next'
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
          <Lock class="h-5 w-5 nv-text-subtle" />
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
          <Lock class="h-5 w-5 nv-text-subtle" />
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
  </AuthFormShell>
</template>
