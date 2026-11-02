<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import api from '@/api'
import type { Product, PurchaseIn, Supplier } from '@/types'
import { fmtMoney, fmtQty } from '@/utils/format'

// ========== 查询与分页 ==========
const dateRange = ref<[string, string] | null>(null)
const keyword = ref('')
const status = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const list = ref<PurchaseIn[]>([])
const loading = ref(false)
const exporting = ref(false)

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '已入库', value: 'STORED' },
  { label: '已作废', value: 'VOID' }
]

async function load() {
  loading.value = true
  try {
    const res = await api.purchases.list({
      dateFrom: dateRange.value?.[0] || '',
      dateTo: dateRange.value?.[1] || '',
      keyword: keyword.value,
      status: status.value,
      page: page.value,
      size: size.value
    })
    list.value = res.list || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function reset() {
  dateRange.value = null
  keyword.value = ''
  status.value = ''
  search()
}

function onPageChange(p: number) {
  page.value = p
  load()
}

function onSizeChange(s: number) {
  size.value = s
  page.value = 1
  load()
}

async function exportExcel() {
  exporting.value = true
  try {
    const blob = await api.export.purchases({
      dateFrom: dateRange.value?.[0] || '',
      dateTo: dateRange.value?.[1] || ''
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `采购入库_${dateRange.value ? `${dateRange.value[0]}_${dateRange.value[1]}` : '全部'}.xlsx`
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  } finally {
    exporting.value = false
  }
}

// ========== 新建入库单 ==========
const createVisible = ref(false)
const saving = ref(false)
const suppliers = ref<Supplier[]>([])
const products = ref<Product[]>([])
const keywordP = ref('')

const form = reactive({
  orderDate: '',
  supplierId: null as number | null,
  remark: '',
  items: [] as { productId: number; name: string; spec: string; unit: string; qty: number; price: number }[]
})

function todayStr() {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

async function openCreate() {
  form.orderDate = todayStr()
  form.supplierId = null
  form.remark = ''
  form.items = []
  keywordP.value = ''
  createVisible.value = true
  if (!suppliers.value.length) suppliers.value = await api.suppliers.list()
  await loadProducts()
}

async function loadProducts() {
  products.value = await api.products.list({ keyword: keywordP.value })
}

const filteredProducts = computed(() =>
  keywordP.value
    ? products.value.filter(
        (p) => p.name.includes(keywordP.value) || (p.spec || '').includes(keywordP.value)
      )
    : products.value
)

function addProduct(p: Product) {
  const exist = form.items.find((i) => i.productId === p.id)
  if (exist) {
    exist.qty += 1
    return
  }
  form.items.push({
    productId: p.id,
    name: p.name,
    spec: p.spec,
    unit: p.unit,
    qty: 1,
    price: Number(p.purchasePrice)
  })
}

function removeItem(idx: number) {
  form.items.splice(idx, 1)
}

const totalAmount = computed(() =>
  Math.round(form.items.reduce((s, i) => s + i.qty * i.price, 0) * 100) / 100
)

const invalid = computed(() => form.items.some((i) => i.qty <= 0 || i.price < 0))

async function submit() {
  if (!form.supplierId) {
    ElMessage.warning('请选择供应商')
    return
  }
  if (!form.items.length) {
    ElMessage.warning('请添加入库明细')
    return
  }
  if (invalid.value) {
    ElMessage.warning('明细数量或进价不合法')
    return
  }
  saving.value = true
  try {
    await api.purchases.create({
      orderDate: form.orderDate,
      supplierId: form.supplierId,
      remark: form.remark,
      items: form.items.map((i) => ({ productId: i.productId, qty: i.qty, price: i.price }))
    })
    ElMessage.success('入库成功，库存已增加')
    createVisible.value = false
    search()
  } finally {
    saving.value = false
  }
}

// ========== 详情 ==========
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<PurchaseIn | null>(null)

async function openDetail(row: PurchaseIn) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await api.purchases.detail(row.id)
  } finally {
    detailLoading.value = false
  }
}

// ========== 作废（负库存警示确认） ==========
function voidOrder(row: PurchaseIn) {
  ElMessageBox.prompt('请填写作废原因（作废后该单库存将回滚）', '作废采购入库单', {
    type: 'warning',
    confirmButtonText: '确认作废',
    cancelButtonText: '取消',
    inputValidator: (v: string) => (v && v.trim().length > 0 ? true : '原因不能为空')
  })
    .then(async ({ value }) => {
      try {
        await api.purchases.voidOrder(row.id, value.trim())
        ElMessage.success('已作废并回滚库存')
        detailVisible.value = false
        load()
      } catch (e: any) {
        // 409：回滚将导致负库存，需二次警示确认
        if (e?.response?.status === 409 || e?.code === 409 || String(e?.message || '').includes('负库存')) {
          const msg = e?.response?.data?.message || e?.message || '回滚将导致部分商品出现负库存'
          ElMessageBox.confirm(`${msg}，是否仍要强制作废？`, '负库存警示', {
            type: 'error',
            confirmButtonText: '仍要作废',
            cancelButtonText: '取消'
          })
            .then(async () => {
              await api.purchases.voidOrder(row.id, value.trim(), true)
              ElMessage.success('已强制作废并回滚库存')
              detailVisible.value = false
              load()
            })
            .catch(() => {})
        } else {
          throw e
        }
      }
    })
    .catch(() => {})
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-header">
      <h3>采购入库</h3>
      <div class="page-header-desc">按供应商录入入库明细，入库即增加库存；作废回滚支持负库存警示确认</div>
    </div>

    <!-- 筛选栏 -->
    <div class="page-card query-bar">
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        :clearable="true"
      />
      <el-input
        v-model="keyword"
        placeholder="单号 / 供应商"
        clearable
        style="width: 190px"
        @keyup.enter="search"
      />
      <el-select v-model="status" placeholder="全部状态" style="width: 130px">
        <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
      <div style="flex: 1"></div>
      <el-button type="primary" @click="openCreate">新建入库</el-button>
      <el-button :loading="exporting" @click="exportExcel">导出 Excel</el-button>
    </div>

    <!-- 列表 -->
    <div class="page-card">
      <el-table v-loading="loading" :data="list" stripe style="width: 100%">
        <el-table-column prop="orderNo" label="入库单号" width="150" />
        <el-table-column prop="orderDate" label="日期" width="110" />
        <el-table-column prop="supplierName" label="供应商" min-width="150" show-overflow-tooltip />
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }"><b>{{ fmtMoney(row.totalAmount) }}</b></template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'STORED' ? 'success' : 'info'" size="small">
              {{ row.status === 'STORED' ? '已入库' : '已作废' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdByName" label="操作人" width="90" />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.status !== 'VOID'"
              link
              type="danger"
              @click="voidOrder(row)"
            >
              作废
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无入库单" />
        </template>
      </el-table>

      <div class="table-toolbar">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="page"
          :page-size="size"
          :page-sizes="[10, 20, 50, 100]"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <!-- 新建弹窗 -->
    <el-dialog v-model="createVisible" title="新建采购入库单" width="760px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="入库日期">
              <el-date-picker v-model="form.orderDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="供应商" required>
              <el-select v-model="form.supplierId" placeholder="选择供应商" filterable style="width: 100%">
                <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注">
              <el-input v-model="form.remark" placeholder="选填" clearable />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div class="pick-bar">
        <el-input
          v-model="keywordP"
          placeholder="搜索商品加入明细"
          clearable
          style="width: 240px"
          @input="loadProducts"
        />
        <span class="pick-tip">点击商品加入下方明细</span>
      </div>
      <div class="pick-list">
        <el-tag
          v-for="p in filteredProducts"
          :key="p.id"
          class="pick-tag"
          effect="plain"
          @click="addProduct(p)"
        >
          {{ p.name }} {{ p.spec }}
        </el-tag>
      </div>

      <el-table :data="form.items" stripe style="width: 100%; margin-top: 12px">
        <el-table-column label="商品" min-width="170">
          <template #default="{ row }">
            {{ row.name }} <span class="cell-spec">{{ row.spec }}</span>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="150">
          <template #default="{ row }">
            <el-input-number
              v-model="row.qty"
              :min="0.01"
              :precision="2"
              :step="1"
              size="small"
              controls-position="right"
              style="width: 120px"
            />
            <span class="cell-spec"> {{ row.unit }}</span>
          </template>
        </el-table-column>
        <el-table-column label="进价" width="140">
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
          </template>
        </el-table-column>
        <el-table-column label="小计" width="110" align="right">
          <template #default="{ row }"><b>{{ fmtMoney(row.qty * row.price) }}</b></template>
        </el-table-column>
        <el-table-column label="" width="56" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" :icon="Delete" @click="removeItem($index)" />
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无明细，请点击上方商品加入" :image-size="60" />
        </template>
      </el-table>

      <div class="dialog-foot">
        <span class="foot-total">合计：<b>¥ {{ fmtMoney(totalAmount) }}</b></span>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="!form.items.length || invalid"
          @click="submit"
        >
          确认入库
        </el-button>
      </div>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="入库单详情" width="700px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="入库单号">{{ detail.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="日期">{{ detail.orderDate }}</el-descriptions-item>
            <el-descriptions-item label="供应商">{{ detail.supplierName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="detail.status === 'STORED' ? 'success' : 'info'" size="small">
                {{ detail.status === 'STORED' ? '已入库' : '已作废' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="合计金额">¥ {{ fmtMoney(detail.totalAmount) }}</el-descriptions-item>
            <el-descriptions-item label="操作人">{{ detail.createdByName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
            <template v-if="detail.status === 'VOID'">
              <el-descriptions-item label="作废原因" :span="2">{{ detail.voidReason }}</el-descriptions-item>
              <el-descriptions-item label="作废人">{{ detail.voidedByName }}</el-descriptions-item>
              <el-descriptions-item label="作废时间">{{ detail.voidedAt }}</el-descriptions-item>
            </template>
          </el-descriptions>

          <el-table :data="detail.items || []" stripe style="width: 100%; margin-top: 12px">
            <el-table-column type="index" label="#" width="46" />
            <el-table-column label="商品" min-width="160">
              <template #default="{ row }">
                {{ row.productName }} <span class="cell-spec">{{ row.spec }}</span>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="90" align="right">
              <template #default="{ row }">{{ fmtQty(row.qty) }}</template>
            </el-table-column>
            <el-table-column label="进价" width="100" align="right">
              <template #default="{ row }">{{ fmtMoney(row.price) }}</template>
            </el-table-column>
            <el-table-column label="小计" width="110" align="right">
              <template #default="{ row }"><b>{{ fmtMoney(row.amount) }}</b></template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.cell-spec {
  font-size: 12px;
  color: var(--nct-text-sub);
}

.pick-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.pick-tip {
  font-size: 12px;
  color: var(--nct-text-sub);
}

.pick-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  max-height: 120px;
  overflow-y: auto;
}

.pick-tag {
  cursor: pointer;
}

.pick-tag:hover {
  color: var(--nct-primary);
  border-color: var(--nct-primary);
}

.dialog-foot {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
}

.foot-total {
  margin-right: auto;
  font-size: 14px;
}

.foot-total b {
  font-size: 18px;
  color: var(--nct-primary);
}
</style>
