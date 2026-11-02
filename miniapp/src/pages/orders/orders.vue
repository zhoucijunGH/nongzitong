<script setup lang="ts">
/**
 * 流水（tab）：开单记录分页列表
 * 筛选：起止日期 + 关键字 + 付款状态下拉
 * 下拉刷新 / 上拉加载更多；点条目进详情页看磅单照片
 */
import { ref, reactive } from 'vue'
import { onShow, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { get } from '../../utils/request'
import { fmtMoney, fmtWeight } from '../../utils/format'

interface Order {
  id: number
  orderNo?: string
  orderDate?: string
  farmerName?: string
  fruitName?: string
  spec?: string
  netWeight?: number
  amount?: number
  payStatus?: string
  source?: string
  payMethod?: string
}

interface OrderPage {
  total: number
  list: Order[]
}

const PAGE_SIZE = 20
const PAY_OPTIONS = ['全部', '已付', '未付']

const filters = reactive({
  dateFrom: '',
  dateTo: '',
  keyword: '',
  payStatus: '全部'
})
const payIndex = ref(0)

const list = ref<Order[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const finished = ref(false)

/** 拉取一页；reset=true 时从第一页重来 */
async function load(reset = false): Promise<void> {
  if (loading.value) return
  if (reset) {
    page.value = 1
    finished.value = false
  }
  if (finished.value) return
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      page: page.value,
      size: PAGE_SIZE,
      keyword: filters.keyword.trim()
    }
    if (filters.dateFrom) params.dateFrom = filters.dateFrom
    if (filters.dateTo) params.dateTo = filters.dateTo
    if (filters.payStatus !== '全部') params.payStatus = filters.payStatus === '已付' ? 'PAID' : 'UNPAID'
    const data = await get<OrderPage>('/api/orders', params)
    const rows = (data && data.list) || []
    total.value = (data && data.total) || 0
    list.value = reset ? rows : list.value.concat(rows)
    if (rows.length < PAGE_SIZE || list.value.length >= total.value) {
      finished.value = true
    } else {
      page.value += 1
    }
  } catch (e) {
    // 提示由封装统一处理
  } finally {
    loading.value = false
  }
}

function pickFrom(e: { detail: { value: string } }): void {
  filters.dateFrom = e.detail.value || ''
  if (filters.dateTo && filters.dateFrom > filters.dateTo) filters.dateTo = ''
  load(true)
}

function pickTo(e: { detail: { value: string } }): void {
  filters.dateTo = e.detail.value || ''
  load(true)
}

function clearDate(): void {
  filters.dateFrom = ''
  filters.dateTo = ''
  load(true)
}

function onPayChange(e: { detail: { value: number } }): void {
  payIndex.value = Number(e.detail.value)
  filters.payStatus = PAY_OPTIONS[payIndex.value]
  load(true)
}

function onKeyword(e: unknown): void {
  filters.keyword = (e as { detail: { value: string } }).detail.value
}

function search(): void {
  load(true)
}

function goDetail(id: number): void {
  uni.navigateTo({ url: '/pages/orders/detail?id=' + id })
}

function isPaid(status?: string): boolean {
  return status === 'PAID'
}

/** 付款状态显示：PAID/UNPAID → 已付/未付 */
function payLabel(status?: string): string {
  if (status === 'PAID') return '已付'
  if (status === 'UNPAID') return '未付'
  return status || '未付'
}

onShow(() => {
  // 每次回到 tab 都刷新第一页，保证新单立即可见
  load(true)
})

onPullDownRefresh(() => {
  load(true).finally(() => uni.stopPullDownRefresh())
})

onReachBottom(() => {
  load()
})
</script>

