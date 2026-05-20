<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api/auth'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { useToastStore } from '@/stores/toast'
import { extractErrorMessage } from '@/utils/errorHandler'
import { useAuthPasswordValidation } from '@/composables/useAuthPasswordValidation'
import axios from 'axios'
import { Mail, ChevronLeft, Key, User, CheckCircle } from 'lucide-vue-next'

const { t } = useI18n()
const router = useRouter()
const toastStore = useToastStore()
const { validatePasswordPair } = useAuthPasswordValidation()

const activeTab = ref('id')
const form = reactive({
    email: '',
    code: '',
    newPassword: '',
    confirmPassword: ''
})

const status = reactive({
    isCodeSent: false,
    isVerified: false,
    verificationTicket: '',
    loading: false,
    foundId: ''
})

const resetState = () => {
    form.email = ''
    form.code = ''
    form.newPassword = ''
    form.confirmPassword = ''
    status.isCodeSent = false
    status.isVerified = false
    status.verificationTicket = ''
    status.foundId = ''
    status.loading = false
}

const resetVerificationState = () => {
    form.code = ''
    status.isCodeSent = false
    status.isVerified = false
    status.verificationTicket = ''
}

const switchTab = (tab: string) => {
    activeTab.value = tab
    resetState()
}

const handleSendCode = async () => {
    const email = form.email.trim()
    if (!email) {
        toastStore.addToast(t('auth.placeholders.email'), 'error')
        return
    }

    status.loading = true
    try {
        const purpose = activeTab.value === 'id' ? 'FIND_ID' : 'PASSWORD_RESET'
        const { data } = await authApi.sendVerificationCode(email, purpose)
        if (data.success) {
            resetVerificationState()
            status.isCodeSent = true
            status.foundId = ''
            toastStore.addToast(t('auth.codeSent'), 'success')
        }
    } catch {
        // Error handled by global interceptor or toast
    } finally {
        status.loading = false
    }
}

const handleVerifyCode = async () => {
    if (!form.code) {
        toastStore.addToast(t('auth.codeInvalid'), 'error')
        return
    }

    status.loading = true
    try {
        const purpose = activeTab.value === 'id' ? 'FIND_ID' : 'PASSWORD_RESET'
        const { data } = await authApi.verifyCode(form.email.trim(), form.code, purpose)
        const verificationTicket = data.data?.verificationTicket
        if (!data.success || !verificationTicket) {
            throw new Error(t('auth.verificationFailed'))
        }

        if (activeTab.value === 'id') {
            await findId(verificationTicket)
        } else {
            status.isVerified = true
            status.verificationTicket = verificationTicket
            toastStore.addToast(t('auth.codeVerified'), 'success')
        }
    } catch (error: unknown) {
        const message = extractErrorMessage(error) || t('auth.verificationFailed')
        toastStore.addToast(message, 'error')
    } finally {
        status.loading = false
    }
}

const findId = async (verificationTicket: string) => {
    try {
        const { data } = await authApi.findId(form.email.trim(), verificationTicket)
        if (data.success) {
            status.isVerified = true
            status.verificationTicket = verificationTicket
            status.foundId = data.data.loginId
            toastStore.addToast(t('auth.codeVerified'), 'success')
        }
    } catch (error: unknown) {
        if (axios.isAxiosError(error) && error.response?.data?.error?.code === 'A009') {
            toastStore.addToast(t('auth.userDeleted'), 'info')
            router.push(`/signup?email=${encodeURIComponent(form.email.trim())}`)
        } else {
            const message = extractErrorMessage(error) || t('auth.verificationFailed')
            toastStore.addToast(message, 'error')
        }
    }
}

const handleResetPassword = async () => {
    const passwordError = validatePasswordPair(form.newPassword, form.confirmPassword, {
        messages: {
            invalid: t('auth.validation.passwordStrength'),
            mismatch: t('auth.passwordMismatch')
        }
    })
    if (passwordError) {
        toastStore.addToast(passwordError, 'error')
        return
    }

    status.loading = true
    try {
        const { data } = await authApi.resetPassword({
            email: form.email.trim(),
            verificationTicket: status.verificationTicket,
            newPassword: form.newPassword
        })
        if (data.success) {
            toastStore.addToast(t('auth.passwordResetSuccess'), 'success')
            router.push('/login')
        }
    } catch (error: unknown) {
        if (axios.isAxiosError(error) && error.response?.data?.error?.code === 'A009') {
            toastStore.addToast(t('auth.userDeleted'), 'info')
            router.push(`/signup?email=${encodeURIComponent(form.email.trim())}`)
        } else {
            const message = extractErrorMessage(error) || t('auth.verificationFailed')
            toastStore.addToast(message, 'error')
        }
    } finally {
        status.loading = false
    }
}
</script>

