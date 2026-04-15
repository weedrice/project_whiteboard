export interface EmoticonImage {
    imageId: number
    emoticonId: number
    imageUrl: string
    sortOrder: number
}

export interface EmoticonMaster {
    emoticonId: number
    name: string
    thumbnailUrl?: string
    tags: string[]
    isActive: boolean
    creatorId?: number
    creatorName?: string
    purchaseCount?: number
    images: EmoticonImage[]
    createdAt: string
    modifiedAt: string
}

export interface EmoticonCreateRequest {
    name: string
    thumbnailFileId?: number
    tags?: string[]
    imageFileIds?: number[]
}

export interface EmoticonUpdateRequest {
    name?: string
    thumbnailFileId?: number
    tags?: string[]
}

export interface EmoticonSearchParams {
    page?: number
    size?: number
    keyword?: string
    searchType?: 'ALL' | 'NAME' | 'CREATOR' | 'TAG'
    sortBy?: 'latest' | 'popular'
}

export interface EmoticonPurchaseStatus {
    purchased: boolean
    price: number
}
