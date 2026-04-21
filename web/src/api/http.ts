import axios, { AxiosError } from 'axios'
import { friendlyHttpMessage } from '../utils/errors'

/**
 * 统一 axios 实例。
 * - baseURL 走相对路径 `/api`，由 Vite 代理到 http://localhost:8088
 * - 超时 30s，与 API_REFERENCE 建议一致
 */
export const http = axios.create({
  baseURL: '/api',
  timeout: 30_000,
  headers: { 'Content-Type': 'application/json' },
})

export interface NormalizedHttpError {
  status?: number
  message: string
  data?: unknown
  raw: AxiosError
}

http.interceptors.response.use(
  (res) => res,
  (error: AxiosError) => {
    const status = error.response?.status
    const normalized: NormalizedHttpError = {
      status,
      message: friendlyHttpMessage(status),
      data: error.response?.data,
      raw: error,
    }
    return Promise.reject(normalized)
  },
)
