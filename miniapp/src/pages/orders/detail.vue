<script setup lang="ts">
/**
 * 订单详情：GET /api/orders/{id}
 * 展示全部字段 + 磅单照片（可点开大图预览）
 */
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { get, resolveUrl } from '../../utils/request'
import { fmtMoney, fmtWeight } from '../../utils/format'

interface OrderDetail {
  id: number
  orderNo?: string
  orderDate?: string
  fruitName?: string
  spec?: string
  grossWeight?: number
  tareWeight?: number
  netWeight?: number
  price?: number
  amount?: number
  farmerName?: string
  farmerPhone?: string
  source?: string
  payStatus?: string
  payMethod?: string
  remark?: string
  photoUrl?: string
  createdAt?: string
}

const order = ref<OrderDetail | null>(null)
const photoFull = ref('')

async function load(id: string): Promise<void> {
  try {
    const data = await get<OrderDetail>(`/api/orders/${id}`, undefined)
    order.value = data
    photoFull.value = data && data.photoUrl ? resolveUrl(data.photoUrl) : ''
  } catch (e) {
    // 提示由封装统一处理
  }
}

function previewPhoto(): void {
  if (!photoFull.value) return
  uni.previewImage({ urls: [photoFull.value] })
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

onLoad((query) => {
  const id = (query && query.id) || ''
  if (id) load(String(id))
})
</script>

<template>
  <view class="page">
    <view v-if="order" class="card">
      <view class="head">
        <view class="head-left">
          <text class="order-no">{{ order.orderNo || '单号 ' + order.id }}</text>
          <text class="order-date text-grey">{{ order.orderDate || order.createdAt || '' }}</text>
        </view>
        <text :class="['pill', isPaid(order.payStatus) ? 'pill-paid' : 'pill-unpaid']">
          {{ payLabel(order.payStatus) }}
        </text>
      </view>

      <view class="row">
        <text class="label">果品</text>
        <text class="value">
          {{ order.fruitName || '—' }}{{ order.spec ? ' · ' + order.spec : '' }}
        </text>
      </view>
      <view class="row">
        <text class="label">果农</text>
        <text class="value">{{ order.farmerName || '—' }}</text>
      </view>
      <view class="row">
        <text class="label">电话</text>
        <text class="value">{{ order.farmerPhone || '—' }}</text>
      </view>
      <view class="row">
        <text class="label">毛重 / 皮重</text>
        <text class="value">
          {{ fmtWeight(order.grossWeight) }} / {{ fmtWeight(order.tareWeight) }} 斤
        </text>
      </view>
      <view class="row">
        <text class="label">净重</text>
        <text class="value">{{ fmtWeight(order.netWeight) }} 斤</text>
      </view>
      <view class="row">
        <text class="label">单价</text>
        <text class="value">{{ fmtMoney(order.price) }} 元/斤</text>
      </view>
      <view class="row">
        <text class="label">金额</text>
        <text class="value price">¥ {{ fmtMoney(order.amount) }}</text>
      </view>
      <view class="row">
        <text class="label">来源</text>
        <text class="value">{{ order.source || '—' }}</text>
      </view>
      <view class="row">
        <text class="label">付款方式</text>
        <text class="value">{{ order.payMethod || '—' }}</text>
      </view>
      <view class="row">
        <text class="label">备注</text>
        <text class="value">{{ order.remark || '—' }}</text>
      </view>

      <view class="photo-block">
        <text class="card-title">磅单照片</text>
        <view v-if="photoFull" class="photo" @tap="previewPhoto">
          <image :src="photoFull" mode="aspectFill" class="photo-img" />
          <text class="photo-tip">点击预览</text>
        </view>
        <text v-else class="text-grey">无磅单照片</text>
      </view>
    </view>
    <view v-else class="empty">加载中...</view>
  </view>
</template>

<style scoped>
.page {
  padding-bottom: 40rpx;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 20rpx;
  border-bottom: 1rpx solid #f0f1f3;
  margin-bottom: 8rpx;
}

.head-left {
  display: flex;
  flex-direction: column;
}

.order-no {
  font-size: 32rpx;
  font-weight: 700;
}

.order-date {
  font-size: 24rpx;
  margin-top: 6rpx;
}

.row {
  display: flex;
  justify-content: space-between;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f7f8fa;
  font-size: 28rpx;
}

.row:last-of-type {
  border-bottom: none;
}

.label {
  color: #909399;
}

.value {
  color: #303133;
  text-align: right;
  flex: 1;
  margin-left: 24rpx;
}

.value.price {
  color: #f56c6c;
  font-weight: 700;
}

.photo-block {
  margin-top: 20rpx;
  border-top: 1rpx solid #f0f1f3;
  padding-top: 24rpx;
}

.photo {
  position: relative;
  width: 300rpx;
}

.photo-img {
  width: 300rpx;
  height: 300rpx;
  border-radius: 12rpx;
  background: #f2f3f5;
}

.photo-tip {
  position: absolute;
  right: 12rpx;
  bottom: 12rpx;
  font-size: 20rpx;
  color: #ffffff;
  background: rgba(0, 0, 0, 0.5);
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
}
</style>
