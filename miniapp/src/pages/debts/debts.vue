<script setup lang="ts">
/**
 * 欠款汇总：按果农聚合的未付款列表（GET /api/debts?keyword=）
 */
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { get } from '../../utils/request'
import { fmtMoney } from '../../utils/format'

interface Debt {
  farmerName: string
  phone?: string
  unpaidCount: number
  unpaidAmount: number
  lastDate?: string
}

const list = ref<Debt[]>([])
const keyword = ref('')
const loading = ref(false)

async function load(): Promise<void> {
  loading.value = true
  try {
    list.value = (await get<Debt[]>('/api/debts', { keyword: keyword.value.trim() })) || []
  } catch (e) {
    // 提示由封装统一处理
  } finally {
    loading.value = false
  }
}

function onKeyword(e: unknown): void {
  keyword.value = (e as { detail: { value: string } }).detail.value
}

function search(): void {
  load()
}

onShow(() => {
  load()
})
</script>

<template>
  <view class="page">
    <view class="search-bar">
      <input
        class="search-input"
        type="text"
        placeholder="搜索果农姓名 / 电话"
        placeholder-class="ph"
        :value="keyword"
        confirm-type="search"
        @input="onKeyword"
        @confirm="search"
      />
      <text class="search-btn" @tap="search">搜索</text>
    </view>

    <!-- 合计条 -->
    <view class="total-bar" v-if="list.length">
      <text>共 {{ list.length }} 位果农有欠款</text>
      <text class="total-amount">
        合计 ¥ {{ fmtMoney(list.reduce((s, d) => s + Number(d.unpaidAmount || 0), 0)) }}
      </text>
    </view>

    <view v-if="list.length">
      <view v-for="(item, i) in list" :key="i" class="card debt-card">
        <view class="debt-left">
          <text class="debt-name">{{ item.farmerName }}</text>
          <text class="debt-meta text-grey">
            {{ item.phone || '无电话' }} · {{ item.unpaidCount }} 单未付
          </text>
          <text class="debt-date text-grey">最近：{{ item.lastDate || '—' }}</text>
        </view>
        <text class="debt-amount">¥ {{ fmtMoney(item.unpaidAmount) }}</text>
      </view>
    </view>
    <view v-else-if="!loading" class="empty">暂无欠款，大家都结清了</view>
  </view>
</template>

<style scoped>
.page {
  padding-bottom: 40rpx;
}

.ph {
  color: #a8abb2;
}

.total-bar {
  display: flex;
  justify-content: space-between;
  padding: 20rpx 32rpx;
  font-size: 26rpx;
  color: #606266;
}

.total-amount {
  color: #f56c6c;
  font-weight: 600;
}

.debt-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.debt-left {
  display: flex;
  flex-direction: column;
}

.debt-name {
  font-size: 32rpx;
  font-weight: 600;
}

.debt-meta,
.debt-date {
  font-size: 24rpx;
  margin-top: 6rpx;
}

.debt-amount {
  font-size: 38rpx;
  font-weight: 700;
  color: #f56c6c;
}
</style>
