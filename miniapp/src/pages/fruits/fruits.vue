<script setup lang="ts">
/**
 * 果品档案：列表 + 搜索
 * 仅 ADMIN 可新增 / 编辑 / 删除（按钮按角色显示），普通员工只读
 */
import { ref, reactive } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { get, post, put, del } from '../../utils/request'
import { isAdmin } from '../../utils/store'
import { fmtMoney } from '../../utils/format'

interface Fruit {
  id: number
  name: string
  spec: string
  price: number
  unit: string
  enabled: boolean
}

const list = ref<Fruit[]>([])
const keyword = ref('')
const loading = ref(false)
const admin = ref(false)

// 新增/编辑弹层
const showEditor = ref(false)
const editingId = ref(0) // 0 表示新增
const saving = ref(false)
const form = reactive({
  name: '',
  spec: '',
  price: '',
  unit: '斤',
  enabled: true
})

async function load(): Promise<void> {
  loading.value = true
  try {
    list.value = (await get<Fruit[]>('/api/fruits', { keyword: keyword.value.trim() })) || []
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

/** 启用开关变化 */
function onEnabledChange(e: unknown): void {
  form.enabled = !!(e as { detail: { value: boolean } }).detail.value
}

function openCreate(): void {
  editingId.value = 0
  form.name = ''
  form.spec = ''
  form.price = ''
  form.unit = '斤'
  form.enabled = true
  showEditor.value = true
}

function openEdit(item: Fruit): void {
  editingId.value = item.id
  form.name = item.name
  form.spec = item.spec
  form.price = String(item.price)
  form.unit = item.unit || '斤'
  form.enabled = item.enabled !== false
  showEditor.value = true
}

function closeEditor(): void {
  showEditor.value = false
}

async function save(): Promise<void> {
  if (saving.value) return
  if (!form.name.trim()) {
    uni.showToast({ title: '请输入果品名称', icon: 'none' })
    return
  }
  if (!form.spec.trim()) {
    uni.showToast({ title: '请输入规格', icon: 'none' })
    return
  }
  if (!(Number(form.price) >= 0) || form.price === '') {
    uni.showToast({ title: '请输入单价', icon: 'none' })
    return
  }
  saving.value = true
  const payload: Record<string, unknown> = {
    name: form.name.trim(),
    spec: form.spec.trim(),
    price: Number(form.price),
    unit: form.unit.trim() || '斤',
    enabled: form.enabled
  }
  try {
    if (editingId.value) {
      await put(`/api/fruits/${editingId.value}`, payload, true)
    } else {
      await post('/api/fruits', payload, true)
    }
    uni.showToast({ title: '保存成功', icon: 'success' })
    showEditor.value = false
    load()
  } catch (e) {
    // 提示由封装统一处理
  } finally {
    saving.value = false
  }
}

function removeFruit(item: Fruit): void {
  uni.showModal({
    title: '删除果品',
    content: `确定删除「${item.name}-${item.spec}」？`,
    confirmColor: '#f56c6c',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await del(`/api/fruits/${item.id}`, true)
        uni.showToast({ title: '已删除', icon: 'success' })
        load()
      } catch (e) {
        // 提示由封装统一处理
      }
    }
  })
}

onShow(() => {
  admin.value = isAdmin()
  load()
})
</script>

