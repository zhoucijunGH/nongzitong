<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import api from '@/api'
import type { ReportSummary } from '@/types'
import { fmtMoney, fmtQty } from '@/utils/format'

function dayStr(offset: number) {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

const dateRange = ref<[string, string] | null>([dayStr(-29), dayStr(0)])
const loading = ref(false)
const exporting = ref(false)
const data = ref<ReportSummary | null>(null)

async function load() {
  loading.value = true
  try {
    data.value = await api.reports.summary({
      dateFrom: dateRange.value?.[0] || '',
      dateTo: dateRange.value?.[1] || ''
    })
  } finally {
    loading.value = false
  }
}

function search() {
  load()
}

function reset() {
  dateRange.value = [dayStr(-29), dayStr(0)]
  load()
}

function quick(type: 'today' | 'week' | 'month') {
  if (type === 'today') dateRange.value = [dayStr(0), dayStr(0)]
  else if (type === 'week') dateRange.value = [dayStr(-6), dayStr(0)]
  else dateRange.value = [dayStr(-29), dayStr(0)]
  load()
}

const s = computed(() => data.value?.summary)

async function exportExcel() {
  exporting.value = true
  try {
    const blob = await api.export.report({
      dateFrom: dateRange.value?.[0] || '',
      dateTo: dateRange.value?.[1] || ''
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `经营报表_${dateRange.value ? `${dateRange.value[0]}_${dateRange.value[1]}` : ''}.xlsx`
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-header">
      <h3>经营报表</h3>
      <div class="page-header-desc">区间汇总、按日明细与商品销量 Top，毛利 = 销售额 − 单据进价成本（仅老板可见）</div>
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
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="quick('today')">今日</el-button>
      <el-button @click="quick('week')">近7天</el-button>
      <el-button @click="quick('month')">近30天</el-button>
      <el-button @click="reset">重置</el-button>
      <div style="flex: 1"></div>
      <el-button type="success" :loading="exporting" @click="exportExcel">一键导出 Excel</el-button>
    </div>

    <div v-loading="loading">
      <!-- 汇总卡 -->
      <el-row :gutter="16" class="kpi-row">
        <el-col :xs="12" :md="6">
          <div class="page-card kpi-card">
            <div class="kpi-label">销售额</div>
            <div class="kpi-value">¥ {{ fmtMoney(s?.salesAmount) }}</div>
            <div class="kpi-sub">{{ s?.orderCount ?? 0 }} 单</div>
          </div>
        </el-col>
        <el-col :xs="12" :md="6">
          <div class="page-card kpi-card">
            <div class="kpi-label">毛利</div>
            <div class="kpi-value green">¥ {{ fmtMoney(s?.profit) }}</div>
            <div class="kpi-sub">成本 ¥ {{ fmtMoney(s?.costAmount) }}</div>
          </div>
        </el-col>
        <el-col :xs="12" :md="6">
          <div class="page-card kpi-card">
            <div class="kpi-label">现结 / 挂账</div>
            <div class="kpi-value small">¥ {{ fmtMoney(s?.cashAmount) }} / {{ fmtMoney(s?.creditAmount) }}</div>
            <div class="kpi-sub">收款登记 ¥ {{ fmtMoney(s?.paymentAmount) }}</div>
          </div>
        </el-col>
        <el-col :xs="12" :md="6">
          <div class="page-card kpi-card">
            <div class="kpi-label">新增欠款</div>
            <div class="kpi-value warn">¥ {{ fmtMoney(s?.newDebt) }}</div>
            <div class="kpi-sub">挂账 − 收款</div>
          </div>
        </el-col>
        <el-col :xs="12" :md="6">
          <div class="page-card kpi-card">
            <div class="kpi-label">采购入库</div>
            <div class="kpi-value">¥ {{ fmtMoney(s?.purchaseAmount) }}</div>
            <div class="kpi-sub">{{ s?.purchaseCount ?? 0 }} 单</div>
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <!-- 按日明细 -->
        <el-col :xs="24" :md="14">
          <div class="page-card">
            <div class="chart-title">按日明细</div>
            <el-table :data="data?.byDay || []" stripe max-height="420" style="width: 100%">
              <el-table-column prop="date" label="日期" width="110" />
              <el-table-column prop="count" label="单数" width="70" align="center" />
              <el-table-column label="销售额" width="110" align="right">
                <template #default="{ row }"><b>{{ fmtMoney(row.amount) }}</b></template>
              </el-table-column>
              <el-table-column label="现结" width="100" align="right">
                <template #default="{ row }">{{ fmtMoney(row.cashAmount) }}</template>
              </el-table-column>
              <el-table-column label="挂账" width="100" align="right">
                <template #default="{ row }">{{ fmtMoney(row.creditAmount) }}</template>
              </el-table-column>
              <template #empty>
                <el-empty description="该区间无销售数据" :image-size="70" />
              </template>
            </el-table>
          </div>
        </el-col>

        <!-- 商品销量 Top -->
        <el-col :xs="24" :md="10">
          <div class="page-card">
            <div class="chart-title">商品销量 Top 10</div>
            <el-table :data="data?.topProducts || []" stripe max-height="420" style="width: 100%">
              <el-table-column type="index" label="#" width="46" />
              <el-table-column label="商品" min-width="130">
                <template #default="{ row }">
                  <div class="top-name">{{ row.productName }}</div>
                  <div class="top-spec">{{ row.spec }}</div>
                </template>
              </el-table-column>
              <el-table-column label="销量" width="90" align="right">
                <template #default="{ row }">{{ fmtQty(row.qty) }}</template>
              </el-table-column>
              <el-table-column label="销售额" width="100" align="right">
                <template #default="{ row }">{{ fmtMoney(row.amount) }}</template>
              </el-table-column>
              <el-table-column label="毛利" width="90" align="right">
                <template #default="{ row }"><b class="green">{{ fmtMoney(row.profit) }}</b></template>
              </el-table-column>
              <template #empty>
                <el-empty description="该区间无销售数据" :image-size="70" />
              </template>
            </el-table>
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<style scoped>
.kpi-row {
  margin-bottom: 16px;
}

.kpi-card {
  margin-bottom: 16px;
}

.kpi-label {
  font-size: 13px;
  color: var(--nct-text-sub);
}

.kpi-value {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 700;
  color: var(--nct-text);
}

.kpi-value.small {
  font-size: 16px;
}

.kpi-value.green {
  color: var(--nct-primary, #2f9e62);
}

.kpi-value.warn {
  color: var(--nct-warning, #e6a23c);
}

.kpi-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--nct-text-sub);
}

.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--nct-text);
  margin-bottom: 12px;
}

.top-name {
  font-size: 13px;
  color: var(--nct-text);
}

.top-spec {
  font-size: 12px;
  color: var(--nct-text-sub);
}

.green {
  color: var(--nct-primary, #2f9e62);
}
</style>
