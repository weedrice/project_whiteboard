import type { AdminMessages } from '../types'

export const adminShopMessagesEn = {
  shop: {
    title: 'Shop management',
    description: 'Review source availability and suspend or resume new sales for shop items.',
    empty: 'No shop items match these filters.',
    filter: {
      title: 'Search items',
      search: 'Name',
      searchPlaceholder: 'Search item names',
      itemType: 'Item type',
      sourceStatus: 'Source status',
      saleStatus: 'Sales permission',
      enabled: 'Enabled',
      disabled: 'Disabled',
    },
    table: {
      id: 'ID',
      name: 'Item',
      type: 'Type',
      price: 'Price',
      sourceStatus: 'Source status',
      saleStatus: 'Sales status',
      modifiedAt: 'Modified',
      actions: 'Actions',
    },
    status: {
      active: 'Active',
      inactive: 'Inactive',
      onSale: 'On sale',
      suspended: 'Admin suspended',
      sourceInactive: 'Source inactive',
      retired: 'Retired',
    },
    actions: {
      suspend: 'Suspend sales',
      resume: 'Resume sales',
    },
    modal: {
      suspendTitle: 'Suspend item sales',
      resumeTitle: 'Resume item sales',
      description: 'Change the sales status for “{name}”. Existing owners keep their access.',
      reason: 'Reason',
      reasonPlaceholder: 'Enter the reason to record in the audit log.',
    },
    messages: {
      reasonRequired: 'Enter a reason.',
      reasonTooLong: 'The reason must be 500 characters or fewer.',
      suspended: 'Item sales have been suspended.',
      resumed: 'Item sales have been resumed.',
      updateFailed: 'Failed to change the sales status.',
    },
  },
} satisfies Pick<AdminMessages, 'shop'>
