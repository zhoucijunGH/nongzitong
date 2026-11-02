<script setup lang="ts">
/**
 * 我的（tab）：当前登录人信息 + 功能菜单
 * 员工管理留给 Web 后台，此处仅查看账号信息
 */
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getUser, clearAuth, isAdmin } from '../../utils/store'

const user = ref(getUser())
const admin = ref(isAdmin())

onShow(() => {
  user.value = getUser()
  admin.value = isAdmin()
})

function goFruits(): void {
  uni.navigateTo({ url: '/pages/fruits/fruits' })
}

function goDebts(): void {
  uni.navigateTo({ url: '/pages/debts/debts' })
}

function goDaily(): void {
  uni.navigateTo({ url: '/pages/daily/daily' })
}

/** 导出 Excel 说明：小程序端不做导出，提示去 Web 后台 */
function showExportTip(): void {
  uni.showModal({
    title: '导出说明',
    content: '小程序暂不支持导出。请在电脑上登录 Web 后台，在流水/报表页面导出 Excel。',
    showCancel: false,
    confirmText: '知道了'
  })
}

function logout(): void {
  uni.showModal({
    title: '退出登录',
    content: '确定退出当前账号？',
    success: (r) => {
      if (!r.confirm) return
      clearAuth()
      uni.reLaunch({ url: '/pages/login/index' })
    }
  })
}

/** 角色中文名 */
function roleName(role?: string): string {
  const r = String(role || '').toUpperCase()
  if (r === 'ADMIN') return '管理员'
  return '员工'
}
</script>

<template>
  <view class="page">
    <!-- 用户信息 -->
    <view class="profile">
      <view class="avatar">{{ (user?.nickname || user?.username || '果').slice(0, 1) }}</view>
      <view class="profile-info">
        <text class="nickname">{{ user?.nickname || user?.username || '未登录' }}</text>
        <text class="meta">
          账号 {{ user?.username || '—' }} · {{ roleName(user?.role) }}
        </text>
      </view>
      <text v-if="admin" class="admin-badge">ADMIN</text>
    </view>

    <!-- 菜单 -->
    <view class="card menu">
      <view class="menu-item" @tap="goFruits">
        <text class="menu-name">果品档案</text>
        <text class="menu-extra text-grey">品类与默认单价</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @tap="goDebts">
        <text class="menu-name">欠款汇总</text>
        <text class="menu-extra text-grey">按果农看未付</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @tap="goDaily">
        <text class="menu-name">日结报表</text>
        <text class="menu-extra text-grey">当日汇总</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @tap="showExportTip">
        <text class="menu-name">导出说明</text>
        <text class="menu-extra text-grey">Excel 去 Web 后台</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <!-- 退出 -->
    <view class="logout" @tap="logout">退出登录</view>
  </view>
</template>

<style scoped>
.page {
  padding-bottom: 60rpx;
}

.profile {
  display: flex;
  align-items: center;
  background: linear-gradient(135deg, #07c160, #05a24f);
  padding: 48rpx 32rpx;
  color: #ffffff;
}

.avatar {
  width: 110rpx;
  height: 110rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
  font-weight: 700;
}

.profile-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-left: 24rpx;
}

.nickname {
  font-size: 36rpx;
  font-weight: 700;
}

.meta {
  font-size: 24rpx;
  opacity: 0.9;
  margin-top: 8rpx;
}

.admin-badge {
  font-size: 20rpx;
  background: rgba(255, 255, 255, 0.25);
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
}

.menu {
  padding: 0 28rpx;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 30rpx 0;
  border-bottom: 1rpx solid #f0f1f3;
}

.menu-item:last-child {
  border-bottom: none;
}

.menu-name {
  font-size: 30rpx;
  color: #303133;
}

.menu-extra {
  flex: 1;
  text-align: right;
  font-size: 24rpx;
  margin-right: 12rpx;
}

.menu-arrow {
  color: #c0c4cc;
  font-size: 34rpx;
}

.logout {
  margin: 40rpx 24rpx 0;
  background: #ffffff;
  color: #f56c6c;
  text-align: center;
  padding: 26rpx 0;
  border-radius: 16rpx;
  font-size: 30rpx;
}
</style>
