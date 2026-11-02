/**
 * 展示格式化小工具
 */

/** 金额格式化：保留两位千分位，如 1234.5 -> "1,234.50" */
export function fmtMoney(value: number | null | undefined): string {
  const n = Number(value || 0)
  return n.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

/** 重量格式化：去掉多余的 0，如 12.50 -> "12.5"，12.00 -> "12" */
export function fmtWeight(value: number | null | undefined): string {
  const n = Number(value || 0)
  return String(Math.round(n * 100) / 100)
}

/** 数字输入清洗：只保留数字与小数点 */
export function digitsOnly(value: string): string {
  return String(value || '').replace(/[^\d.]/g, '')
}
