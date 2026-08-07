<script setup lang="ts">
import { computed, onScopeDispose, ref, shallowRef, useAttrs, watch, type ComponentPublicInstance } from 'vue'
import { useI18n } from 'vue-i18n'
import SandboxedHtmlFrame from '@/components/common/SandboxedHtmlFrame.vue'
import SanitizedHtmlView from '@/components/common/SanitizedHtmlView.vue'
import { renderPostContentHtml, type CodeBlockHighlighter } from '@/utils/postContentHtml'
import { decodeSandboxedPostHtml, expandSandboxedPostHtml, requiresSandboxedPostHtml } from '@/utils/postHtmlSandbox'

defineOptions({
  inheritAttrs: false,
})

const props = defineProps<{
  content: string | null | undefined
  sandboxTitle?: string
  sandboxMinHeight?: number
  sandboxLoading?: 'eager' | 'lazy'
  codeBlockHighlighterLoader?: () => Promise<CodeBlockHighlighter>
}>()

const attrs = useAttrs()
const { t } = useI18n()
const element = ref<HTMLElement | null>(null)
const sandboxWrapper = ref<HTMLElement | null>(null)
const codeBlockHighlighter = shallowRef<CodeBlockHighlighter | null>(null)
const decodedSandboxHtml = computed(() => decodeSandboxedPostHtml(props.content))
const expandedSandboxHtml = computed(() => expandSandboxedPostHtml(props.content))
const sandboxHtml = computed(() => (
  decodedSandboxHtml.value
  ?? expandedSandboxHtml.value
  ?? (requiresSandboxedPostHtml(props.content) ? props.content ?? '' : null)
))
const shouldUseSandbox = computed(() => sandboxHtml.value != null)
const hasCodeBlock = computed(() => /<pre[\s>]/i.test(props.content ?? ''))
const sanitizedHtml = computed(() => renderPostContentHtml(props.content, {
  copy: t('board.writePost.codeBlock.copy'),
  copyAriaLabel: t('board.writePost.codeBlock.copyAriaLabel'),
}, codeBlockHighlighter.value))

type SanitizedHtmlViewExpose = ComponentPublicInstance & {
  element?: HTMLElement | { value: HTMLElement | null } | null
}

function assignSanitizedRef(value: Element | ComponentPublicInstance | null) {
  const exposedElement = (value as SanitizedHtmlViewExpose | null)?.element
  if (exposedElement && typeof exposedElement === 'object' && 'value' in exposedElement) {
    element.value = exposedElement.value
    return
  }
  element.value = exposedElement instanceof HTMLElement ? exposedElement : null
}

function assignSandboxRef(value: Element | ComponentPublicInstance | null) {
  sandboxWrapper.value = value instanceof HTMLElement ? value : null
  element.value = sandboxWrapper.value
}

let codeBlockLoadRevision = 0

watch(hasCodeBlock, async (hasCode) => {
  const revision = ++codeBlockLoadRevision
  if (!hasCode || codeBlockHighlighter.value) return

  try {
    const highlighter = props.codeBlockHighlighterLoader
      ? await props.codeBlockHighlighterLoader()
      : (await import('@/utils/codeHighlighting')).highlightCodeBlocksInDocument
    if (revision === codeBlockLoadRevision && hasCodeBlock.value) {
      codeBlockHighlighter.value = highlighter
    }
  } catch {
    // Plain code markup is already rendered; highlighting is progressive enhancement.
  }
}, { immediate: true })

onScopeDispose(() => {
  codeBlockLoadRevision += 1
})

defineExpose({
  element,
})
</script>

<template>
  <div
    v-if="shouldUseSandbox"
    :ref="assignSandboxRef"
    v-bind="attrs"
  >
    <SandboxedHtmlFrame
      :html="sandboxHtml ?? ''"
      :title="sandboxTitle ?? t('common.content')"
      :min-height="sandboxMinHeight"
      :loading="sandboxLoading"
    />
  </div>
  <SanitizedHtmlView
    v-else
    :ref="assignSanitizedRef"
    tag="div"
    v-bind="attrs"
    :html="sanitizedHtml"
  />
</template>
