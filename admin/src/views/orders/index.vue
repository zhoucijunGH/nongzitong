<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import api from '@/api'
import type { Fruit, Order, OrderForm } from '@/types'
import { useUserStore } from '@/stores/user'
import { amountOf, fmtMoney, fmtWeight, netWeightOf } from '@/utils/format'

const userStore = useUserStore()

// ========== 查询与分页 ==========
const dateRange = ref<[string, string] | null>(null)
const keyword = ref('')
const payStatus = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const list = ref<Order[]>([])
const loading = ref(false)
const exporting = ref(false)

// 付款状态下拉选项（契约字段 payStatus 为字符串，按 PAID/UNPAID 约定）
const payOptions = [
  { label: '全部状态', value: '' },
  { label: '已付款', value: 'PAID' },
  { label: '未付款', value: 'UNPAID' }
]

// 付款状态 → 标签展示
function payTag(status?: string) {
  const map: Record<string, { label: string; type: 'success' | 'warning' | 'info' }> = {
    PAID: { label: '已付款', type: 'success' },
    UNPAID: { label: '未付款', type: 'warning' },
    已付款: { label: '已付款', type: 'success' },
    未付款: { label: '未付款', type: 'warning' }
  }
  if (!status) return { label: '-', type: 'info' as const }
  return map[status] || { label: status, type: 'info' as const }
}

// 支付方式展示
function payMethodLabel(m?: string) {
  const map: Record<string, string> = { CASH: '现金', WECHAT: '微信', ALIPAY: '支付宝', BANK: '转账' }
  if (!m) return '-'
  return map[m] || m
}

// 磅单照片地址（相对路径走 /uploads 代理）
function photoSrc(url?: string) {
  if (!url) return ''
  if (url.startsWith('http') || url.startsWith('/')) return url
  return '/' + url
}

