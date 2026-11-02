<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, ShoppingCart } from '@element-plus/icons-vue'
import api from '@/api'
import type { Customer, Product } from '@/types'
import { fmtMoney, fmtQty } from '@/utils/format'

// ========== 商品搜索 ==========
const keyword = ref('')
const products = ref<Product[]>([])
const searching = ref(false)

async function searchProducts() {
  searching.value = true
  try {
    products.value = await api.products.list({ keyword: keyword.value, status: 1 })
  } finally {
    searching.value = false
  }
}

// ========== 购物车明细 ==========
interface CartItem {
  productId: number
  name: string
  spec: string
  unit: string
  stock: number
  salePrice: number // 档案原价
  qty: number
  price: number // 实际售价（可改）
}

const cart = ref<CartItem[]>([])

function addToCart(p: Product) {
  const exist = cart.value.find((i) => i.productId === p.id)
  if (exist) {
    if (exist.qty + 1 > Number(p.stock)) {
      ElMessage.warning(`「${p.name}」库存不足，当前库存 ${fmtQty(p.stock)} ${p.unit}`)
      return
    }
    exist.qty += 1
    return
  }
  if (Number(p.stock) <= 0) {
    ElMessage.warning(`「${p.name}」当前无库存，无法开单`)
    return
  }
  cart.value.push({
    productId: p.id,
    name: p.name,
    spec: p.spec,
    unit: p.unit,
    stock: Number(p.stock),
    salePrice: Number(p.salePrice),
    qty: 1,
    price: Number(p.salePrice)
  })
}

function removeItem(idx: number) {
  cart.value.splice(idx, 1)
}

/** 单行小计 */
function lineAmount(i: CartItem) {
  return round2(i.qty * i.price)
}

/** 库存是否不足 */
function stockLack(i: CartItem) {
  return i.qty > i.stock
}

/** 是否有任一行超卖或数量非法 */
const hasStockError = computed(() => cart.value.some((i) => stockLack(i) || i.qty <= 0))

const totalAmount = computed(() => round2(cart.value.reduce((s, i) => s + lineAmount(i), 0)))

function round2(n: number) {
  return Math.round(n * 100) / 100
}

function changeQty(i: CartItem) {
  if (i.qty < 1) i.qty = 1
}

// ========== 收款方式与客户 ==========
const payMethod = ref<'CASH' | 'CREDIT'>('CASH')
const customerId = ref<number | null>(null)
const customers = ref<Customer[]>([])
const remark = ref('')

async function loadCustomers() {
  customers.value = await api.customers.list({ status: 1 })
}

// ========== 提交 ==========
const submitting = ref(false)

