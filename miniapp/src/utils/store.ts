/**
 * 本地登录态存取（token / 当前用户）
 * token 与 user 均存于 uni storage，供请求封装统一读取
 */

const TOKEN_KEY = 'token'
const USER_KEY = 'user'

/** 登录用户信息，对应 /api/auth/login 返回的 data.user */
export interface User {
  id: number
  username: string
  nickname: string
  /** 角色：ADMIN / STAFF（后端可能返回小写，比较时做了兼容） */
  role: string
}

/** 是否已登录（有 token 即视为已登录，过期由服务端 401 兜底） */
export function hasToken(): boolean {
  return !!uni.getStorageSync(TOKEN_KEY)
}

export function getToken(): string {
  return (uni.getStorageSync(TOKEN_KEY) as string) || ''
}

/** 保存登录态：token + 用户信息 */
export function setAuth(token: string, user: User): void {
  uni.setStorageSync(TOKEN_KEY, token)
  uni.setStorageSync(USER_KEY, user)
}

export function getUser(): User | null {
  const user = uni.getStorageSync(USER_KEY)
  return (user as User) || null
}

/** 清除登录态（退出登录 / 401 时调用） */
export function clearAuth(): void {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(USER_KEY)
}

/** 当前用户是否管理员（果品档案的增删改需要 ADMIN） */
export function isAdmin(): boolean {
  const user = getUser()
  return !!user && String(user.role).toUpperCase() === 'ADMIN'
}
