import api from './index'

export interface Tag {
    name: string;
    count: number;
}

export const tagApi = {
    // Get tags
    getTags: () => api.get('/tags'),
}
