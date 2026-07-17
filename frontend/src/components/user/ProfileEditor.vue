<template>
  <div class="bg-transparent shadow-none rounded-lg p-0 sm:p-6 sm:bg-[var(--nv-surface)] sm:shadow transition-colors duration-200">
    <ProfileBasicForm
      v-model:display-name="form.displayName"
      v-model:profile-image-error="profileImageError"
      :fallback-display-name="authStore.user?.displayName"
      :profile-image-display-url="profileImageDisplayUrl"
      :display-name-error="profileValidation.visibleError('displayName') || errors.displayName"
      :profile-image-cost-text="profileImageCostText"
      :can-remove-profile-image="canRemoveProfileImage"
      @file-change="handleFileChange"
      @remove-photo="clearProfileImage"
      @blur-display-name="profileValidation.touchField('displayName', profileValidationValues)"
      @submit="handleProfileSubmit"
    />
    <p v-if="errors.profileImage" class="mt-2 text-sm nv-form-error" role="alert">
      {{ errors.profileImage }}
    </p>

    <hr class="nv-border my-4 sm:my-6" />

    <ProfileAgentPanel
      v-model:agent-token="agentToken"
      :agent-error="agentError"
      :is-claiming="isClaiming"
      :is-agent-list-pending="isAgentListPending"
      :is-agent-list-error="isAgentsError"
      :is-email-verified="isEmailVerified"
      :active-agent="activeAgent"
      :processing-agent-id="processingAgentId"
      :processing-action="processingAction"
      @claim="handleClaimAgent"
      @suspend="handleSuspendAgent"
      @retry="refetchAgents()"
    />

    <hr class="nv-border my-4 sm:my-6" />

    <AccountDeletionSection
      v-model:show-delete-modal="showDeleteModal"
      v-model:delete-password="deletePassword"
      :delete-error="deleteError"
      :is-deleting="isDeletingAccount"
      :loading="loading"
      @save="updateProfile"
      @delete="handleDeleteAccount"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AccountDeletionSection from '@/components/user/AccountDeletionSection.vue'
import ProfileAgentPanel from '@/components/user/ProfileAgentPanel.vue'
import ProfileBasicForm from '@/components/user/ProfileBasicForm.vue'
import { useConfirm } from '@/composables/useConfirm'
import { useUser } from '@/composables/useUser'
import { useAccountDeletion } from '@/features/user/profile/useAccountDeletion'
import { useMyAgentActions } from '@/features/user/agents/useMyAgentActions'
import { useProfileImageEditor } from '@/features/user/profile/useProfileImageEditor'
import { useProfileUpdateSubmit } from '@/features/user/profile/useProfileUpdateSubmit'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { useFieldValidation } from '@/composables/useFieldValidation'
import { isValidDisplayName } from '@/utils/validation'

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'refreshed'): void
}>()

const authStore = useAuthStore()
const toastStore = useToastStore()
const router = useRouter()
const { t } = useI18n()
const { confirm } = useConfirm()
const { useDeleteAccount, useMyAgents, useClaimAgent, useSuspendMyAgent, useUpdateMyProfile } = useUser()
const { mutateAsync: updateProfileMutateAsync } = useUpdateMyProfile()
const { mutateAsync: deleteAccount, isPending: isDeleting } = useDeleteAccount()
const {
  data: agentsData,
  isLoading: isAgentsLoading,
  isFetching: isAgentsFetching,
  isError: isAgentsError,
  refetch: refetchAgents,
} = useMyAgents()
const { mutateAsync: claimAgent, isPending: isClaiming } = useClaimAgent()
const { mutateAsync: suspendMyAgent } = useSuspendMyAgent()

const isEmailVerified = computed(() => {
  const value: unknown = authStore.user?.isEmailVerified
  return value === true || value === 'Y' || value === 'true' || value === 1
})

const form = reactive({
  displayName: authStore.user?.displayName || '',
})

const isDeletingAccount = computed(() => Boolean(isDeleting.value))

const {
  selectedFile,
  removeProfileImage,
  profileImageError,
  profileImageDisplayUrl,
  handleFileChange,
  clearProfileImage
} = useProfileImageEditor({
  profileImageUrl: () => authStore.user?.profileImageUrl,
  onFileSizeExceeded: () => toastStore.addToast(t('common.messages.fileSizeExceeded'), 'warning'),
  onProcessFailed: () => toastStore.addToast(t('common.messages.processImageFailed'), 'error')
})
const profileValidation = useFieldValidation<'displayName'>({
  validators: {
    displayName: (values) => isValidDisplayName(String(values.displayName ?? ''))
      ? ''
      : t('auth.validation.displayNameLength'),
  },
  fieldIds: { displayName: 'profile-display-name' },
})
const profileValidationValues = computed(() => ({ displayName: form.displayName }))

const profileImageChangeCost = computed(() => authStore.user?.profileImageChangeCost ?? 1000)
const profileImageChangeFreeAvailable = computed(() => authStore.user?.profileImageChangeFreeAvailable !== false)
const profileImageCostText = computed(() => {
  if (profileImageChangeFreeAvailable.value) return t('user.profile.profileImageFirstFree')
  return t('user.profile.profileImageCost', {
    cost: profileImageChangeCost.value,
    points: authStore.user?.points ?? 0,
  })
})
const canRemoveProfileImage = computed(() => Boolean(profileImageDisplayUrl.value))

const {
  loading,
  errors,
  updateProfile
} = useProfileUpdateSubmit({
  selectedFile,
  removeProfileImage,
  getDisplayName: () => form.displayName,
  updateProfile: updateProfileMutateAsync,
  refreshUser: authStore.fetchUser,
  addToast: (message, type) => toastStore.addToast(message, type),
  t,
  confirm: (message) => confirm(message),
  getProfileImageChangeCost: () => profileImageChangeCost.value,
  isProfileImageChangeFree: () => profileImageChangeFreeAvailable.value,
  getCurrentPoints: () => authStore.user?.points ?? 0,
  authSession: authStore,
  onRefreshed: () => emit('refreshed'),
  onClose: () => emit('close')
})

async function handleProfileSubmit() {
  if (!profileValidation.validateAll(profileValidationValues.value)) return
  await updateProfile()
}

const {
  agentToken,
  agentError,
  processingAgentId,
  processingAction,
  activeAgent,
  isAgentListPending,
  handleClaimAgent,
  handleSuspendAgent
} = useMyAgentActions({
  agentsData,
  isAgentsLoading,
  isAgentsFetching,
  isClaiming,
  isEmailVerified,
  claimAgent,
  suspendMyAgent,
  confirm,
  addToast: (message, type) => toastStore.addToast(message, type),
  t,
  onRefreshed: () => emit('refreshed')
})

const {
  showDeleteModal,
  deletePassword,
  deleteError,
  handleDeleteAccount
} = useAccountDeletion({
  deleteAccount,
  logout: authStore.logout,
  pushHome: () => router.push('/'),
  t
})
</script>
