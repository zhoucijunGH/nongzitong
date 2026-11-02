/**
 * 网络层封装
 *
 * 后端契约：统一返回 { code: 0, msg: "success", data: ... }
 * 除登录外的请求自动注入 Authorization: Bearer <token>（token 存 uni storage）
 * 收到 401（HTTP 状态码或业务 code）时清理 token 并跳转登录页
 *
 * 开发提示：后端跑在本机 8090，微信开发者工具需在
 * 「详情 - 本地设置」里勾选「不校验合法域名」才能请求通
 */
import { clearAuth, getToken } from './store'

/**
 * 后端地址（本地 / 云 两套部署，只切这里）：
 * - 开发（dev:mp-weixin）：本机后端 8090，配合开发者工具「不校验合法域名」
 * - 发布（build:mp-weixin）：走云服务域名
 *   优先取环境变量 VITE_BASE_URL（.env.production / .env.development），
 *   没配则用下面的 CLOUD_BASE_URL 常量——上线前改成自己的 https 域名
 */
const CLOUD_BASE_URL = 'https://api.example.com' // TODO: 云服务部署后改成你的域名

const envBase =
  (import.meta as unknown as { env?: Record<string, string | undefined> }).env?.VITE_BASE_URL || ''

export const BASE_URL: string =
  envBase ||
  (process.env.NODE_ENV === 'production' ? CLOUD_BASE_URL : 'http://127.0.0.1:8090')

/** 统一响应结构 */
export interface ApiResult<T = unknown> {
  code: number
  msg: string
  data: T
}

interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: Record<string, unknown>
  header?: Record<string, string>
  /** 是否显示 loading 遮罩 */
  loading?: boolean
}

/** 防止 401 时重复跳转登录 */
let redirecting = false

/** 清 token 并回登录页 */
function toLogin(): void {
  if (redirecting) return
  redirecting = true
  clearAuth()
  uni.reLaunch({
    url: '/pages/login/index',
    complete: () => {
      redirecting = false
    }
  })
}

/** 把后端返回的相对路径（如 /uploads/x.jpg）拼成可预览的完整地址 */
export function resolveUrl(path: string): string {
  if (!path) return ''
  if (/^https?:\/\//i.test(path)) return path
  return BASE_URL + path
}

/** 通用请求：resolve 出业务 data；业务 code !== 0 时 toast + reject */
export function request<T = unknown>(options: RequestOptions): Promise<T> {
  const { url, method = 'GET', data, header = {}, loading = false } = options
  return new Promise<T>((resolve, reject) => {
    const finalHeader: Record<string, string> = { 'Content-Type': 'application/json', ...header }
    const token = getToken()
    if (token) finalHeader.Authorization = `Bearer ${token}`
    if (loading) uni.showLoading({ title: '加载中', mask: true })
    uni.request({
      url: BASE_URL + url,
      method,
      data,
      header: finalHeader,
      success: (res) => {
        const body = res.data as ApiResult<T>
        if (res.statusCode === 401 || (body && body.code === 401)) {
          toLogin()
          reject(new Error('登录已过期，请重新登录'))
          return
        }
        if (body && body.code === 0) {
          resolve(body.data)
          return
        }
        const msg = (body && body.msg) || `请求失败(${res.statusCode})`
        uni.showToast({ title: msg, icon: 'none' })
        reject(new Error(msg))
      },
      fail: (err) => {
        uni.showToast({ title: '网络异常，请稍后重试', icon: 'none' })
        reject(err)
      },
      complete: () => {
        if (loading) uni.hideLoading()
      }
    })
  })
}

export function get<T = unknown>(url: string, data?: Record<string, unknown>): Promise<T> {
  return request<T>({ url, data })
}

export function post<T = unknown>(url: string, data?: Record<string, unknown>, loading = false): Promise<T> {
  return request<T>({ url, method: 'POST', data, loading })
}

export function put<T = unknown>(url: string, data?: Record<string, unknown>, loading = false): Promise<T> {
  return request<T>({ url, method: 'PUT', data, loading })
}

export function del<T = unknown>(url: string, loading = false): Promise<T> {
  return request<T>({ url, method: 'DELETE', loading })
}

/**
 * 磅单照片上传：POST /api/upload（uni.uploadFile，name="file"，header 带 token）
 * @param filePath chooseImage 拿到的本地临时路径
 * @returns 后端返回的相对路径，如 /uploads/xxx.jpg
 */
export function upload(filePath: string): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    const header: Record<string, string> = {}
    const token = getToken()
    if (token) header.Authorization = `Bearer ${token}`
    uni.uploadFile({
      url: BASE_URL + '/api/upload',
      filePath,
      name: 'file',
      header,
      success: (res) => {
        // uploadFile 的 data 是 JSON 字符串，需要手动解析
        let body: ApiResult<{ url: string }> | null = null
        try {
          body = JSON.parse(String(res.data))
        } catch (e) {
          body = null
        }
        if (res.statusCode === 401 || (body && body.code === 401)) {
          toLogin()
          reject(new Error('登录已过期，请重新登录'))
          return
        }
        if (body && body.code === 0 && body.data) {
          resolve(body.data.url)
          return
        }
        const msg = (body && body.msg) || '上传失败'
        uni.showToast({ title: msg, icon: 'none' })
        reject(new Error(msg))
      },
      fail: (err) => {
        uni.showToast({ title: '上传失败，请检查网络', icon: 'none' })
        reject(err)
      }
    })
  })
}
