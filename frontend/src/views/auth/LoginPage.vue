<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { Lock, User } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseInput from '@/components/common/BaseInput.vue'
import BaseButton from '@/components/common/BaseButton.vue'

const { t } = useI18n()

const router = useRouter()
const authStore = useAuthStore()

const loginId = ref('')
const password = ref('')
const error = ref('')
const isLoading = ref(false)

async function handleLogin() {
  error.value = ''
  isLoading.value = true

  try {
    await authStore.login({
      loginId: loginId.value,
      password: password.value
    })
    router.push('/')
  } catch (err) {
    error.value = err.response?.data?.error?.message || t('auth.loginFailed')
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div
    class="min-h-screen flex items-start justify-center bg-gray-50 dark:bg-gray-900 py-12 px-4 sm:px-6 lg:px-8 pt-32 transition-colors duration-200">
    <div class="max-w-md w-full space-y-8">
      <div>
        <h2 class="mt-6 text-center text-3xl font-extrabold text-gray-900 dark:text-white">
          {{ $t('common.login') }}
        </h2>
        <p class="mt-2 text-center text-sm text-gray-600 dark:text-gray-400">
          {{ $t('common.or') }}
          <router-link to="/signup"
            class="font-medium text-indigo-600 hover:text-indigo-500 dark:text-indigo-400 dark:hover:text-indigo-300">
            {{ $t('auth.createAccount') }}
          </router-link>
        </p>
      </div>
      <form class="mt-8 space-y-6" @submit.prevent="handleLogin">
        <div class="rounded-md shadow-sm -space-y-px">
          <div class="mb-4">
            <BaseInput id="login-id" v-model="loginId" name="loginId" type="text" required
              :placeholder="$t('auth.placeholders.loginId')" :label="$t('common.loginId')" hideLabel>
              <template #prefix>
                <User class="h-5 w-5 text-gray-400" />
              </template>
            </BaseInput>
          </div>
          <div>
            <BaseInput id="password" v-model="password" name="password" type="password" required
              :placeholder="$t('auth.placeholders.password')" :label="$t('common.password')" hideLabel>
              <template #prefix>
                <Lock class="h-5 w-5 text-gray-400" />
              </template>
            </BaseInput>
          </div>
        </div>

        <div v-if="error" class="text-red-500 dark:text-red-400 text-sm text-center">
          {{ error }}
        </div>

        <div>
          <BaseButton type="submit" :loading="isLoading" fullWidth variant="primary">
            {{ $t('common.login') }}
          </BaseButton>
        </div>
      </form>
    </div>
  </div>
</template>
