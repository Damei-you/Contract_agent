import axios, { AxiosError } from 'axios'
import { friendlyHttpMessage } from '../utils/errors'

/**
 * HTTP 基础层（统一 axios 实例 + 全局错误归一化）
 *
 * 设计目标：
 * - 页面层只关心“调用哪个 API 函数”和“展示什么”，不关心 axios 的细枝末节
 * - 所有请求统一走相对路径 `/api`，并通过 Vite 代理转发到后端（避免 CORS）
 * - 所有错误统一变成同一种形状（NormalizedHttpError），页面只需显示 message/data
 *
 * 调用链：
 * pages/*.vue -> api/contracts.ts -> 这里的 http 实例 -> (baseURL=/api) -> Vite proxy -> 后端
 *
 * 关键约定：
 * - baseURL: '/api'（不要写死 localhost:8088，方便未来切环境）
 * - timeout: 30s（与 docs/API_REFERENCE.md 的建议一致）
 *
 * 注意：
 * - 这里的拦截器会把 axios 原生错误转换为 NormalizedHttpError 并 reject
 * - 页面 catch(e) 时拿到的是 NormalizedHttpError，而不是 AxiosError
 *
 * 页面层建议用法（伪代码）：
 * - try { await apiCall() } catch (e) { const err = e as NormalizedHttpError; 展示 err.message / err.data }
 */
export const http = axios.create({
  baseURL: '/api',
  timeout: 30_000,
})

/**
 * 归一化后的错误结构（供页面层直接展示）
 *
 * - status: HTTP 状态码（400/404/409/500...）
 * - message: 已映射成“用户可理解”的中文提示（见 utils/errors.ts）
 * - data: 后端返回的错误体（如果有），用于联调时辅助定位
 * - raw: 原始 AxiosError（用于必要时查看更多细节，例如 config/stack）
 */
export interface NormalizedHttpError {
  status?: number
  message: string
  data?: unknown
  raw: AxiosError
}

/**
 * 响应拦截器
 *
 * - 成功：原样返回 response
 * - 失败：转成 NormalizedHttpError，保证页面层的错误处理逻辑一致
 */
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
