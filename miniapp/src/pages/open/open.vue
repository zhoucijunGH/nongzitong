<script setup lang="ts">
/**
 * 开单页（tab，核心页）
 * - 果品 picker 从 /api/fruits 拉启用项，选中带出默认单价（可改）
 * - 毛重/皮重实时算净重与金额
 * - 磅单照片：uni.chooseImage -> POST /api/upload 上传预览
 * - 离线草稿：表单实时写 storage('draft:order')，进页面有草稿则询问恢复，提交成功即清
 * - 记住上次选项：果品/来源/付款方式/付款状态存 storage('last:open:options')，进页面自动带出
 * - 果农历史：聚焦姓名时拉最近 100 单去重出常收果农，点一下带出姓名+电话
 * 开发提示：微信开发者工具需勾选「不校验合法域名」才能请求 127.0.0.1:8090
 */
import { ref, reactive, computed, watch } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { get, post, upload, resolveUrl } from '../../utils/request'
import { fmtMoney, fmtWeight, digitsOnly } from '../../utils/format'

interface Fruit {
  id: number
  name: string
  spec: string
  price: number
  unit: string
  enabled: boolean
}

const SOURCES = ['田头', '市场', '合作社']
/** 付款状态：界面显示中文，提交值 PAID/UNPAID（与后端/数据库枚举一致） */
const PAY_STATUS = [
  { label: '已付', value: 'PAID' },
  { label: '未付', value: 'UNPAID' }
]
const PAY_METHODS = ['现金', '微信', '转账']
const DRAFT_KEY = 'draft:order'
/** 上次开单选项（果品/来源/付款） */
const LAST_OPTS_KEY = 'last:open:options'

/** 表单模型（字段与 POST /api/orders 契约一致） */
const form = reactive({
  fruitId: 0,
  fruitLabel: '', // 仅用于回显选中的果品文案
  price: '', // 单价（元/斤），选中果品后带出，可改
  grossWeight: '', // 毛重（斤）
  tareWeight: '', // 皮重（斤）
  farmerName: '',
  farmerPhone: '',
  source: '田头',
  payStatus: 'PAID',
  payMethod: '现金',
  remark: '',
  photoUrl: ''
})

const fruits = ref<Fruit[]>([])
const fruitRange = ref<string[]>([])
const photoPreview = ref('')
const photoUploading = ref(false)
const submitting = ref(false)

/** 果农历史（姓名+电话，按最近开单去重） */
interface FarmerItem {
  name: string
  phone: string
}
const farmerHistory = ref<FarmerItem[]>([])
const showFarmerSug = ref(false)
let farmerHistoryLoaded = false

/** 净重 = 毛重 - 皮重（不为负） */
const netWeight = computed(() => {
  const gross = Number(form.grossWeight || 0)
  const tare = Number(form.tareWeight || 0)
  const net = gross - tare
  return net > 0 ? net : 0
})

/** 金额 = 净重 × 单价 */
const amount = computed(() => netWeight.value * Number(form.price || 0))

/** 选择果品：带出默认单价 */
function onFruitChange(e: { detail: { value: number } }): void {
  const idx = Number(e.detail.value)
  const fruit = fruits.value[idx]
  if (!fruit) return
  form.fruitId = fruit.id
  form.fruitLabel = fruitRange.value[idx] || ''
  form.price = String(fruit.price)
}

/** 套用上次记住的开单选项（草稿恢复流程在其后，确认恢复会覆盖这里） */
function applyLastOptions(): void {
  const raw = uni.getStorageSync(LAST_OPTS_KEY) as string
  if (!raw) return
  let opts: { fruitId?: number; source?: string; payStatus?: string; payMethod?: string } | null
  try {
    opts = JSON.parse(raw)
  } catch (e) {
    opts = null
  }
  if (!opts) return
  if (opts.source && SOURCES.includes(opts.source)) form.source = opts.source
  if (opts.payStatus && ['PAID', 'UNPAID'].includes(opts.payStatus)) form.payStatus = opts.payStatus
  if (opts.payMethod && PAY_METHODS.includes(opts.payMethod)) form.payMethod = opts.payMethod
  if (opts.fruitId) {
    const idx = fruits.value.findIndex((f) => f.id === opts!.fruitId)
    if (idx >= 0) {
      const fruit = fruits.value[idx]
      form.fruitId = fruit.id
      form.fruitLabel = fruitRange.value[idx] || ''
      form.price = String(fruit.price)
    }
  }
}

