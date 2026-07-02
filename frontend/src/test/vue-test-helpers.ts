import { flushPromises, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'

export const identityT = (key: string, params?: Record<string, unknown>) => {
  if (params?.count !== undefined) {
    return `${key}:${params.count}`
  }
  return key
}

export const BaseModalStub = defineComponent({
  name: 'BaseModalStub',
  props: {
    isOpen: Boolean,
    title: String,
  },
  setup(props, { slots }) {
    return () => props.isOpen
      ? h('div', { 'data-test': 'modal' }, [
        h('h1', props.title),
        slots.default?.(),
        slots.footer?.(),
      ])
      : null
  },
})

export const BaseBadgeStub = defineComponent({
  name: 'BaseBadgeStub',
  setup(_, { slots }) {
    return () => h('span', slots.default?.())
  },
})

export const BaseButtonStub = defineComponent({
  name: 'BaseButtonStub',
  props: {
    disabled: { type: Boolean, default: false },
    loading: { type: Boolean, default: false },
    type: { type: String, default: 'button' },
  },
  emits: ['click'],
  setup(props, { slots, emit }) {
    return () => h('button', {
      type: props.type,
      disabled: props.disabled,
      'data-loading': String(props.loading),
      onClick: () => emit('click'),
    }, slots.default?.())
  },
})

export const PassThroughStub = defineComponent({
  name: 'PassThroughStub',
  setup(_, { slots }) {
    return () => h('div', slots.default?.())
  },
})

export const BaseInputStub = defineComponent({
  name: 'BaseInputStub',
  props: {
    id: String,
    modelValue: [String, Number],
    label: String,
    type: String,
    min: String,
    placeholder: String,
    disabled: Boolean,
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () => h('label', [
      props.label,
      h('input', {
        id: props.id,
        type: props.type,
        min: props.min,
        value: props.modelValue,
        placeholder: props.placeholder,
        disabled: props.disabled,
        onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
      }),
    ])
  },
})

export const BaseSelectStub = defineComponent({
  name: 'BaseSelectStub',
  props: {
    id: String,
    modelValue: [String, Number],
    label: String,
    disabled: Boolean,
  },
  emits: ['update:modelValue'],
  setup(props, { emit, slots }) {
    return () => h('label', [
      props.label,
      h('select', {
        id: props.id,
        value: props.modelValue,
        disabled: props.disabled,
        onChange: (event: Event) => emit('update:modelValue', (event.target as HTMLSelectElement).value),
      }, slots.default?.()),
    ])
  },
})

export const BaseTextareaStub = defineComponent({
  name: 'BaseTextareaStub',
  props: {
    id: String,
    modelValue: String,
    label: String,
    rows: [String, Number],
    placeholder: String,
    disabled: Boolean,
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () => h('label', [
      props.label,
      h('textarea', {
        id: props.id,
        value: props.modelValue,
        rows: props.rows,
        placeholder: props.placeholder,
        disabled: props.disabled,
        onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLTextAreaElement).value),
      }),
    ])
  },
})

export function createPaginationStub(nextPage = 2) {
  return defineComponent({
    name: 'PaginationStub',
    props: {
      currentPage: Number,
      totalPages: Number,
    },
    emits: ['page-change'],
    template: `<button data-test="pagination" type="button" @click="$emit('page-change', ${nextPage})">{{ currentPage }}/{{ totalPages }}</button>`,
  })
}

export function createPrevNextPaginationStub() {
  return defineComponent({
    name: 'PrevNextPaginationStub',
    props: {
      currentPage: Number,
      totalPages: Number,
    },
    emits: ['page-change'],
    template: `
      <div data-test="pagination">
        <button type="button" data-test="prev-page" :disabled="currentPage === 0" @click="$emit('page-change', currentPage - 1)">prev</button>
        <button type="button" data-test="next-page" :disabled="currentPage >= totalPages - 1" @click="$emit('page-change', currentPage + 1)">next</button>
      </div>
    `,
  })
}

type ButtonSearchRoot = {
  findAll: (selector: string) => DOMWrapper<Element>[]
}

export function findButtonByText(root: ButtonSearchRoot, text: string) {
  return root.findAll('button').find((button) => button.text() === text)
}

export function getButtonByText(root: ButtonSearchRoot, text: string) {
  const button = findButtonByText(root, text)

  if (!button) {
    throw new Error(`Button not found: ${text}`)
  }

  return button
}

export function getButtonByAriaLabel(root: ButtonSearchRoot, label: string) {
  const button = root.findAll('button').find((candidate) => candidate.attributes('aria-label') === label)

  if (!button) {
    throw new Error(`Button not found by aria-label: ${label}`)
  }

  return button
}

export function getExposedVm<TExposed extends object>(wrapper: VueWrapper): TExposed {
  return wrapper.vm as unknown as TExposed
}

export async function flushAll() {
  await flushPromises()
  await nextTick()
}
