import { mount } from '@vue/test-utils'
import { computed, defineComponent, h, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CategoryManager from '../CategoryManager.vue'
import type { Category } from '@/types'

const categories = ref<Category[]>([])
const isLoading = ref(false)
const newCategoryName = ref('')
const newCategoryRole = ref('USER')
const editingId = ref<number | null>(null)
const editingName = ref('')
const editingRole = ref('USER')
const handleAdd = vi.fn()
const handleDelete = vi.fn()
const startEdit = vi.fn()
const cancelEdit = vi.fn()
const saveEdit = vi.fn()
const fetchCategories = vi.fn()
const moveCategory = vi.fn()

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@/features/board/categories/useBoardCategoriesManager', () => ({
  useBoardCategoriesManager: () => ({
    categories,
    isLoading,
    newCategoryName,
    newCategoryRole,
    editingId,
    editingName,
    editingRole,
    defaultCategory: computed(() => categories.value.find((category) => category.isDefault) ?? null),
    draggableCategories: computed(() => categories.value.filter((category) => !category.isDefault)),
    fetchCategories,
    handleAdd,
    handleDelete,
    startEdit,
    cancelEdit,
    saveEdit,
    onDragStart: vi.fn(),
    onDrop: vi.fn(),
    moveCategory,
  }),
}))

const BaseButtonStub = defineComponent({
  name: 'BaseButton',
  props: {
    type: { type: String, default: 'button' },
  },
  emits: ['click'],
  setup(props, { attrs, emit, slots }) {
    return () =>
      h(
        'button',
        {
          ...attrs,
          type: props.type,
          onClick: () => emit('click'),
        },
        slots.default?.(),
      )
  },
})

const BaseInputStub = defineComponent({
  name: 'BaseInput',
  props: {
    modelValue: { type: String, default: '' },
    label: { type: String, default: '' },
    hideLabel: { type: Boolean, default: false },
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () =>
      h('div', [
        props.label ? h('label', { class: props.hideLabel ? 'sr-only' : '' }, props.label) : null,
        h('input', {
          value: props.modelValue,
          onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
        }),
      ])
  },
})

const BaseSelectStub = defineComponent({
  name: 'BaseSelect',
  props: {
    modelValue: { type: String, default: '' },
    label: { type: String, default: '' },
    hideLabel: { type: Boolean, default: false },
  },
  emits: ['update:modelValue'],
  setup(props, { emit, slots }) {
    return () =>
      h('div', [
        props.label ? h('label', { class: props.hideLabel ? 'sr-only' : '' }, props.label) : null,
        h(
          'select',
          {
            value: props.modelValue,
            onChange: (event: Event) => emit('update:modelValue', (event.target as HTMLSelectElement).value),
          },
          slots.default?.(),
        ),
      ])
  },
})

const makeCategory = (overrides: Partial<Category>): Category => ({
  categoryId: 1,
  name: 'General',
  sortOrder: 1,
  minWriteRole: 'USER',
  ...overrides,
})

const mountCategoryManager = () =>
  mount(CategoryManager, {
    props: {
      boardUrl: 'free',
    },
    global: {
      mocks: {
        $t: (key: string) => key,
      },
      stubs: {
        BaseButton: BaseButtonStub,
        BaseInput: BaseInputStub,
        BaseSelect: BaseSelectStub,
        Check: true,
        ChevronDown: true,
        ChevronUp: true,
        Edit2: true,
        GripVertical: true,
        Plus: true,
        Trash2: true,
        X: true,
      },
    },
  })

describe('CategoryManager', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    categories.value = [
      makeCategory({ categoryId: 1, name: 'General', isDefault: true }),
      makeCategory({ categoryId: 2, name: 'News', sortOrder: 2 }),
    ]
    isLoading.value = false
    editingId.value = null
  })

  it('adds accessible labels to icon-only category actions', () => {
    const wrapper = mountCategoryManager()

    expect(wrapper.find('button[aria-label="board.category.add"]').exists()).toBe(true)
    expect(wrapper.findAll('button[aria-label="board.category.edit"]')).toHaveLength(2)
    expect(wrapper.find('button[aria-label="board.category.delete"]').exists()).toBe(true)
  })

  it('keeps hidden labels on category name and role fields', () => {
    const wrapper = mountCategoryManager()
    const labels = wrapper.findAll('label')

    expect(labels.some((label) => label.text() === 'board.category.placeholder.new' && label.classes().includes('sr-only'))).toBe(true)
    expect(labels.some((label) => label.text() === 'common.role' && label.classes().includes('sr-only'))).toBe(true)
  })

  it('labels save and cancel actions while editing a category', () => {
    editingId.value = 2
    const wrapper = mountCategoryManager()

    expect(wrapper.find('button[aria-label="board.category.save"]').exists()).toBe(true)
    expect(wrapper.find('button[aria-label="board.category.cancel"]').exists()).toBe(true)
  })

  it('provides keyboard controls for category reordering', async () => {
    categories.value.push(makeCategory({ categoryId: 3, name: 'Events', sortOrder: 3 }))
    moveCategory.mockResolvedValueOnce(true)
    const wrapper = mountCategoryManager()

    const moveDown = wrapper.find('button[aria-label="common.moveDown"]')
    expect(moveDown.exists()).toBe(true)
    await moveDown.trigger('click')

    expect(moveCategory).toHaveBeenCalledWith(0, 1)
    expect(wrapper.get('[role="status"]').text()).toBe('common.moved')
  })
})
