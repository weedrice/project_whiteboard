export interface TableColumn<TKey extends string = string> {
  key: TKey
  label: string
  align?: 'left' | 'center' | 'right'
  width?: string
  sortable?: boolean
  hideBelow?: 'sm' | 'lg'
}

export function getTableResponsiveClass(hideBelow?: 'sm' | 'lg'): string {
  if (hideBelow === 'sm') return 'hidden sm:table-cell'
  if (hideBelow === 'lg') return 'hidden lg:table-cell'
  return ''
}

export type RowKeyResolver<TItem> = Extract<keyof TItem, string> | ((item: TItem, index: number) => string | number)
export type RowActionLabelResolver<TItem> = string | ((item: TItem, index: number) => string)
export type RowActivationEvent = 'row-click' | 'row-dblclick'
export type SortDirection = 'asc' | 'desc' | null

export function getTableAlignClass(align?: string): string {
  switch (align) {
    case 'center': return 'text-center'
    case 'right': return 'text-right'
    default: return 'text-left'
  }
}

export function getTableAlignButtonClass(align?: string): string {
  switch (align) {
    case 'center': return 'justify-center'
    case 'right': return 'justify-end'
    default: return 'justify-start'
  }
}

export function getTableAriaSort(
  column: TableColumn,
  currentSortKey: string | null,
  currentSortDirection: SortDirection,
): 'ascending' | 'descending' | 'none' | undefined {
  if (!column.sortable) {
    return undefined
  }

  if (currentSortKey !== column.key) {
    return 'none'
  }

  return currentSortDirection === 'asc' ? 'ascending' : 'descending'
}

export function getTableSortIndicator(
  column: TableColumn,
  currentSortKey: string | null,
  currentSortDirection: SortDirection,
): string {
  if (currentSortKey !== column.key) {
    return ''
  }

  return currentSortDirection === 'asc' ? '^' : 'v'
}

export function getTableSortButtonLabel(
  column: TableColumn,
  currentSortKey: string | null,
  currentSortDirection: SortDirection,
  sortOrderLabel: string,
): string {
  if (currentSortKey !== column.key) {
    return `${sortOrderLabel} ${column.label}`
  }

  const direction = getTableAriaSort(column, currentSortKey, currentSortDirection)
  return `${sortOrderLabel} ${column.label}: ${direction}`
}

export function getTableCellValue<TItem extends object>(item: TItem, key: string): unknown {
  return (item as Record<string, unknown>)[key]
}

type ResolveBaseTableRowKeyOptions<TItem extends object> = {
  item: TItem
  index: number
  rowKey?: RowKeyResolver<TItem>
  onIndexFallback?: (item: TItem) => void
}

export function resolveBaseTableRowKey<TItem extends object>({
  item,
  index,
  rowKey,
  onIndexFallback,
}: ResolveBaseTableRowKeyOptions<TItem>): string | number {
  if (typeof rowKey === 'function') {
    return rowKey(item, index)
  }

  const record = item as Record<string, unknown>

  if (typeof rowKey === 'string') {
    const resolvedKey = record[rowKey]
    if (typeof resolvedKey === 'string' || typeof resolvedKey === 'number') {
      return resolvedKey
    }
  }

  const defaultKey = record.id
    ?? record.postId
    ?? record.key
    ?? record.userId
    ?? record.reportId
    ?? record.ipAddress
    ?? record.adminId
    ?? record.errorLogId
  if (typeof defaultKey === 'string' || typeof defaultKey === 'number') {
    return defaultKey
  }

  onIndexFallback?.(item)
  return index
}

export function getBaseTableRowActionLabel<TItem extends object>(
  item: TItem,
  index: number,
  interactiveRows: boolean,
  rowActionLabel?: RowActionLabelResolver<TItem>,
): string | undefined {
  if (!interactiveRows) {
    return undefined
  }

  if (typeof rowActionLabel === 'function') {
    return rowActionLabel(item, index)
  }

  return rowActionLabel
}