async function submit() {
  if (!cart.value.length) {
    ElMessage.warning('请先添加商品明细')
    return
  }
  if (hasStockError.value) {
    ElMessage.error('存在库存不足的明细行，请调整数量后再提交')
    return
  }
  if (payMethod.value === 'CREDIT' && !customerId.value) {
    ElMessage.warning('挂账开单必须选择客户')
    return
  }
  submitting.value = true
  try {
    await api.sales.create({
      payMethod: payMethod.value,
      customerId: payMethod.value === 'CREDIT' ? customerId.value : null,
      remark: remark.value,
      items: cart.value.map((i) => ({ productId: i.productId, qty: i.qty, price: i.price }))
    })
    ElMessage.success(payMethod.value === 'CASH' ? '开单成功（已结清）' : '挂账开单成功（待收款）')
    resetForm()
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  cart.value = []
  payMethod.value = 'CASH'
  customerId.value = null
  remark.value = ''
  keyword.value = ''
  searchProducts()
}

onMounted(() => {
  searchProducts()
  loadCustomers()
})
</script>

<template>
  <div>
    <div class="page-header">
      <h3>销售开单</h3>
      <div class="page-header-desc">搜索商品加入单据，支持改价留痕与库存校验，现结 / 挂账二选一</div>
    </div>

    <el-row :gutter="16">
      <!-- 左：商品选择 -->
      <el-col :xs="24" :md="10">
        <div class="page-card">
          <div class="section-title">选择商品</div>
          <div class="search-row">
            <el-input
              v-model="keyword"
              placeholder="输入商品名称 / 规格"
              clearable
              :prefix-icon="Search"
              @keyup.enter="searchProducts"
            />
            <el-button type="primary" :loading="searching" @click="searchProducts">搜索</el-button>
          </div>
          <div v-loading="searching" class="product-list">
            <div
              v-for="p in products"
              :key="p.id"
              class="product-item"
              :class="{ disabled: Number(p.stock) <= 0 }"
              @click="Number(p.stock) > 0 && addToCart(p)"
            >
              <div class="product-info">
                <div class="product-name">{{ p.name }}</div>
                <div class="product-spec">{{ p.spec }} · 库存 {{ fmtQty(p.stock) }} {{ p.unit }}</div>
              </div>
              <div class="product-price">
                <b>¥{{ fmtMoney(p.salePrice) }}</b>
                <span class="product-unit">/{{ p.unit }}</span>
              </div>
            </div>
            <el-empty v-if="!products.length && !searching" description="无匹配商品" :image-size="70" />
          </div>
        </div>
      </el-col>

      <!-- 右：开单明细 -->
      <el-col :xs="24" :md="14">
        <div class="page-card">
          <div class="section-title">
            <el-icon><ShoppingCart /></el-icon>
            开单明细（{{ cart.length }}）
          </div>

          <el-table :data="cart" stripe style="width: 100%">
            <el-table-column label="商品" min-width="150">
              <template #default="{ row }">
                <div class="cell-name">{{ row.name }}</div>
                <div class="cell-spec">{{ row.spec }}</div>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="120">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.qty"
                  :min="1"
                  :max="row.stock"
                  :precision="2"
                  :step="1"
                  size="small"
                  controls-position="right"
                  style="width: 100px"
                  @change="changeQty(row)"
                />
                <div v-if="stockLack(row)" class="stock-warn">
                  库存仅 {{ fmtQty(row.stock) }} {{ row.unit }}，不足！
                </div>
              </template>
            </el-table-column>
            <el-table-column label="单价" width="140">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.price"
                  :min="0"
                  :precision="2"
                  :step="1"
                  size="small"
                  controls-position="right"
                  style="width: 110px"
                />
                <div v-if="row.price !== row.salePrice" class="price-changed">
                  原价 ¥{{ fmtMoney(row.salePrice) }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="小计" width="100" align="right">
              <template #default="{ row }"><b>{{ fmtMoney(lineAmount(row)) }}</b></template>
            </el-table-column>
            <el-table-column label="" width="56" align="center">
              <template #default="{ $index }">
                <el-button link type="danger" @click="removeItem($index)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="点击左侧商品加入开单" :image-size="70" />
            </template>
          </el-table>

          <!-- 收款与提交 -->
          <div class="settle-box">
            <div class="settle-row">
              <span class="settle-label">收款方式</span>
              <el-radio-group v-model="payMethod">
                <el-radio value="CASH">现结（当场收钱）</el-radio>
                <el-radio value="CREDIT">挂账（计入欠款）</el-radio>
              </el-radio-group>
            </div>
            <div v-if="payMethod === 'CREDIT'" class="settle-row">
              <span class="settle-label">挂账客户</span>
              <el-select
                v-model="customerId"
                placeholder="必选：选择客户"
                clearable
                filterable
                style="width: 240px"
              >
                <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
              <span class="settle-tip">挂账金额将计入该客户欠款</span>
            </div>
            <div class="settle-row">
              <span class="settle-label">备注</span>
              <el-input v-model="remark" placeholder="选填" clearable style="width: 320px" />
            </div>
            <div class="settle-total">
              合计：<span class="total-num">¥ {{ fmtMoney(totalAmount) }}</span>
            </div>
            <div class="settle-actions">
              <el-button @click="resetForm">清空重开</el-button>
              <el-button
                type="primary"
                size="large"
                :loading="submitting"
                :disabled="!cart.length || hasStockError"
                @click="submit"
              >
                {{ payMethod === 'CASH' ? '确认开单收款' : '确认挂账开单' }}
              </el-button>
            </div>
            <div v-if="hasStockError" class="settle-error">存在库存不足的明细行，无法提交</div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--nct-text);
  margin-bottom: 12px;
}

.search-row {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.product-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 620px;
  overflow-y: auto;
}

.product-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid var(--nct-border);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
}

.product-item:hover {
  border-color: var(--nct-primary);
  background: #f3fbf6;
}

.product-item.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.product-name {
  font-size: 14px;
  color: var(--nct-text);
  font-weight: 500;
}

.product-spec {
  font-size: 12px;
  color: var(--nct-text-sub);
  margin-top: 2px;
}

.product-price {
  color: var(--nct-primary);
  font-size: 15px;
  white-space: nowrap;
}

.product-unit {
  font-size: 12px;
  color: var(--nct-text-sub);
}

.cell-name {
  font-size: 13px;
  color: var(--nct-text);
}

.cell-spec {
  font-size: 12px;
  color: var(--nct-text-sub);
}

.stock-warn {
  font-size: 12px;
  color: #e05050;
  line-height: 1.4;
  margin-top: 2px;
}

.price-changed {
  font-size: 12px;
  color: #e6a23c;
  margin-top: 2px;
  text-decoration: line-through;
}

.settle-box {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px dashed var(--nct-border);
}

.settle-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.settle-label {
  font-size: 13px;
  color: var(--nct-text-sub);
  width: 64px;
  flex-shrink: 0;
}

.settle-tip {
  font-size: 12px;
  color: var(--nct-text-sub);
}

.settle-total {
  text-align: right;
  font-size: 14px;
  color: var(--nct-text);
  margin: 8px 0 14px;
}

.total-num {
  font-size: 24px;
  font-weight: 700;
  color: var(--nct-primary);
}

.settle-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.settle-error {
  margin-top: 8px;
  text-align: right;
  font-size: 12px;
  color: #e05050;
}
</style>