<template>
  <view class="page">
    <!-- 筛选区 -->
    <view class="filter">
      <view class="filter-row">
        <view class="date-box">
          <picker mode="date" :value="filters.dateFrom || '2026-01-01'" @change="pickFrom">
            <text :class="filters.dateFrom ? 'date-val' : 'ph'">
              {{ filters.dateFrom || '开始日期' }}
            </text>
          </picker>
          <text class="date-sep">至</text>
          <picker mode="date" :value="filters.dateTo || '2026-01-01'" @change="pickTo">
            <text :class="filters.dateTo ? 'date-val' : 'ph'">
              {{ filters.dateTo || '结束日期' }}
            </text>
          </picker>
          <text v-if="filters.dateFrom || filters.dateTo" class="date-clear" @tap="clearDate">
            清除
          </text>
        </view>
        <picker :range="PAY_OPTIONS" :value="payIndex" @change="onPayChange">
          <view class="pay-picker">
            <text>{{ filters.payStatus }}</text>
            <text class="arrow">▾</text>
          </view>
        </picker>
      </view>
      <view class="search-bar nopad">
        <input
          class="search-input"
          type="text"
          placeholder="搜索单号 / 果农 / 品类"
          placeholder-class="ph"
          :value="filters.keyword"
          confirm-type="search"
          @input="onKeyword"
          @confirm="search"
        />
        <text class="search-btn" @tap="search">搜索</text>
      </view>
    </view>

    <!-- 列表 -->
    <view v-if="list.length" class="list">
      <view v-for="item in list" :key="item.id" class="card order-card" @tap="goDetail(item.id)">
        <view class="order-head">
          <text class="order-no">{{ item.orderNo || '单号 ' + item.id }}</text>
          <text :class="['pill', isPaid(item.payStatus) ? 'pill-paid' : 'pill-unpaid']">
            {{ payLabel(item.payStatus) }}
          </text>
        </view>
        <view class="order-body">
          <view class="order-col">
            <text class="order-farmer">{{ item.farmerName || '—' }}</text>
            <text class="order-fruit">
              {{ item.fruitName || '—' }}{{ item.spec ? ' · ' + item.spec : '' }}
            </text>
            <text class="order-date text-grey">{{ item.orderDate || '' }}</text>
          </view>
          <view class="order-col right">
            <text class="order-amount">¥ {{ fmtMoney(item.amount) }}</text>
            <text class="order-weight text-grey">净重 {{ fmtWeight(item.netWeight) }} 斤</text>
          </view>
        </view>
      </view>
      <view class="list-end">
        {{ finished ? '没有更多了' : loading ? '加载中...' : '' }}
      </view>
    </view>
    <view v-else-if="!loading" class="empty">暂无开单记录</view>
  </view>
</template>

<style scoped>
.page {
  padding-bottom: 40rpx;
}

.filter {
  background: #ffffff;
  padding: 20rpx 24rpx;
}

.filter-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}

.date-box {
  display: flex;
  align-items: center;
  font-size: 26rpx;
}

.date-val {
  color: #303133;
}

.date-sep {
  color: #909399;
  margin: 0 12rpx;
}

.date-clear {
  margin-left: 16rpx;
  color: #07c160;
  font-size: 24rpx;
}

.pay-picker {
  display: flex;
  align-items: center;
  background: #f2f3f5;
  border-radius: 999rpx;
  padding: 8rpx 22rpx;
  font-size: 26rpx;
  color: #303133;
}

.arrow {
  margin-left: 8rpx;
  color: #909399;
  font-size: 22rpx;
}

.nopad {
  padding: 0;
  background: transparent;
}

.ph {
  color: #a8abb2;
}

/* 列表 */
.order-card {
  padding: 24rpx;
}

.order-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14rpx;
}

.order-no {
  font-size: 26rpx;
  font-weight: 600;
  color: #1f2329;
}

.order-body {
  display: flex;
  justify-content: space-between;
}

.order-col {
  display: flex;
  flex-direction: column;
}

.order-col.right {
  align-items: flex-end;
}

.order-farmer {
  font-size: 30rpx;
  font-weight: 600;
}

.order-fruit,
.order-date {
  font-size: 24rpx;
  margin-top: 6rpx;
}

.order-amount {
  font-size: 34rpx;
  font-weight: 700;
  color: #f56c6c;
}

.order-weight {
  font-size: 24rpx;
  margin-top: 6rpx;
}

.list-end {
  text-align: center;
  color: #a8abb2;
  font-size: 24rpx;
  padding: 24rpx 0;
}
</style>
