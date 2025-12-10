<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '@/api/auth'
import { Lock, User, Mail, Smile } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import BaseInput from '@/components/common/BaseInput.vue'
import BaseButton from '@/components/common/BaseButton.vue'

const { t } = useI18n()

const router = useRouter()

const form = ref({
  loginId: '',
  password: '',
  email: '',
  displayName: ''
})

const error = ref('')
const isLoading = ref(false)

async function handleSignup() {
  error.value = ''
  isLoading.value = true

  try {
    const { data } = await authApi.signup(form.value)
    if (data.success) {
      alert(t('auth.signupSuccess'))
      router.push('/login')
    }
  } catch (err) {
    error.value = err.response?.data?.error?.message || t('auth.signupFailed')
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div
    class="min-h-screen flex items-start justify-center bg-gray-50 dark:bg-gray-900 py-12 px-4 sm:px-6 lg:px-8 pt-20 transition-colors duration-200">
    <div class="max-w-md w-full space-y-8">
      <div>
        <h2 class="mt-6 text-center text-3xl font-extrabold text-gray-900 dark:text-white">
          {{ $t('auth.createAccountTitle') }}
        </h2>
        <p class="mt-2 text-center text-sm text-gray-600 dark:text-gray-400">
          {{ $t('auth.alreadyHaveAccount') }}
          <router-link to="/login"
            class="font-medium text-indigo-600 hover:text-indigo-500 dark:text-indigo-400 dark:hover:text-indigo-300">
            {{ $t('common.login') }}
          </router-link>
        </p>
      </div>
      <form class="mt-8 space-y-6" @submit.prevent="handleSignup">
        <div class="rounded-md shadow-sm -space-y-px">
          <div class="mb-4">
            <BaseInput id="login-id" v-model="form.loginId" name="loginId" type="text" required
              :placeholder="$t('auth.placeholders.loginId')" :label="$t('common.loginId')" hideLabel>
              <template #prefix>
                <User class="h-5 w-5 text-gray-400" />
              </template>
            </BaseInput>
          </div>
          <div class="mb-4">
            <BaseInput id="password" v-model="form.password" name="password" type="password" required
              :placeholder="$t('auth.placeholders.password')" :label="$t('common.password')" hideLabel>
              <template #prefix>
                <Lock class="h-5 w-5 text-gray-400" />
              </template>
            </BaseInput>
          </div>
          <div class="mb-4">
            <BaseInput id="email" v-model="form.email" name="email" type="email" required
              :placeholder="$t('auth.placeholders.email')" :label="$t('common.email')" hideLabel>
              <template #prefix>
                <Mail class="h-5 w-5 text-gray-400" />
              </template>
            </BaseInput>
          </div>
          <div>
            <BaseInput id="display-name" v-model="form.displayName" name="displayName" type="text" required
              :placeholder="$t('auth.placeholders.displayName')" :label="$t('common.displayName')" hideLabel>
              <template #prefix>
                <Smile class="h-5 w-5 text-gray-400" />
              </template>
            </BaseInput>
          </div>
        </div>

        <div v-if="error" class="text-red-500 dark:text-red-400 text-sm text-center">
          {{ error }}
        </div>

        <div>
          <BaseButton type="submit" :loading="isLoading" fullWidth variant="primary">
            {{ $t('auth.signup') }}
          </BaseButton>
        </div>
      </form>
    </div>
  </div>
</template>
