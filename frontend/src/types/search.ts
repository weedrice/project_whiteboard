export interface PopularKeyword {
    keyword: string
    count: number
}

export interface SearchParams {
    q?: string
    keyword?: string
    page?: number
    size?: number
    type?: string
    searchType?: string
    sort?: string
    boardUrl?: string
}