<template>
    <div class="p-8 relative h-full flex flex-col justify-center">
        <!-- Back Button -->
        <div class="absolute top-4 left-4">
            <router-link to="/login"
                class="flex items-center text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200 transition-colors">
                <ChevronLeft class="h-5 w-5 mr-1" />
                <span class="text-sm font-medium">{{ $t('common.back') }}</span>
            </router-link>
        </div>

        <div class="w-[80%] mx-auto">
            <!-- Tabs -->
            <div class="flex border-b border-gray-200 dark:border-gray-700 mb-6">
                <BaseButton @click="switchTab('id')" variant="ghost" class="flex-1 rounded-b-none border-b-2"
                    :class="activeTab === 'id' ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400' : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300'">
                    <User class="w-4 h-4 mr-2" />
                    {{ t('auth.findId') }}
                </BaseButton>
                <BaseButton @click="switchTab('password')" variant="ghost" class="flex-1 rounded-b-none border-b-2"
                    :class="activeTab === 'password' ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400' : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300'">
                    <Key class="w-4 h-4 mr-2" />
                    {{ t('auth.findPassword') }}
                </BaseButton>
            </div>

            <!-- Content -->
            <div class="space-y-6">
                <!-- Step 1: Email & Code -->
                <div v-if="!status.foundId && (!status.isVerified || activeTab === 'id')">
                    <div class="space-y-4">
                        <div class="flex gap-2 items-end">
                            <div class="flex-grow">
                                <BaseInput v-model="form.email" type="email" :label="t('auth.email')"
                                    :placeholder="t('auth.placeholders.email')" :disabled="status.loading" hideLabel>
                                    <template #prefix>
                                        <Mail class="h-5 w-5 text-gray-400" />
                                    </template>
                                </BaseInput>
                            </div>
                            <BaseButton @click="handleSendCode" :disabled="status.loading"
                                :loading="status.loading && !status.isCodeSent" class="mb-[2px] h-[42px]">
                                {{ status.isCodeSent ? t('auth.resendCode') : t('auth.sendCode') }}
                            </BaseButton>
                        </div>

                        <div v-if="status.isCodeSent && !status.isVerified"
                            class="flex gap-2 items-end animate-fade-in-down">
                            <div class="flex-grow">
                                <BaseInput v-model="form.code" :placeholder="t('auth.codePlaceholder')" hideLabel>
                                    <template #prefix>
                                        <CheckCircle class="h-5 w-5 text-gray-400" />
                                    </template>
                                </BaseInput>
                            </div>
                            <BaseButton @click="handleVerifyCode" :loading="status.loading" class="mb-[2px] h-[42px]">
                                {{ t('auth.verifyCode') }}
                            </BaseButton>
                        </div>
                    </div>
                </div>

                <!-- Step 2: Result (Find ID) -->
                <div v-if="activeTab === 'id' && status.foundId"
                    class="text-center py-8 bg-gray-50 dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 animate-fade-in">
                    <p class="text-gray-600 dark:text-gray-300 mb-2">{{ t('auth.yourIdIs', { id: '' }).replace('{id}',
                        '') }}</p>
                    <p class="text-2xl font-bold text-indigo-600 dark:text-indigo-400 mb-6">{{ status.foundId }}</p>
                    <BaseButton @click="router.push('/login')" full-width>
                        {{ t('auth.login') }}
                    </BaseButton>
                </div>

                <!-- Step 2: Reset Password (Find Password) -->
                <div v-if="activeTab === 'password' && status.isVerified" class="space-y-6 animate-fade-in">
                    <BaseInput v-model="form.newPassword" type="password" :label="t('auth.newPassword')" required />
                    <BaseInput v-model="form.confirmPassword" type="password" :label="t('auth.newPasswordConfirm')"
                        required />
                    <BaseButton @click="handleResetPassword" :loading="status.loading" full-width variant="primary">
                        {{ t('auth.resetPassword') }}
                    </BaseButton>
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
.animate-fade-in-down {
    animation: fadeInDown 0.3s ease-out;
}

.animate-fade-in {
    animation: fadeIn 0.3s ease-out;
}

@keyframes fadeInDown {
    from {
        opacity: 0;
        transform: translateY(-10px);
    }

    to {
        opacity: 1;
        transform: translateY(0);
    }
}

@keyframes fadeIn {
    from {
        opacity: 0;
    }

    to {
        opacity: 1;
    }
}
</style>
