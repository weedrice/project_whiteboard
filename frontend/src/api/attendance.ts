import api from '@/api'
import type { ApiResponse } from '@/types'
import type { AxiosRequestConfig } from 'axios'

export interface AttendanceCheckInResponse {
    attendanceDate: string
    streakCount: number
    checkedIn: boolean
    alreadyCheckedIn: boolean
    earnedPoints: number
}

export interface AttendanceDay {
    attendanceDate: string
    streakCount: number
}

export interface AttendanceMonthResponse {
    month: string
    today: string
    checkedInToday: boolean
    currentStreakCount: number
    days: AttendanceDay[]
}

export const attendanceApi = {
    checkIn(config?: AxiosRequestConfig) {
        return config
            ? api.post<ApiResponse<AttendanceCheckInResponse>>('/attendance/check-in', undefined, config)
            : api.post<ApiResponse<AttendanceCheckInResponse>>('/attendance/check-in')
    },
    getMyAttendance(month?: string, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<AttendanceMonthResponse>>('/attendance/me', {
            ...config,
            params: {
                ...config?.params,
                ...(month ? { month } : {}),
            },
        })
    },
}