<template>
  <view class="page">
    <!-- 搜索 + 新增 -->
    <view class="search-bar">
      <input
        class="search-input"
        type="text"
        placeholder="搜索果品名称"
        placeholder-class="ph"
        :value="keyword"
        confirm-type="search"
        @input="onKeyword"
        @confirm="search"
      />
      <text class="search-btn" @tap="search">搜索</text>
      <text v-if="admin" class="add-btn" @tap="openCreate">＋ 新增</text>
    </view>

    <!-- 列表 -->
    <view v-if="list.length">
      <view v-for="item in list" :key="item.id" class="card fruit-card">
        <view class="fruit-main">
          <view class="fruit-title-row">
            <text class="fruit-name">{{ item.name }}</text>
            <text :class="['pill', item.enabled !== false ? 'pill-paid' : 'pill-unpaid']">
              {{ item.enabled !== false ? '启用' : '停用' }}
            </text>
          </view>
          <text class="fruit-meta text-grey">规格 {{ item.spec }} · 单位 {{ item.unit || '斤' }}</text>
          <text class="fruit-price">¥ {{ fmtMoney(item.price) }} / {{ item.unit || '斤' }}</text>
        </view>
        <view v-if="admin" class="fruit-ops">
          <text class="op-btn" @tap="openEdit(item)">编辑</text>
          <text class="op-btn danger" @tap="removeFruit(item)">删除</text>
        </view>
      </view>
    </view>
    <view v-else-if="!loading" class="empty">暂无果品档案</view>

    <!-- 新增/编辑弹层 -->
    <view v-if="showEditor" class="mask" @tap="closeEditor">
      <view class="sheet" @tap.stop>
        <text class="sheet-title">{{ editingId ? '编辑果品' : '新增果品' }}</text>
        <view class="form-item">
          <text class="form-label">名称</text>
          <input v-model="form.name" class="form-input" type="text" placeholder="如：沙糖桔" placeholder-class="ph" />
        </view>
        <view class="form-item">
          <text class="form-label">规格</text>
          <input v-model="form.spec" class="form-input" type="text" placeholder="如：大果" placeholder-class="ph" />
        </view>
        <view class="form-item">
          <text class="form-label">单价（元）</text>
          <input v-model="form.price" class="form-input" type="digit" placeholder="默认开单单价" placeholder-class="ph" />
        </view>
        <view class="form-item">
          <text class="form-label">单位</text>
          <input v-model="form.unit" class="form-input" type="text" placeholder="斤" placeholder-class="ph" />
        </view>
        <view class="form-item">
          <text class="form-label">启用</text>
          <switch :checked="form.enabled" color="#07c160" @change="onEnabledChange" />
        </view>
        <view class="sheet-btns">
          <view class="btn-cancel" @tap="closeEditor">取消</view>
          <view :class="['btn-primary', saving ? 'disabled' : '']" @tap="save">
            {{ saving ? '保存中...' : '保存' }}
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.page {
  padding-bottom: 40rpx;
}

.ph {
  color: #a8abb2;
}

.add-btn {
  margin-left: 20rpx;
  color: #ffffff;
  background: #07c160;
  border-radius: 999rpx;
  padding: 10rpx 26rpx;
  font-size: 26rpx;
}

.fruit-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.fruit-main {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.fruit-title-row {
  display: flex;
  align-items: center;
}

.fruit-name {
  font-size: 32rpx;
  font-weight: 600;
  margin-right: 16rpx;
}

.fruit-meta {
  font-size: 24rpx;
  margin-top: 8rpx;
}

.fruit-price {
  font-size: 30rpx;
  color: #f56c6c;
  font-weight: 700;
  margin-top: 8rpx;
}

.fruit-ops {
  display: flex;
  flex-direction: column;
  margin-left: 20rpx;
}

.op-btn {
  font-size: 26rpx;
  color: #07c160;
  padding: 8rpx 0;
}

.op-btn.danger {
  color: #f56c6c;
}

/* 弹层 */
.mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 99;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sheet {
  width: 620rpx;
  background: #ffffff;
  border-radius: 20rpx;
  padding: 32rpx;
}

.sheet-title {
  font-size: 32rpx;
  font-weight: 700;
  display: block;
  margin-bottom: 8rpx;
}

.sheet-btns {
  display: flex;
  margin-top: 32rpx;
}

.sheet-btns > view {
  flex: 1;
}

.sheet-btns > view:first-child {
  margin-right: 20rpx;
}

.btn-cancel {
  background: #f2f3f5;
  color: #606266;
  border-radius: 12rpx;
  text-align: center;
  padding: 24rpx 0;
  font-size: 30rpx;
}
</style>
