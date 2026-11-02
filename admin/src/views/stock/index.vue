<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '@/api'
import type { Product, StockFlow } from '@/types'
import { fmtMoney, fmtQty } from '@/utils/format'

const activeTab = ref('stock')

// ========== 当前库存 ==========
const keyword = ref('')
const category = ref('')
const lowOnly = ref(false)
const stockList = ref<Product[]>([])
const loading = ref(false)
const exporting = ref(false)

const categoryOptions = [
  { label: '全部类别', value: '' },
  { label: '化肥', value: '化肥' },
  { label: '农药', value: '农药' },
  { label: '种子', value: '种子' },
  { label: '其他', value: '其他' }
]

async function loadStock() {
  loading.value = true
  try {
    stockList.value = await api.stocks.list({
      keyword: keyword.value,
      category: category.value,
      lowOnly: lowOnly.value
    })
  } finally {
    loading.value = false
  }
}

function searchStock() {
  loadStock()
}

function resetStock() {
  keyword.value = ''
  category.value = ''
  lowOnly.value = false
  loadStock()
}

function isLow(p: Product) {
  return Number(p.stock) <= Number(p.warningStock)
}

async function exportExcel() {
  exporting.value = true
  try {
    const blob = await api.export.stock()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '库存台账.xlsx'
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  } finally {
    exporting.value = false
  }
}

// ========== 库存流水 ==========
const flowKeyword = ref('')
const flowType = ref('')
const flowRange = ref<[string, string] | null>(null)
const flowPage = ref(1)
const flowSize = ref(20)
const flowTotal = ref(0)
const flows = ref<StockFlow[]>([])
const flowLoading = ref(false)

const flowTypeOptions = [
  { label: '全部类型', value: '' },
  { label: '入库（+）', value: 'IN' },
  { label: '出库（-）', value: 'OUT' },
  { label: '作废回滚', value: 'VOID' }
]

function flowTypeTag(t: string) {
  const map: Record<string, { label: string; type: 'success' | 'warning' | 'info' }> = {
    IN: { label: '入库', type: 'success' },
    OUT: { label: '出库', type: 'warning' },
    VOID: { label: '作废回滚', type: 'info' }
  }
  return map[t] || { label: t, type: 'info' as const }
}

async function loadFlows() {
  flowLoading.value = true
  try {
    const res = await api.stocks.flows({
      keyword: flowKeyword.value,
      flowType: flowType.value,
      dateFrom: flowRange.value?.[0] || '',
      dateTo: flowRange.value?.[1] || '',
      page: flowPage.value,
      size: flowSize.value
    })
    flows.value = res.list || []
    flowTotal.value = res.total || 0
  } finally {
    flowLoading.value = false
  }
}

function searchFlows() {
  flowPage.value = 1
  loadFlows()
}

function resetFlows() {
  flowKeyword.value = ''
  flowType.value = ''
  flowRange.value = null
  searchFlows()
}

function onFlowPage(p: number) {
  flowPage.value = p
  loadFlows()
}

function onFlowSize(s: number) {
  flowSize.value = s
  flowPage.value = 1
  loadFlows()
}

// 切到流水页签时加载
function onTabChange(tab: string) {
  if (tab === 'flow' && !flows.value.length) loadFlows()
}

onMounted(loadStock)
</script>

