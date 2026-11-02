<script setup lang="ts">
/**
 * 工作台（tab 页）
 * - KPI 卡：今日收购额 / 今日单数 / 待付欠款 / 待付单数（GET /api/dashboard）
 * - 近 7 天趋势：不用 ECharts，用 view 柱状条按百分比高度自绘
 * - 「今日日结」入口跳日结报表页
 */
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { get } from '../../utils/request'
import { fmtMoney, fmtWeight } from '../../utils/format'
import { hasToken, getUser } from '../../utils/store'

interface TrendItem {
  date: string
  amount: number
}

interface DashboardData {
  todayAmount: number
  todayCount: number
  todayNetWeight: number
  unpaidAmount: number
  unpaidCount: number
  trend: TrendItem[]
}

const dashboard = ref<DashboardData>({
  todayAmount: 0,
  todayCount: 0,
  todayNetWeight: 0,
  unpaidAmount: 0,
  unpaidCount: 0,
  trend: []
})

/** 趋势柱最大值（用于换算柱高百分比），保底 1 避免除零 */
const trendMax = ref(1)

async function loadDashboard(): Promise<void> {
  try {
    const data = await get<DashboardData>('/api/dashboard')
    dashboard.value = {
      todayAmount: Number(data.todayAmount || 0),
      todayCount: Number(data.todayCount || 0),
      todayNetWeight: Number(data.todayNetWeight || 0),
      unpaidAmount: Number(data.unpaidAmount || 0),
      unpaidCount: Number(data.unpaidCount || 0),
      trend: Array.isArray(data.trend) ? data.trend : []
    }
    const max = dashboard.value.trend.reduce((m, t) => Math.max(m, Number(t.amount || 0)), 0)
    trendMax.value = max > 0 ? max : 1
  } catch (e) {
    // 提示已由封装统一处理
  }
}

/** 柱子高度百分比 */
function barHeight(amount: number): string {
  const pct = (Number(amount || 0) / trendMax.value) * 100
  // 至少留 4%，避免金额为 0 时柱子完全看不见
  return `${Math.max(pct, 0) > 0 ? Math.max(pct, 4) : 0}%`
}

function goDaily(): void {
  uni.navigateTo({ url: '/pages/daily/daily' })
}

onShow(() => {
  // 兜底守卫：token 被清（401/退出）时回登录页
  if (!hasToken()) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  loadDashboard()
})

onPullDownRefresh(() => {
  loadDashboard().finally(() => uni.stopPullDownRefresh())
})
</script>

<template>
  <view class="page">
    <!-- 顶部问候 -->
    <view class="hello">
      <text class="hello-name">你好，{{ getUser()?.nickname || getUser()?.username || '师傅' }}</text>
      <text class="hello-sub">今天辛苦了，收果顺利</text>
    </view>

    <!-- KPI 卡 -->
    <view class="kpi-grid">
      <view class="card kpi">
        <text class="kpi-label">今日收购额（元）</text>
        <text class="kpi-value">{{ fmtMoney(dashboard.todayAmount) }}</text>
        <text class="kpi-sub">净重 {{ fmtWeight(dashboard.todayNetWeight) }} 斤</text>
      </view>
      <view class="card kpi">
        <text class="kpi-label">今日单数</text>
        <text class="kpi-value">{{ dashboard.todayCount }}</text>
        <text class="kpi-sub">今日已开单</text>
      </view>
      <view class="card kpi">
        <text class="kpi-label">待付欠款（元）</text>
        <text class="kpi-value warn">{{ fmtMoney(dashboard.unpaidAmount) }}</text>
        <text class="kpi-sub">未付款合计</text>
      </view>
      <view class="card kpi">
        <text class="kpi-label">待付单数</text>
        <text class="kpi-value warn">{{ dashboard.unpaidCount }}</text>
        <text class="kpi-sub">未付款单据</text>
      </view>
    </view>

    <!-- 近 7 天趋势（view 自绘柱状图） -->
    <view class="card">
      <view class="card-title">近 7 天收购趋势（元）</view>
      <view v-if="dashboard.trend.length" class="chart">
        <view v-for="(item, i) in dashboard.trend" :key="i" class="bar-col">
          <text class="bar-num">{{ Math.round(Number(item.amount || 0)) }}</text>
          <view class="bar-track">
            <view class="bar" :style="{ height: barHeight(item.amount) }" />
          </view>
          <text class="bar-date">{{ item.date }}</text>
        </view>
      </view>
      <view v-else class="empty">近 7 天暂无数据</view>
    </view>

    <!-- 日结入口 -->
    <view class="entry" @tap="goDaily">
      <view class="entry-left">
        <text class="entry-title">今日日结</text>
        <text class="entry-sub">按品类 / 果农汇总当天收购</text>
      </view>
      <text class="entry-arrow">查看报表 ›</text>
    </view>
  </view>
</template>

<style scoped>
.page {
  padding-bottom: 40rpx;
}

.hello {
  padding: 32rpx 32rpx 8rpx;
  display: flex;
  flex-direction: column;
}

.hello-name {
  font-size: 36rpx;
  font-weight: 700;
}

.hello-sub {
  font-size: 24rpx;
  color: #909399;
  margin-top: 6rpx;
}

.kpi-grid {
  display: flex;
  flex-wrap: wrap;
  padding: 0 12rpx;
  box-sizing: border-box;
}

/* 两列 KPI：默认一行两个；内容（如超长金额）超过半宽时
   min-width:auto 撑开卡片并换行，该卡自动独占一行 */
.kpi {
  box-sizing: border-box;
  flex: 1 1 calc(50% - 24rpx);
  min-width: auto;
  margin: 0 12rpx 20rpx; /* 覆盖全局 .card 的 20rpx 24rpx 外边距 */
  display: flex;
  flex-direction: column;
}

.kpi-label {
  font-size: 24rpx;
  color: #909399;
}

.kpi-value {
  font-size: 48rpx;
  font-weight: 700;
  color: #1f2329;
  margin-top: 12rpx;
  white-space: nowrap; /* 金额不折行，超宽时触发整卡独占一行 */
}

.kpi-value.warn {
  color: #f56c6c;
}

.kpi-sub {
  font-size: 22rpx;
  color: #a8abb2;
  margin-top: 8rpx;
}

/* 趋势图 */
.chart {
  display: flex;
  align-items: flex-end;
}

.bar-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.bar-num {
  font-size: 20rpx;
  color: #909399;
  margin-bottom: 6rpx;
}

.bar-track {
  width: 60%;
  height: 200rpx;
  background: #f2f3f5;
  border-radius: 8rpx;
  display: flex;
  align-items: flex-end;
  overflow: hidden;
}

.bar {
  width: 100%;
  background: linear-gradient(180deg, #2ee08a, #07c160);
  border-radius: 8rpx 8rpx 0 0;
}

.bar-date {
  font-size: 20rpx;
  color: #909399;
  margin-top: 8rpx;
}

/* 日结入口 */
.entry {
  margin: 20rpx 24rpx;
  background: linear-gradient(135deg, #07c160, #05a24f);
  border-radius: 16rpx;
  padding: 32rpx 28rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #ffffff;
}

.entry-left {
  display: flex;
  flex-direction: column;
}

.entry-title {
  font-size: 32rpx;
  font-weight: 700;
}

.entry-sub {
  font-size: 24rpx;
  opacity: 0.85;
  margin-top: 6rpx;
}

.entry-arrow {
  font-size: 26rpx;
  opacity: 0.95;
}
</style>