async function load() {
  loading.value = true
  try {
    const res = await api.orders.list({
      dateFrom: dateRange.value?.[0] || '',
      dateTo: dateRange.value?.[1] || '',
      keyword: keyword.value,
      payStatus: payStatus.value,
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
  payStatus.value = ''
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

// ========== 导出 Excel（blob 下载，走 Authorization 头） ==========
async function exportExcel() {
  exporting.value = true
  try {
    const blob = await api.export.orders({
      dateFrom: dateRange.value?.[0] || '',
      dateTo: dateRange.value?.[1] || ''
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `收购流水_${dateRange.value ? `${dateRange.value[0]}_${dateRange.value[1]}` : '全部'}.xlsx`
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  } finally {
    exporting.value = false
  }
}

// ========== 开单 / 编辑弹窗 ==========
const dialogVisible = ref(false)
const dialogTitle = ref('开单')
const editingId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const fruits = ref<Fruit[]>([])

function defaultForm(): OrderForm {
  return {
    fruitId: undefined,
    grossWeight: undefined,
    tareWeight: 0,
    price: undefined,
    farmerName: '',
    farmerPhone: '',
    source: '',
    payStatus: 'PAID',
    payMethod: 'CASH',
    remark: '',
    photoUrl: ''
  }
}

const form = reactive<OrderForm>(defaultForm())

const rules: FormRules = {
  fruitId: [{ required: true, message: '请选择果品', trigger: 'change' }],
  grossWeight: [{ required: true, message: '请输入毛重', trigger: 'blur' }],
  price: [{ required: true, message: '请输入单价', trigger: 'blur' }],
  farmerName: [{ required: true, message: '请输入果农姓名', trigger: 'blur' }]
}

// 表单净重 / 金额实时预览
const formNetWeight = () => Math.max(Number(form.grossWeight || 0) - Number(form.tareWeight || 0), 0)
const formAmount = () => formNetWeight() * Number(form.price || 0)

function openCreate() {
  editingId.value = null
  dialogTitle.value = '开单 · 新增收购单'
  Object.assign(form, defaultForm())
  dialogVisible.value = true
}

function openEdit(row: Order) {
  editingId.value = row.id
  dialogTitle.value = '编辑收购单'
  Object.assign(form, {
    fruitId: row.fruitId,
    grossWeight: row.grossWeight,
    tareWeight: row.tareWeight,
    price: row.price,
    farmerName: row.farmerName,
    farmerPhone: row.farmerPhone,
    source: row.source,
    payStatus: row.payStatus,
    payMethod: row.payMethod,
    remark: row.remark,
    photoUrl: row.photoUrl
  })
  dialogVisible.value = true
}

// 选后果品自动带出单价
function onFruitChange(id: number | undefined) {
  const f = fruits.value.find((x) => x.id === id)
  if (f) form.price = f.price
}

async function save() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    if (editingId.value != null) {
      await api.orders.update(editingId.value, { ...form })
      ElMessage.success('修改成功')
    } else {
      await api.orders.create({ ...form })
      ElMessage.success('开单成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ========== 详情弹窗 ==========
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<Order | null>(null)

async function openDetail(row: Order) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await api.orders.detail(row.id)
  } finally {
    detailLoading.value = false
  }
}

// ========== 删除（仅 ADMIN） ==========
function remove(row: Order) {
  ElMessageBox.confirm(`确认删除「${row.farmerName}」这笔收购单？删除后不可恢复。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
    .then(async () => {
      await api.orders.remove(row.id)
      ElMessage.success('已删除')
      load()
    })
    .catch(() => {})
}

onMounted(async () => {
  load()
  // 预取果品档案（开单/编辑下拉用）
  try {
    fruits.value = await api.fruits.list({})
  } catch {
    fruits.value = []
  }
})
</script>

<template>
  <div>
    <div class="page-header">
      <h3>收购流水</h3>
      <div class="page-header-desc">按日期 / 关键字 / 付款状态查询收购开单记录，支持导出 Excel</div>
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
      <el-button type="primary" :loading="exporting" @click="exportExcel">导出 Excel</el-button>
      <el-input
        v-model="keyword"
        placeholder="果农姓名 / 电话 / 果品"
        clearable
        style="width: 200px"
        @keyup.enter="search"
      />
      <el-select v-model="payStatus" style="width: 140px">
        <el-option v-for="o in payOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
      <div style="flex: 1"></div>
      <el-button type="primary" :icon="Plus" @click="openCreate">开单</el-button>
    </div>

    <!-- 流水表格 -->
    <div class="page-card">
      <el-table v-loading="loading" :data="list" stripe style="width: 100%">
        <el-table-column prop="createdAt" label="开单时间" width="160" />
        <el-table-column label="果品" min-width="140">
          <template #default="{ row }">
            <span>{{ row.fruitName || '-' }}</span>
            <span v-if="row.spec" class="spec-text">（{{ row.spec }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="毛重(斤)" width="90" align="right">
          <template #default="{ row }">{{ fmtWeight(row.grossWeight) }}</template>
        </el-table-column>
        <el-table-column label="皮重(斤)" width="90" align="right">
          <template #default="{ row }">{{ fmtWeight(row.tareWeight) }}</template>
        </el-table-column>
        <el-table-column label="净重(斤)" width="90" align="right">
          <template #default="{ row }">{{ fmtWeight(netWeightOf(row)) }}</template>
        </el-table-column>
        <el-table-column label="单价" width="90" align="right">
          <template #default="{ row }">{{ fmtMoney(row.price) }}</template>
        </el-table-column>
        <el-table-column label="金额(元)" width="110" align="right">
          <template #default="{ row }">
            <b>{{ fmtMoney(amountOf(row)) }}</b>
          </template>
        </el-table-column>
        <el-table-column prop="farmerName" label="果农" width="100" />
        <el-table-column prop="farmerPhone" label="电话" width="120" />
        <el-table-column label="付款" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="payTag(row.payStatus).type" size="small">{{ payTag(row.payStatus).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="支付方式" width="90" align="center">
          <template #default="{ row }">{{ payMethodLabel(row.payMethod) }}</template>
        </el-table-column>
        <el-table-column prop="source" label="来源" width="100" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <template v-if="userStore.isAdmin">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无流水数据" />
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

    <!-- 开单 / 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="24">
            <el-form-item label="果品" prop="fruitId">
              <el-select v-model="form.fruitId" placeholder="选择果品" style="width: 100%" @change="onFruitChange">
                <el-option
                  v-for="f in fruits"
                  :key="f.id"
                  :label="`${f.name}${f.spec ? '（' + f.spec + '）' : ''} ¥${f.price}/${f.unit || '斤'}`"
                  :value="f.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="毛重(斤)" prop="grossWeight">
              <el-input-number v-model="form.grossWeight" :min="0" :precision="2" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="皮重(斤)" prop="tareWeight">
              <el-input-number v-model="form.tareWeight" :min="0" :precision="2" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="单价" prop="price">
              <el-input-number v-model="form.price" :min="0" :precision="2" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="果农姓名" prop="farmerName">
              <el-input v-model="form.farmerName" placeholder="果农姓名" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="电话">
              <el-input v-model="form.farmerPhone" placeholder="联系电话" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="来源">
              <el-input v-model="form.source" placeholder="如：收购点/中介" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="付款状态">
              <el-select v-model="form.payStatus" style="width: 100%">
                <el-option label="已付款" value="PAID" />
                <el-option label="未付款" value="UNPAID" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="支付方式">
              <el-select v-model="form.payMethod" style="width: 100%">
                <el-option label="现金" value="现金" />
                <el-option label="微信" value="微信" />
                <el-option label="支付宝" value="支付宝" />
                <el-option label="转账" value="转账" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="净重/金额">
              <div class="calc-preview">{{ fmtWeight(formNetWeight()) }} 斤 / ¥{{ fmtMoney(formAmount()) }}</div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="磅单照片">
              <el-input v-model="form.photoUrl" placeholder="照片地址（/uploads/xxx.jpg）" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注（可选）" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗（可看磅单照片） -->
    <el-dialog v-model="detailVisible" title="流水详情" width="640px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <div v-if="detail.photoUrl" class="detail-photo">
            <el-image
              :src="photoSrc(detail.photoUrl)"
              :preview-src-list="[photoSrc(detail.photoUrl)]"
              fit="contain"
              style="max-height: 260px; width: 100%"
            />
          </div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="开单时间">{{ detail.createdAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="果品">
              {{ detail.fruitName || '-' }}{{ detail.spec ? `（${detail.spec}）` : '' }}
            </el-descriptions-item>
            <el-descriptions-item label="毛重">{{ fmtWeight(detail.grossWeight) }} 斤</el-descriptions-item>
            <el-descriptions-item label="皮重">{{ fmtWeight(detail.tareWeight) }} 斤</el-descriptions-item>
            <el-descriptions-item label="净重">{{ fmtWeight(netWeightOf(detail)) }} 斤</el-descriptions-item>
            <el-descriptions-item label="单价">{{ fmtMoney(detail.price) }}</el-descriptions-item>
            <el-descriptions-item label="金额">¥ {{ fmtMoney(amountOf(detail)) }}</el-descriptions-item>
            <el-descriptions-item label="付款状态">
              <el-tag :type="payTag(detail.payStatus).type" size="small">{{ payTag(detail.payStatus).label }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="支付方式">{{ payMethodLabel(detail.payMethod) }}</el-descriptions-item>
            <el-descriptions-item label="来源">{{ detail.source || '-' }}</el-descriptions-item>
            <el-descriptions-item label="果农">{{ detail.farmerName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="电话">{{ detail.farmerPhone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.spec-text {
  color: var(--nct-text-sub);
}

.calc-preview {
  font-weight: 600;
  color: var(--nct-primary);
  line-height: 32px;
}

.detail-photo {
  margin-bottom: 14px;
  text-align: center;
  background: #f7f9f8;
  border-radius: 8px;
  padding: 8px;
}
</style>
