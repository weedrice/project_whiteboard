import { describe, expect, it, vi } from 'vitest'
import {
  getBaseTableRowActionLabel,
  getTableAriaSort,
  getTableSortButtonLabel,
  getTableSortIndicator,
  resolveBaseTableRowKey,
  type TableColumn,
} from '../ui/baseTableModel'

const sortableColumn: TableColumn = {
  key: 'title',
  label: 'Title',
  sortable: true,
}

describe('baseTableModel', () => {
  it('builds sortable header metadata from the active sort state', () => {
    expect(getTableAriaSort(sortableColumn, 'title', 'asc')).toBe('ascending')
    expect(getTableAriaSort(sortableColumn, 'title', 'desc')).toBe('descending')
    expect(getTableAriaSort(sortableColumn, 'createdAt', 'desc')).toBe('none')
    expect(getTableSortIndicator(sortableColumn, 'title', 'asc')).toBe('^')
    expect(getTableSortIndicator(sortableColumn, 'title', 'desc')).toBe('v')
    expect(getTableSortIndicator(sortableColumn, 'createdAt', 'desc')).toBe('')
    expect(getTableSortButtonLabel(sortableColumn, 'title', 'asc', 'Sort by')).toBe('Sort by Title: ascending')
    expect(getTableSortButtonLabel(sortableColumn, 'createdAt', 'desc', 'Sort by')).toBe('Sort by Title')
  })

  it('resolves explicit, default and index fallback row keys', () => {
    expect(resolveBaseTableRowKey({
      item: { customId: 'custom-key' },
      index: 0,
      rowKey: 'customId',
    })).toBe('custom-key')
    expect(resolveBaseTableRowKey({
      item: { postId: 15 },
      index: 1,
    })).toBe(15)

    const onIndexFallback = vi.fn()
    expect(resolveBaseTableRowKey({
      item: { title: 'missing' },
      index: 2,
      onIndexFallback,
    })).toBe(2)
    expect(onIndexFallback).toHaveBeenCalledWith({ title: 'missing' })
  })

  it('returns row action labels only when rows are interactive', () => {
    expect(getBaseTableRowActionLabel({ title: 'A' }, 0, false, 'Open')).toBeUndefined()
    expect(getBaseTableRowActionLabel({ title: 'A' }, 0, true, 'Open')).toBe('Open')
    expect(getBaseTableRowActionLabel(
      { title: 'A' },
      3,
      true,
      (item, index) => `${item.title}:${index}`,
    )).toBe('A:3')
  })
})
