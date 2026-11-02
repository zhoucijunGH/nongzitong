<script setup lang="ts">
/**
 * 登录页：账号密码登录
 * 成功后存 token 与 user 到 storage，跳转工作台
 */
import { ref } from 'vue'
import { post } from '../../utils/request'
import { setAuth, hasToken } from '../../utils/store'
import type { User } from '../../utils/store'

interface LoginData {
  token: string
  user: User
}

const username = ref('')
const password = ref('')
const loading = ref(false)

/** 若本地已有登录态，直接进工作台（避免登录页闪现） */
function goHomeIfLoggedIn(): void {
  if (hasToken()) {
    uni.switchTab({ url: '/pages/index/index' })
  }
}

async function doLogin(): Promise<void> {
  if (!username.value.trim()) {
    uni.showToast({ title: '请输入账号', icon: 'none' })
    return
  }
  if (!password.value) {
    uni.showToast({ title: '请输入密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const data = await post<LoginData>(
      '/api/auth/login',
      { username: username.value.trim(), password: password.value },
      true
    )
    setAuth(data.token, data.user)
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => uni.switchTab({ url: '/pages/index/index' }), 400)
  } catch (e) {
    // 错误提示已由 request 封装统一 toast
  } finally {
    loading.value = false
  }
}

// 进页面时若已登录则直接回工作台
goHomeIfLoggedIn()
</script>

<template>
  <view class="login-page">
    <view class="brand">
      <view class="logo">果</view>
      <text class="app-name">果账通</text>
      <text class="app-slogan">产地收购 · 开单记账</text>
    </view>

    <view class="card login-card">
      <view class="form-item">
        <text class="form-label">账号</text>
        <input
          v-model="username"
          class="form-input"
          type="text"
          placeholder="请输入账号"
          placeholder-class="ph"
        />
      </view>
      <view class="form-item">
        <text class="form-label">密码</text>
        <input
          v-model="password"
          class="form-input"
          type="password"
          password
          placeholder="请输入密码"
          placeholder-class="ph"
        />
      </view>
    </view>

    <view class="login-btn-wrap">
      <view :class="['btn-primary', loading ? 'disabled' : '']" @tap="doLogin">
        {{ loading ? '登录中...' : '登 录' }}
      </view>
    </view>
  </view>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  padding-top: 180rpx;
}

.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 40rpx;
}

.logo {
  width: 140rpx;
  height: 140rpx;
  border-radius: 32rpx;
  background: #07c160;
  color: #ffffff;
  font-size: 76rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  margin-bottom: 24rpx;
}

.app-name {
  font-size: 44rpx;
  font-weight: 700;
  color: #1f2329;
}

.app-slogan {
  font-size: 26rpx;
  color: #909399;
  margin-top: 8rpx;
}

.login-card {
  margin-top: 20rpx;
}

.ph {
  color: #a8abb2;
}

.login-btn-wrap {
  margin: 48rpx 24rpx 0;
}
</style>
