<script setup lang="ts">
import BaseButton from '@/components/common/ui/BaseButton.vue'
import type { PostFormPoll } from '@/utils/postForm'

const props = defineProps<{
  modelValue: PostFormPoll | null
  mode?: 'create' | 'edit'
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: PostFormPoll | null): void
}>()

function update(value: PostFormPoll) {
  emit('update:modelValue', value)
}

function setQuestion(question: string) {
  if (!props.modelValue) return
  update({ ...props.modelValue, question })
}

function setOption(index: number, option: string) {
  if (!props.modelValue) return
  const options = [...props.modelValue.options]
  options[index] = option
  update({ ...props.modelValue, options })
}

function addOption() {
  if (!props.modelValue || props.modelValue.options.length >= 10) return
  update({ ...props.modelValue, options: [...props.modelValue.options, ''] })
}

function removeOption(index: number) {
  if (!props.modelValue || props.modelValue.options.length <= 2) return
  update({
    ...props.modelValue,
    options: props.modelValue.options.filter((_, optionIndex) => optionIndex !== index),
  })
}

function toggle(field: 'multipleChoiceEnabled' | 'anonymousEnabled', checked: boolean) {
  if (!props.modelValue) return
  update({ ...props.modelValue, [field]: checked })
}

function setClosesAt(closesAt: string) {
  if (!props.modelValue) return
  update({ ...props.modelValue, closesAt: closesAt || null })
}
</script>

<template>
  <section v-if="modelValue" class="mt-5 rounded-[var(--nv-radius-xl)] border border-[var(--nv-line)] bg-[var(--nv-elevated)] p-4">
    <div class="flex items-center justify-between gap-3">
      <h2 class="text-sm font-semibold nv-title">{{ $t('board.writePost.poll.title') }}</h2>
      <BaseButton type="button" variant="secondary" size="sm" @click="emit('update:modelValue', null)">
        {{ $t('board.writePost.poll.remove') }}
      </BaseButton>
    </div>

    <label class="mt-4 block">
      <span class="mb-1 block text-xs font-medium uppercase tracking-[0.18em] text-[var(--nv-muted)]">
        {{ $t('board.writePost.poll.question') }}
      </span>
      <input
        data-testid="poll-question"
        :value="modelValue.question"
        type="text"
        class="form-input w-full"
        :placeholder="$t('board.writePost.poll.questionPlaceholder')"
        :disabled="mode === 'edit'"
        @input="setQuestion(($event.target as HTMLInputElement).value)"
      >
    </label>

    <div class="mt-4 space-y-2">
      <label
        v-for="(option, index) in modelValue.options"
        :key="index"
        class="grid gap-2 sm:grid-cols-[1fr_auto]"
      >
        <span class="sr-only">{{ $t('board.writePost.poll.option', { index: index + 1 }) }}</span>
        <input
          :data-testid="`poll-option-${index}`"
          :value="option"
          type="text"
          class="form-input w-full"
          :placeholder="$t('board.writePost.poll.optionPlaceholder')"
          :disabled="mode === 'edit'"
          @input="setOption(index, ($event.target as HTMLInputElement).value)"
        >
        <BaseButton
          type="button"
          variant="secondary"
          size="sm"
          :disabled="mode === 'edit' || modelValue.options.length <= 2"
          @click="removeOption(index)"
        >
          {{ $t('common.delete') }}
        </BaseButton>
      </label>
    </div>

    <div class="mt-3">
      <BaseButton
        type="button"
        variant="secondary"
        size="sm"
        :disabled="mode === 'edit' || modelValue.options.length >= 10"
        @click="addOption"
      >
        {{ $t('board.writePost.poll.addOption') }}
      </BaseButton>
    </div>

    <div class="mt-4 grid gap-3 sm:grid-cols-2">
      <label class="flex items-center gap-2 text-sm nv-title">
        <input
          data-testid="poll-multiple"
          type="checkbox"
          :checked="modelValue.multipleChoiceEnabled"
          :disabled="mode === 'edit'"
          @change="toggle('multipleChoiceEnabled', ($event.target as HTMLInputElement).checked)"
        >
        {{ $t('board.writePost.poll.multiple') }}
      </label>
      <label class="flex items-center gap-2 text-sm nv-title">
        <input
          type="checkbox"
          :checked="modelValue.anonymousEnabled"
          :disabled="mode === 'edit'"
          @change="toggle('anonymousEnabled', ($event.target as HTMLInputElement).checked)"
        >
        {{ $t('board.writePost.poll.anonymous') }}
      </label>
      <label class="sm:col-span-2">
        <span class="mb-1 block text-xs font-medium uppercase tracking-[0.18em] text-[var(--nv-muted)]">
          {{ $t('board.writePost.poll.closesAt') }}
        </span>
        <input
          :value="modelValue.closesAt ?? ''"
          type="datetime-local"
          class="form-input w-full"
          :disabled="mode === 'edit'"
          @input="setClosesAt(($event.target as HTMLInputElement).value)"
        >
      </label>
    </div>
  </section>
</template>