/** 拉取启用果品 */
async function loadFruits(): Promise<void> {
  try {
    const list = await get<Fruit[]>('/api/fruits', { keyword: '' })
    fruits.value = (list || []).filter((f) => f.enabled !== false)
    fruitRange.value = fruits.value.map(
      (f) => `${f.name}-${f.spec} ${f.price}元/${f.unit || '斤'}`
    )
    // 果品加载完再套用上次选项，保证能命中果品列表
    applyLastOptions()
  } catch (e) {
    // 提示由封装统一处理
  }
}

/** 毛重/皮重只允许数字 */
function onWeightInput(key: 'grossWeight' | 'tareWeight', e: unknown): void {
  form[key] = digitsOnly((e as { detail: { value: string } }).detail.value)
}

/** 选择磅单照片并上传 */
async function choosePhoto(): Promise<void> {
  if (photoUploading.value) return
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    success: async (res) => {
      const filePath = res.tempFilePaths && res.tempFilePaths[0]
      if (!filePath) return
      photoUploading.value = true
      uni.showLoading({ title: '上传中', mask: true })
      try {
        const url = await upload(filePath)
        form.photoUrl = url
        photoPreview.value = resolveUrl(url)
      } catch (e) {
        // 失败提示由封装处理
      } finally {
        photoUploading.value = false
        uni.hideLoading()
      }
    }
  })
}

/** 移除已选照片 */
function removePhoto(): void {
  form.photoUrl = ''
  photoPreview.value = ''
}

function previewPhoto(): void {
  if (!photoPreview.value) return
  uni.previewImage({ urls: [photoPreview.value] })
}

/** 表单是否有实质内容（果品/选项由「上次选项」记忆，不算草稿内容） */
function hasContent(): boolean {
  return !!(
    form.grossWeight ||
    form.tareWeight ||
    form.farmerName ||
    form.farmerPhone ||
    form.remark ||
    form.photoUrl
  )
}

/** 重置表单 */
function resetForm(): void {
  form.fruitId = 0
  form.fruitLabel = ''
  form.price = ''
  form.grossWeight = ''
  form.tareWeight = ''
  form.farmerName = ''
  form.farmerPhone = ''
  form.source = '田头'
  form.payStatus = 'PAID'
  form.payMethod = '现金'
  form.remark = ''
  form.photoUrl = ''
  photoPreview.value = ''
}

// 表单实时写草稿；清空表单时同步删除草稿，避免提交后残留
watch(
  form,
  () => {
    if (hasContent()) {
      uni.setStorageSync(DRAFT_KEY, JSON.stringify({ ...form }))
    } else {
      uni.removeStorageSync(DRAFT_KEY)
    }
  },
  { deep: true }
)

// 记住上次的果品/来源/付款选项（选定果品后即写入，退出也记得）
watch(
  () => [form.fruitId, form.source, form.payStatus, form.payMethod],
  () => {
    if (!form.fruitId) return
    uni.setStorageSync(
      LAST_OPTS_KEY,
      JSON.stringify({
        fruitId: form.fruitId,
        source: form.source,
        payStatus: form.payStatus,
        payMethod: form.payMethod
      })
    )
  }
)

