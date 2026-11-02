<script setup lang="ts">
/**
 * 日结报表：选日期 -> GET /api/reports/daily?date=YYYY-MM-DD
 * 汇总卡 + 按品类表 + 按果农表
 * 说明：byFruit / byFarmer 的具体字段契约未细化，这里做了兼容取值
 */
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { get } from '../../utils/request'
import { fmtMoney, fmtWeight } from '../../utils/format'

interface FruitRow {
  [key: string]: unknown
}

interface DailyData {
  date: string
  summary: {
    amount: number
    count: number
    netWeight: number
    unpaidAmount: number
  }
  byFruit: FruitRow[]
  byFarmer: FruitRow[]
}

const today = new Date()
const dateStr = (d: Date): string =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

const date = ref(dateStr(today))
const data = ref<DailyData | null>(null)
const loading = ref(false)

/** 兼容取字符串字段（不同后端命名可能为 name/fruitName/farmerName 等） */
function pickText(item: FruitRow, keys: string[]): string {
  for (const k of keys) {
    const v = item[k]
    if (v !== undefined && v !== null && v !== '') return String(v)
  }
  return '—'
}

/** 兼容取数字字段 */
function pickNum(item: FruitRow, keys: string[]): number {
  for (const k of keys) {
    const v = item[k]
    if (v !== undefined && v !== null && v !== '') return Number(v) || 0
  }
  return 0
}

const byFruit = computed(() => (data.value && data.value.byFruit) || [])
const byFarmer = computed(() => (data.value && data.value.byFarmer) || [])

async function load(): Promise<void> {
  loading.value = true
  try {
    data.value = await get<DailyData>('/api/reports/daily', { date: date.value })
  } catch (e) {
    data.value = null
  } finally {
    loading.value = false
  }
}

function onDateChange(e: { detail: { value: string } }): void {
  date.value = e.detail.value
  load()
}

/** 快捷切换：今天 / 昨天 */
function shiftDate(days: number): void {
  const d = new Date(date.value + 'T00:00:00')
  d.setDate(d.getDate() + days)
  date.value = dateStr(d)
  load()
}

onLoad(() => {
  load()
})
</script>

<template>
  <view class="page">
    <!-- 日期切换 -->
    <view class="date-bar">
      <text class="date-btn" @tap="shiftDate(-1)">‹ 前一天</text>
      <picker mode="date" :value="date" @change="onDateChange">
        <text class="date-current">{{ date }}</text>
      </picker>
      <text class="date-btn" @tap="shiftDate(1)">后一天 ›</text>
    </view>

    <!-- 汇总卡 -->
    <view class="summary" v-if="data">
      <view class="sum-main">
        <text class="sum-label">当日收购额（元）</text>
        <text class="sum-value">{{ fmtMoney(data.summary?.amount) }}</text>
      </view>
      <view class="sum-grid">
        <view class="sum-item">
          <text class="sum-num">{{ data.summary?.count || 0 }}</text>
          <text class="sum-cap">单数</text>
        </view>
        <view class="sum-item">
          <text class="sum-num">{{ fmtWeight(data.summary?.netWeight) }}</text>
          <text class="sum-cap">净重（斤）</text>
        </view>
        <view class="sum-item">
          <text class="sum-num warn">{{ fmtMoney(data.summary?.unpaidAmount) }}</text>
          <text class="sum-cap">未付（元）</text>
        </view>
      </view>
    </view>

    <!-- 按品类 -->
    <view class="card">
      <view class="card-title">按品类</view>
      <view class="thead">
        <text class="th flex2">品类</text>
        <text class="th">单数</text>
        <text class="th">净重</text>
        <text class="th right">金额</text>
      </view>
      <view v-if="byFruit.length">
        <view v-for="(row, i) in byFruit" :key="i" class="tr">
          <text class="td flex2">{{ pickText(row, ['name', 'fruitName', 'fruit']) }}</text>
          <text class="td">{{ pickNum(row, ['count', 'orderCount', 'orders']) }}</text>
          <text class="td">{{ fmtWeight(pickNum(row, ['netWeight', 'weight'])) }}</text>
          <text class="td right price">¥{{ fmtMoney(pickNum(row, ['amount', 'total', 'money'])) }}</text>
        </view>
      </view>
      <view v-else class="empty">当日无数据</view>
    </view>

    <!-- 按果农 -->
    <view class="card">
      <view class="card-title">按果农</view>
      <view class="thead">
        <text class="th flex2">果农</text>
        <text class="th">单数</text>
        <text class="th">净重</text>
        <text class="th right">金额</text>
      </view>
      <view v-if="byFarmer.length">
        <view v-for="(row, i) in byFarmer" :key="i" class="tr">
          <text class="td flex2">{{ pickText(row, ['farmerName', 'name', 'farmer']) }}</text>
          <text class="td">{{ pickNum(row, ['count', 'orderCount', 'orders']) }}</text>
          <text class="td">{{ fmtWeight(pickNum(row, ['netWeight', 'weight'])) }}</text>
          <text class="td right price">¥{{ fmtMoney(pickNum(row, ['amount', 'total', 'money'])) }}</text>
        </view>
      </view>
      <view v-else class="empty">当日无数据</view>
    </view>
  </view>
</template>

<style scoped>
.page {
  padding-bottom: 40rpx;
}

.date-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  padding: 24rpx 32rpx;
}

.date-current {
  font-size: 34rpx;
  font-weight: 700;
  color: #07c160;
}

.date-btn {
  font-size: 26rpx;
  color: #606266;
}

.summary {
  margin: 20rpx 24rpx;
  background: linear-gradient(135deg, #07c160, #05a24f);
  border-radius: 16rpx;
  padding: 32rpx 28rpx;
  color: #ffffff;
}

.sum-label {
  font-size: 24rpx;
  opacity: 0.9;
}

.sum-value {
  display: block;
  font-size: 60rpx;
  font-weight: 700;
  margin-top: 8rpx;
}

.sum-grid {
  display: flex;
  margin-top: 24rpx;
  border-top: 1rpx solid rgba(255, 255, 255, 0.25);
  padding-top: 20rpx;
}

.sum-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.sum-num {
  font-size: 34rpx;
  font-weight: 700;
}

.sum-num.warn {
  color: #ffe1e1;
}

.sum-cap {
  font-size: 22rpx;
  opacity: 0.9;
  margin-top: 6rpx;
}

/* 表格 */
.thead {
  display: flex;
  background: #f7f8fa;
  border-radius: 8rpx;
  padding: 14rpx 12rpx;
}

.th {
  flex: 1;
  font-size: 24rpx;
  color: #909399;
  text-align: center;
}

.tr {
  display: flex;
  padding: 20rpx 12rpx;
  border-bottom: 1rpx solid #f5f6f7;
  font-size: 26rpx;
}

.tr:last-child {
  border-bottom: none;
}

.td {
  flex: 1;
  text-align: center;
  color: #303133;
}

.flex2 {
  flex: 2;
  text-align: left;
}

.right {
  text-align: right;
}

.price {
  color: #f56c6c;
  font-weight: 600;
}
</style>