<template>
  <div>
    <div class="page-header">
      <h3>库存管理</h3>
      <div class="page-header-desc">当前库存与出入库流水只增不改，低库存标红预警</div>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- 当前库存 -->
      <el-tab-pane label="当前库存" name="stock">
        <div class="page-card query-bar">
          <el-input
            v-model="keyword"
            placeholder="商品名称 / 规格"
            clearable
            style="width: 200px"
            @keyup.enter="searchStock"
          />
          <el-select v-model="category" placeholder="全部类别" style="width: 130px">
            <el-option v-for="o in categoryOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-checkbox v-model="lowOnly">只看低库存</el-checkbox>
          <el-button type="primary" @click="searchStock">查询</el-button>
          <el-button @click="resetStock">重置</el-button>
          <div style="flex: 1"></div>
          <el-button :loading="exporting" @click="exportExcel">导出 Excel</el-button>
        </div>

        <div class="page-card">
          <el-table v-loading="loading" :data="stockList" stripe style="width: 100%">
            <el-table-column prop="name" label="商品名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="spec" label="规格" width="130" show-overflow-tooltip />
            <el-table-column prop="category" label="类别" width="80" align="center" />
            <el-table-column prop="unit" label="单位" width="70" align="center" />
            <el-table-column label="当前库存" width="110" align="right">
              <template #default="{ row }">
                <b :class="{ 'low-num': isLow(row) }">{{ fmtQty(row.stock) }}</b>
              </template>
            </el-table-column>
            <el-table-column label="预警库存" width="100" align="right">
              <template #default="{ row }">{{ fmtQty(row.warningStock) }}</template>
            </el-table-column>
            <el-table-column label="库存状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="isLow(row) ? 'danger' : 'success'" size="small">
                  {{ isLow(row) ? '低库存' : '正常' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="进价" width="100" align="right">
              <template #default="{ row }">{{ fmtMoney(row.purchasePrice) }}</template>
            </el-table-column>
            <el-table-column label="售价" width="100" align="right">
              <template #default="{ row }">{{ fmtMoney(row.salePrice) }}</template>
            </el-table-column>
            <el-table-column label="库存金额" width="120" align="right">
              <template #default="{ row }">
                {{ fmtMoney(Number(row.stock) * Number(row.purchasePrice)) }}
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无商品" />
            </template>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- 库存流水 -->
      <el-tab-pane label="库存流水" name="flow">
        <div class="page-card query-bar">
          <el-date-picker
            v-model="flowRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :clearable="true"
          />
          <el-input
            v-model="flowKeyword"
            placeholder="商品 / 关联单号"
            clearable
            style="width: 190px"
            @keyup.enter="searchFlows"
          />
          <el-select v-model="flowType" placeholder="全部类型" style="width: 140px">
            <el-option v-for="o in flowTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-button type="primary" @click="searchFlows">查询</el-button>
          <el-button @click="resetFlows">重置</el-button>
        </div>

        <div class="page-card">
          <el-table v-loading="flowLoading" :data="flows" stripe style="width: 100%">
            <el-table-column prop="createdAt" label="时间" width="160" />
            <el-table-column prop="productName" label="商品" min-width="150" show-overflow-tooltip />
            <el-table-column prop="spec" label="规格" width="120" show-overflow-tooltip />
            <el-table-column label="类型" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="flowTypeTag(row.flowType).type" size="small">
                  {{ flowTypeTag(row.flowType).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="变动数量" width="110" align="right">
              <template #default="{ row }">
                <b :class="Number(row.delta) >= 0 ? 'delta-in' : 'delta-out'">
                  {{ Number(row.delta) >= 0 ? '+' : '' }}{{ fmtQty(row.delta) }}
                </b>
              </template>
            </el-table-column>
            <el-table-column label="结存" width="100" align="right">
              <template #default="{ row }">{{ fmtQty(row.afterStock) }}</template>
            </el-table-column>
            <el-table-column prop="refNo" label="关联单号" width="150" />
            <el-table-column prop="operator" label="操作人" width="90" />
            <template #empty>
              <el-empty description="暂无流水" />
            </template>
          </el-table>

          <div class="table-toolbar">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :total="flowTotal"
              :current-page="flowPage"
              :page-size="flowSize"
              :page-sizes="[10, 20, 50, 100]"
              @current-change="onFlowPage"
              @size-change="onFlowSize"
            />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.low-num {
  color: #e05050;
}

.delta-in {
  color: #2f9e62;
}

.delta-out {
  color: #e6a23c;
}
</style>