/** 果农历史：拉最近 100 单，按单号倒序去重出常收果农（聚焦时懒加载） */
async function loadFarmerHistory(): Promise<void> {
  if (farmerHistoryLoaded) return
  farmerHistoryLoaded = true
  try {
    const res = await get<{ total: number; list: Array<{ id?: number; farmerName?: string; farmerPhone?: string }> }>(
      '/api/orders',
      { page: 1, size: 100 }
    )
    const rows = [...((res && res.list) || [])].sort((a, b) => (b.id || 0) - (a.id || 0))
    const seen = new Set<string>()
    const out: FarmerItem[] = []
    for (const r of rows) {
      const name = (r.farmerName || '').trim()
      if (!name || seen.has(name)) continue
      seen.add(name)
      out.push({ name, phone: (r.farmerPhone || '').trim() })
      if (out.length >= 20) break
    }
    farmerHistory.value = out
  } catch (e) {
    farmerHistoryLoaded = false // 失败允许下次聚焦重试
  }
}

/** 姓名输入框聚焦时显示历史建议 */
function onFarmerFocus(): void {
  loadFarmerHistory()
  showFarmerSug.value = true
}

/** 失焦延迟收起，保证「点建议项」的 tap 先触发 */
function onFarmerBlur(): void {
  setTimeout(() => {
    showFarmerSug.value = false
  }, 250)
}

/** 点历史果农：带出姓名+电话 */
function pickFarmer(f: FarmerItem): void {
  form.farmerName = f.name
  form.farmerPhone = f.phone
  showFarmerSug.value = false
}

/** 过滤后的建议列表：有输入按关键字过滤，无输入显示最近 8 个 */
const farmerSug = computed(() => {
  const kw = form.farmerName.trim()
  const base = farmerHistory.value.filter((f) => !kw || f.name.includes(kw))
  return base.slice(0, 8)
})

/** 进页面检查草稿，提示是否恢复 */
function checkDraft(): void {
  const raw = uni.getStorageSync(DRAFT_KEY) as string
  if (!raw) return
  let draft: Partial<typeof form> | null = null
  try {
    draft = JSON.parse(raw)
  } catch (e) {
    draft = null
  }
  if (!draft || !draft.fruitId) {
    uni.removeStorageSync(DRAFT_KEY)
    return
  }
  // 稍延迟弹窗，等页面渲染完成
  setTimeout(() => {
    uni.showModal({
      title: '发现草稿',
      content: '恢复上次未提交的单？',
      confirmText: '恢复',
      cancelText: '放弃',
      success: (r) => {
        if (r.confirm) {
          Object.assign(form, draft)
          photoPreview.value = form.photoUrl ? resolveUrl(form.photoUrl) : ''
          if (form.fruitId) {
            const idx = fruits.value.findIndex((f) => f.id === form.fruitId)
            form.fruitLabel = idx >= 0 ? fruitRange.value[idx] : form.fruitLabel
          }
        } else {
          uni.removeStorageSync(DRAFT_KEY)
        }
      }
    })
  }, 300)
}

