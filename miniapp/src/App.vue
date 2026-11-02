<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { hasToken } from './utils/store'

onLaunch(() => {
  // 冷启动守卫：没有 token 直接进登录页（启动页是工作台，reLaunch 会替换页面栈）
  if (!hasToken()) {
    uni.reLaunch({ url: '/pages/login/index' })
  }
})
</script>

<style>
/* ===== 全局公共样式（各页面复用的卡片/表单/按钮等） ===== */
page {
  background: #f5f6f7;
  font-size: 28rpx;
  color: #303133;
  line-height: 1.5;
}

/* 白色卡片 */
.card {
  background: #ffffff;
  border-radius: 16rpx;
  padding: 28rpx;
  margin: 20rpx 24rpx;
  box-shadow: 0 2rpx 10rpx rgba(0, 0, 0, 0.04);
}

.card-title {
  font-size: 30rpx;
  font-weight: 600;
  margin-bottom: 20rpx;
  color: #1f2329;
}

/* 主按钮 */
.btn-primary {
  background: #07c160;
  color: #ffffff;
  border-radius: 12rpx;
  text-align: center;
  padding: 24rpx 0;
  font-size: 32rpx;
  font-weight: 600;
}

.btn-primary.disabled {
  background: #9be4bd;
}

.btn-plain {
  background: #ffffff;
  color: #07c160;
  border: 2rpx solid #07c160;
  border-radius: 12rpx;
  text-align: center;
  padding: 20rpx 0;
  font-size: 28rpx;
}

.btn-danger {
  background: #ffffff;
  color: #f56c6c;
  border: 2rpx solid #f56c6c;
  border-radius: 12rpx;
  text-align: center;
  padding: 20rpx 0;
  font-size: 28rpx;
}

/* 表单行 */
.form-item {
  display: flex;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f0f1f3;
}

.form-item:last-child {
  border-bottom: none;
}

.form-label {
  width: 180rpx;
  color: #606266;
  font-size: 28rpx;
  flex-shrink: 0;
}

.form-input {
  flex: 1;
  font-size: 28rpx;
  text-align: right;
  min-height: 40rpx;
}

.form-input .placeholder,
.form-textarea {
  color: #a8abb2;
}

/* 单选组（来源 / 付款状态 / 付款方式） */
.radio-group {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  flex: 1;
}

.radio-tag {
  padding: 10rpx 26rpx;
  margin: 0 0 12rpx 16rpx;
  border-radius: 999rpx;
  background: #f2f3f5;
  color: #606266;
  font-size: 26rpx;
  border: 2rpx solid transparent;
}

.radio-tag.active {
  background: #e8f8ef;
  color: #07c160;
  border-color: #07c160;
}

/* 状态标签 */
.pill {
  display: inline-block;
  padding: 4rpx 18rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
}

.pill-paid {
  background: #e8f8ef;
  color: #07c160;
}

.pill-unpaid {
  background: #fdeeee;
  color: #f56c6c;
}

/* 搜索栏 */
.search-bar {
  display: flex;
  align-items: center;
  background: #ffffff;
  padding: 16rpx 24rpx;
}

.search-input {
  flex: 1;
  background: #f2f3f5;
  border-radius: 999rpx;
  padding: 12rpx 28rpx;
  font-size: 26rpx;
}

.search-btn {
  margin-left: 20rpx;
  color: #07c160;
  font-size: 28rpx;
}

/* 空状态 */
.empty {
  text-align: center;
  color: #a8abb2;
  padding: 80rpx 0;
  font-size: 26rpx;
}

.text-right {
  text-align: right;
}

.text-grey {
  color: #909399;
}

.text-price {
  color: #f56c6c;
}
</style>
