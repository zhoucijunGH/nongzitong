<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '@/api'
import type { DashboardData } from '@/types'
import { TrendChart } from '@/components/charts'
import { fmtMoney, fmtWeight } from '@/utils/format'

const loading = ref(false)
const data = ref<DashboardData | null>(null)

onMounted(async () => {
  loading.value = true
  try {
    data.value = await api.dashboard.get()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading">
    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :xs="12" :sm="6">
        <div class="page-card kpi-card">
          <div class="kpi-label">今日收购额</div>
          <div class="kpi-value">¥ {{ fmtMoney(data?.todayAmount) }}</div>
          <div class="kpi-sub">净重 {{ fmtWeight(data?.todayNetWeight) }} 斤</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="page-card kpi-card">
          <div class="kpi-label">今日单数</div>
          <div class="kpi-value">{{ data?.todayCount ?? 0 }}</div>
          <div class="kpi-sub">今日开单笔数</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="page-card kpi-card">
          <div class="kpi-label">待付欠款额</div>
          <div class="kpi-value warn">¥ {{ fmtMoney(data?.unpaidAmount) }}</div>
          <div class="kpi-sub">未付款累计金额</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="page-card kpi-card">
          <div class="kpi-label">待付单数</div>
          <div class="kpi-value warn">{{ data?.unpaidCount ?? 0 }}</div>
          <div class="kpi-sub">未付款单据笔数</div>
        </div>
      </el-col>
    </el-row>

    <!-- 近 7 天收购额趋势 -->
    <div class="page-card">
      <div class="chart-title">近 7 天收购额趋势</div>
      <TrendChart :rows="data?.trend || []" />
    </div>
  </div>
</template>

<style scoped>
.kpi-row {
  margin-bottom: 16px;
}

.kpi-card {
  margin-bottom: 0;
}

.kpi-value.warn {
  color: var(--nct-warning);
}

.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--nct-text);
  margin-bottom: 12px;
}
</style>