/** 提交开单 */
async function submit(): Promise<void> {
  if (submitting.value) return
  if (!form.fruitId) {
    uni.showToast({ title: '请选择果品', icon: 'none' })
    return
  }
  if (!(Number(form.grossWeight) > 0)) {
    uni.showToast({ title: '请输入毛重', icon: 'none' })
    return
  }
  if (netWeight.value <= 0) {
    uni.showToast({ title: '净重需大于 0', icon: 'none' })
    return
  }
  if (!form.farmerName.trim()) {
    uni.showToast({ title: '请输入果农姓名', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await post(
      '/api/orders',
      {
        fruitId: form.fruitId,
        grossWeight: Number(form.grossWeight),
        tareWeight: Number(form.tareWeight || 0),
        price: Number(form.price || 0),
        farmerName: form.farmerName.trim(),
        farmerPhone: form.farmerPhone.trim(),
        source: form.source,
        payStatus: form.payStatus,
        payMethod: form.payMethod,
        remark: form.remark,
        photoUrl: form.photoUrl
      },
      true
    )
    uni.removeStorageSync(DRAFT_KEY)
    resetForm()
    uni.showToast({ title: '开单成功', icon: 'success' })
  } catch (e) {
    // 失败时保留表单与草稿，方便重试
  } finally {
    submitting.value = false
  }
}

onLoad(() => {
  loadFruits()
  checkDraft()
})
</script>

<template>
  <view class="page">
    <!-- 果品选择 -->
    <view class="card">
      <view class="card-title">果品</view>
      <picker :range="fruitRange" @change="onFruitChange">
        <view class="picker-box">
          <text :class="form.fruitLabel ? '' : 'ph'">
            {{ form.fruitLabel || '请选择果品（品类-规格 单价）' }}
          </text>
          <text class="arrow">›</text>
        </view>
      </picker>
      <view v-if="form.fruitId" class="form-item">
        <text class="form-label">单价（元/斤）</text>
        <input
          v-model="form.price"
          class="form-input"
          type="digit"
          placeholder="可修改单价"
          placeholder-class="ph"
        />
      </view>
    </view>

    <!-- 重量与金额 -->
    <view class="card">
      <view class="card-title">重量</view>
      <view class="form-item">
        <text class="form-label">毛重（斤）</text>
        <input
          :value="form.grossWeight"
          class="form-input"
          type="digit"
          placeholder="请输入毛重"
          placeholder-class="ph"
          @input="onWeightInput('grossWeight', $event)"
        />
      </view>
      <view class="form-item">
        <text class="form-label">皮重（斤）</text>
        <input
          :value="form.tareWeight"
          class="form-input"
          type="digit"
          placeholder="请输入皮重"
          placeholder-class="ph"
          @input="onWeightInput('tareWeight', $event)"
        />
      </view>
      <view class="calc-bar">
        <view class="calc-item">
          <text class="calc-label">净重</text>
          <text class="calc-value">{{ fmtWeight(netWeight) }} 斤</text>
        </view>
        <view class="calc-item">
          <text class="calc-label">金额</text>
          <text class="calc-value price">¥ {{ fmtMoney(amount) }}</text>
        </view>
      </view>
    </view>

    <!-- 果农信息 -->
    <view class="card">
      <view class="card-title">果农</view>
      <view class="form-item farmer-wrap">
        <text class="form-label">姓名</text>
        <input
          v-model="form.farmerName"
          class="form-input"
          type="text"
          placeholder="输入姓名或选择历史果农"
          placeholder-class="ph"
          @focus="onFarmerFocus"
          @blur="onFarmerBlur"
        />
        <!-- 历史果农快捷选择 -->
        <view v-if="showFarmerSug && farmerSug.length" class="farmer-sug">
          <view class="sug-head">
            <text class="sug-head-text">历史果农</text>
            <text class="sug-head-tip">点一下带出电话</text>
          </view>
          <view
            v-for="f in farmerSug"
            :key="f.name + f.phone"
            class="sug-item"
            @tap="pickFarmer(f)"
          >
            <text class="sug-name">{{ f.name }}</text>
            <text class="sug-phone">{{ f.phone || '—' }}</text>
          </view>
        </view>
      </view>
      <view class="form-item">
        <text class="form-label">电话</text>
        <input
          v-model="form.farmerPhone"
          class="form-input"
          type="number"
          placeholder="选填"
          placeholder-class="ph"
        />
      </view>
      <view class="form-item">
        <text class="form-label">备注</text>
        <input
          v-model="form.remark"
          class="form-input"
          type="text"
          placeholder="选填，如品种/等级"
          placeholder-class="ph"
        />
      </view>
    </view>

    <!-- 来源 / 付款 -->
    <view class="card">
      <view class="card-title">交易</view>
      <view class="form-item">
        <text class="form-label">来源</text>
        <view class="radio-group">
          <text
            v-for="s in SOURCES"
            :key="s"
            :class="['radio-tag', form.source === s ? 'active' : '']"
            @tap="form.source = s"
          >
            {{ s }}
          </text>
        </view>
      </view>
      <view class="form-item">
        <text class="form-label">付款状态</text>
        <view class="radio-group">
          <text
            v-for="s in PAY_STATUS"
            :key="s.value"
            :class="['radio-tag', form.payStatus === s.value ? 'active' : '']"
            @tap="form.payStatus = s.value"
          >
            {{ s.label }}
          </text>
        </view>
      </view>
      <view class="form-item">
        <text class="form-label">付款方式</text>
        <view class="radio-group">
          <text
            v-for="m in PAY_METHODS"
            :key="m"
            :class="['radio-tag', form.payMethod === m ? 'active' : '']"
            @tap="form.payMethod = m"
          >
            {{ m }}
          </text>
        </view>
      </view>
    </view>

    <!-- 磅单照片 -->
    <view class="card">
      <view class="card-title">磅单照片</view>
      <view class="photo-row">
        <view v-if="photoPreview" class="photo-wrap">
          <image :src="photoPreview" mode="aspectFill" class="photo" @tap="previewPhoto" />
          <view class="photo-del" @tap.stop="removePhoto">✕</view>
        </view>
        <view v-else class="photo-add" @tap="choosePhoto">
          <text class="photo-add-icon">＋</text>
          <text class="photo-add-text">{{ photoUploading ? '上传中' : '拍磅单' }}</text>
        </view>
      </view>
    </view>

    <!-- 提交 -->
    <view class="submit-wrap">
      <view :class="['btn-primary', submitting ? 'disabled' : '']" @tap="submit">
        {{ submitting ? '提交中...' : '提交开单' }}
      </view>
      <text class="draft-tip">内容自动存本机草稿，弱网也不怕丢单</text>
    </view>
  </view>
</template>

<style scoped>
.page {
  padding-bottom: 60rpx;
}

.picker-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #f7f8fa;
  border-radius: 12rpx;
  padding: 24rpx;
  font-size: 28rpx;
}

.ph {
  color: #a8abb2;
}

/* 果农历史下拉 */
.farmer-wrap {
  position: relative;
}

.farmer-sug {
  position: absolute;
  top: calc(100% - 8rpx);
  left: 0;
  right: 0;
  z-index: 20;
  background: #ffffff;
  border: 1rpx solid #ebeef5;
  border-radius: 12rpx;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.08);
  overflow: hidden;
  padding-bottom: 8rpx;
}

