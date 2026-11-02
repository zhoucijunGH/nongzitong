<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '@/api'
import type { DebtItem } from '@/types'
import { fmtMoney } from '@/utils/format'

const keyword = ref('')
const loading = ref(false)
const list = ref<DebtItem[]>([])

async function load() {
  loading.value = true
  try {
    list.value = await api.debts.list(keyword.value)
  } finally {
    loading.value = false
  }
}

function search() {
  load()
}

function reset() {
  keyword.value = ''
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-header">
      <h3>欠款汇总</h3>
      <div class="page-header-desc">按果农汇总未付款单据与欠款金额</div>
    </div>

    <!-- 关键字搜索 -->
    <div class="page-card query-bar">
      <el-input
        v-model="keyword"
        placeholder="果农姓名 / 电话"
        clearable
        style="width: 240px"
        @keyup.enter="search"
      />
      <el-button type="primary" @click="search">搜索</el-button>
      <el-button @click="reset">重置</el-button>
      <div style="flex: 1"></div>
      <span class="total-text">共 {{ list.length }} 位果农有欠款</span>
    </div>

    <!-- 欠款表格 -->
    <div class="page-card">
      <el-table v-loading="loading" :data="list" stripe style="width: 100%">
        <el-table-column prop="farmerName" label="果农" min-width="140" />
        <el-table-column prop="phone" label="电话" min-width="140">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column prop="unpaidCount" label="未付单数" width="120" align="right" />
        <el-table-column label="未付金额(元)" width="160" align="right">
          <template #default="{ row }">
            <b class="warn-text">{{ fmtMoney(row.unpaidAmount) }}</b>
          </template>
        </el-table-column>
        <el-table-column prop="lastDate" label="最近欠款日期" width="150">
          <template #default="{ row }">{{ row.lastDate || '-' }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无欠款记录" />
        </template>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.total-text {
  font-size: 13px;
  color: var(--nct-text-sub);
}

.warn-text {
  color: var(--nct-warning);
}
</style>
