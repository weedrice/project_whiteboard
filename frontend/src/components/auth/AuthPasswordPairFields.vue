<script setup lang="ts">
import { Lock } from 'lucide-vue-next'
import BaseInput from '@/components/common/ui/BaseInput.vue'

withDefaults(defineProps<{
  password: string
  confirmPassword: string
  passwordId?: string
  confirmPasswordId?: string
  passwordName?: string
  confirmPasswordName?: string
  passwordLabel: string
  confirmPasswordLabel: string
  passwordPlaceholder?: string
  confirmPasswordPlaceholder?: string
  passwordError?: string
  confirmPasswordError?: string
  hideLabels?: boolean
}>(), {
  passwordId: 'password',
  confirmPasswordId: 'password-confirm',
  passwordName: 'password',
  confirmPasswordName: 'passwordConfirm',
  passwordPlaceholder: undefined,
  confirmPasswordPlaceholder: undefined,
  passwordError: '',
  confirmPasswordError: '',
  hideLabels: false,
})

const emit = defineEmits<{
  'update:password': [value: string]
  'update:confirmPassword': [value: string]
}>()
</script>

<template>
  <div>
    <BaseInput
      :id="passwordId"
      :model-value="password"
      :name="passwordName"
      type="password"
      autocomplete="new-password"
      required
      :placeholder="passwordPlaceholder"
      :label="passwordLabel"
      :error="passwordError"
      :hide-label="hideLabels"
      @update:model-value="emit('update:password', String($event))"
    >
      <template #prefix>
        <Lock class="h-5 w-5 nv-text-subtle" />
      </template>
    </BaseInput>
  </div>

  <div>
    <BaseInput
      :id="confirmPasswordId"
      :model-value="confirmPassword"
      :name="confirmPasswordName"
      type="password"
      autocomplete="new-password"
      required
      :placeholder="confirmPasswordPlaceholder"
      :label="confirmPasswordLabel"
      :error="confirmPasswordError"
      :hide-label="hideLabels"
      @update:model-value="emit('update:confirmPassword', String($event))"
    >
      <template #prefix>
        <Lock class="h-5 w-5 nv-text-subtle" />
      </template>
    </BaseInput>
  </div>
</template>