.sug-head {
  display: flex;
  justify-content: space-between;
  padding: 16rpx 24rpx 8rpx;
}

.sug-head-text {
  font-size: 22rpx;
  color: #909399;
}

.sug-head-tip {
  font-size: 22rpx;
  color: #c0c4cc;
}

.sug-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 24rpx;
}

.sug-item:active {
  background: #f5f7fa;
}

.sug-name {
  font-size: 28rpx;
  color: #303133;
}

.sug-phone {
  font-size: 24rpx;
  color: #909399;
}

.arrow {
  color: #a8abb2;
  font-size: 32rpx;
}

.calc-bar {
  display: flex;
  background: #f7fafa;
  border-radius: 12rpx;
  padding: 24rpx;
  margin-top: 20rpx;
}

.calc-item {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.calc-label {
  font-size: 24rpx;
  color: #909399;
}

.calc-value {
  font-size: 36rpx;
  font-weight: 700;
  margin-top: 6rpx;
}

.calc-value.price {
  color: #f56c6c;
}

.photo-row {
  display: flex;
}

.photo-wrap {
  position: relative;
}

.photo {
  width: 180rpx;
  height: 180rpx;
  border-radius: 12rpx;
  background: #f2f3f5;
}

.photo-del {
  position: absolute;
  top: -14rpx;
  right: -14rpx;
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.6);
  color: #ffffff;
  font-size: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.photo-add {
  width: 180rpx;
  height: 180rpx;
  border: 2rpx dashed #c0c4cc;
  border-radius: 12rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #909399;
}

.photo-add-icon {
  font-size: 56rpx;
  line-height: 1;
}

.photo-add-text {
  font-size: 24rpx;
  margin-top: 8rpx;
}

.submit-wrap {
  margin: 32rpx 24rpx 0;
}

.draft-tip {
  display: block;
  text-align: center;
  color: #a8abb2;
  font-size: 22rpx;
  margin-top: 16rpx;
}
</style>
