<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import api from '@/api'
import type { Customer, Product, Supplier } from '@/types'
import { useUserStore } from '@/stores/user'
import { fmtMoney, fmtQty } from '@/utils/format'

const userStore = useUserStore()
const activeTab = ref('product')

const categoryOptions = [
  { label: '全部类别', value: '' },
  { label: '化肥', value: '化肥' },
  { label: '农药', value: '农药' },
  { label: '种子', value: '种子' },
  { label: '其他', value: '其他' }
]

// ========== 商品档案 ==========
const products = ref<Product[]>([])
const pLoading = ref(false)
const pKeyword = ref('')
const pCategory = ref('')

async function loadProducts() {
  pLoading.value = true
  try {
    products.value = await api.products.list({ keyword: pKeyword.value, category: pCategory.value })
  } finally {
    pLoading.value = false
  }
}

const pDialog = ref(false)
const pTitle = ref('新增商品')
const pEditingId = ref<number | null>(null)
const pSaving = ref(false)
const pFormRef = ref<FormInstance>()

const pForm = reactive({
  name: '',
  spec: '',
  unit: '袋',
  category: '化肥',
  purchasePrice: 0,
  salePrice: 0,
  warningStock: 10,
  openingStock: 0,
  status: 1,
  remark: ''
})

const pRules: FormRules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  spec: [{ required: true, message: '请输入规格', trigger: 'blur' }],
  unit: [{ required: true, message: '请输入单位', trigger: 'blur' }],
  category: [{ required: true, message: '请选择类别', trigger: 'change' }]
}

function openPCreate() {
  pEditingId.value = null
  pTitle.value = '新增商品'
  Object.assign(pForm, {
    name: '',
    spec: '',
    unit: '袋',
    category: '化肥',
    purchasePrice: 0,
    salePrice: 0,
    warningStock: 10,
    openingStock: 0,
    status: 1,
    remark: ''
  })
  pDialog.value = true
}

function openPEdit(row: Product) {
  pEditingId.value = row.id
  pTitle.value = '编辑商品'
  Object.assign(pForm, {
    name: row.name,
    spec: row.spec,
    unit: row.unit,
    category: row.category,
    purchasePrice: Number(row.purchasePrice),
    salePrice: Number(row.salePrice),
    warningStock: Number(row.warningStock),
    openingStock: Number(row.openingStock || 0),
    status: row.status,
    remark: row.remark || ''
  })
  pDialog.value = true
}

async function saveProduct() {
  if (!pFormRef.value) return
  try {
    await pFormRef.value.validate()
  } catch {
    return
  }
  pSaving.value = true
  try {
    if (pEditingId.value != null) {
      await api.products.update(pEditingId.value, { ...pForm })
      ElMessage.success('修改成功')
    } else {
      await api.products.create({ ...pForm })
      ElMessage.success('新增成功，期初库存已建档')
    }
    pDialog.value = false
    loadProducts()
  } finally {
    pSaving.value = false
  }
}

function removeProduct(row: Product) {
  ElMessageBox.confirm(`确认删除商品「${row.name}」？有业务单据的商品将无法删除`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
    .then(async () => {
      await api.products.remove(row.id)
      ElMessage.success('已删除')
      loadProducts()
    })
    .catch(() => {})
}

// ========== 供应商档案 ==========
const suppliers = ref<Supplier[]>([])
const sLoading = ref(false)

async function loadSuppliers() {
  sLoading.value = true
  try {
    suppliers.value = await api.suppliers.list()
  } finally {
    sLoading.value = false
  }
}

const sDialog = ref(false)
const sTitle = ref('新增供应商')
const sEditingId = ref<number | null>(null)
const sSaving = ref(false)
const sFormRef = ref<FormInstance>()
const sForm = reactive({ name: '', contact: '', phone: '', remark: '' })
const sRules: FormRules = {
  name: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }]
}

function openSCreate() {
  sEditingId.value = null
  sTitle.value = '新增供应商'
  Object.assign(sForm, { name: '', contact: '', phone: '', remark: '' })
  sDialog.value = true
}

function openSEdit(row: Supplier) {
  sEditingId.value = row.id
  sTitle.value = '编辑供应商'
  Object.assign(sForm, {
    name: row.name,
    contact: row.contact || '',
    phone: row.phone || '',
    remark: row.remark || ''
  })
  sDialog.value = true
}

async function saveSupplier() {
  if (!sFormRef.value) return
  try {
    await sFormRef.value.validate()
  } catch {
    return
  }
  sSaving.value = true
  try {
    if (sEditingId.value != null) {
      await api.suppliers.update(sEditingId.value, { ...sForm })
      ElMessage.success('修改成功')
    } else {
      await api.suppliers.create({ ...sForm })
      ElMessage.success('新增成功')
    }
    sDialog.value = false
    loadSuppliers()
  } finally {
    sSaving.value = false
  }
}

function removeSupplier(row: Supplier) {
  ElMessageBox.confirm(`确认删除供应商「${row.name}」？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
    .then(async () => {
      await api.suppliers.remove(row.id)
      ElMessage.success('已删除')
      loadSuppliers()
    })
    .catch(() => {})
}

// ========== 客户档案 ==========
const customers = ref<Customer[]>([])
const cLoading = ref(false)

async function loadCustomers() {
  cLoading.value = true
  try {
    customers.value = await api.customers.list({})
  } finally {
    cLoading.value = false
  }
}

const cDialog = ref(false)
const cTitle = ref('新增客户')
const cEditingId = ref<number | null>(null)
const cSaving = ref(false)
const cFormRef = ref<FormInstance>()
const cForm = reactive({ name: '', phone: '', address: '', remark: '', status: 1 })
const cRules: FormRules = {
  name: [{ required: true, message: '请输入客户姓名', trigger: 'blur' }]
}

function openCCreate() {
  cEditingId.value = null
  cTitle.value = '新增客户'
  Object.assign(cForm, { name: '', phone: '', address: '', remark: '', status: 1 })
  cDialog.value = true
}

function openCEdit(row: Customer) {
  cEditingId.value = row.id
  cTitle.value = '编辑客户'
  Object.assign(cForm, {
    name: row.name,
    phone: row.phone || '',
    address: row.address || '',
    remark: row.remark || '',
    status: row.status
  })
  cDialog.value = true
}

async function saveCustomer() {
  if (!cFormRef.value) return
  try {
    await cFormRef.value.validate()
  } catch {
    return
  }
  cSaving.value = true
  try {
    if (cEditingId.value != null) {
      await api.customers.update(cEditingId.value, { ...cForm })
      ElMessage.success('修改成功')
    } else {
      await api.customers.create({ ...cForm })
      ElMessage.success('新增成功')
    }
    cDialog.value = false
    loadCustomers()
  } finally {
    cSaving.value = false
  }
}

function removeCustomer(row: Customer) {
  ElMessageBox.confirm(`确认删除客户「${row.name}」？有欠款的客户将无法删除`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
    .then(async () => {
      await api.customers.remove(row.id)
      ElMessage.success('已删除')
      loadCustomers()
    })
    .catch(() => {})
}

function onTabChange(tab: string | number) {
  if (tab === 'supplier' && !suppliers.value.length) loadSuppliers()
  if (tab === 'customer' && !customers.value.length) loadCustomers()
}

onMounted(loadProducts)
</script>

<template>
  <div>
    <div class="page-header">
      <h3>档案管理</h3>
      <div class="page-header-desc">商品 / 供应商 / 客户三类基础档案（档案维护仅老板可操作）</div>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- 商品档案 -->
      <el-tab-pane label="商品档案" name="product">
        <div class="page-card query-bar">
          <el-input
            v-model="pKeyword"
            placeholder="商品名称 / 规格"
            clearable
            style="width: 200px"
            :prefix-icon="Search"
            @keyup.enter="loadProducts"
          />
          <el-select v-model="pCategory" placeholder="全部类别" style="width: 130px">
            <el-option v-for="o in categoryOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-button type="primary" @click="loadProducts">查询</el-button>
          <div style="flex: 1"></div>
          <el-button v-if="userStore.isAdmin" type="primary" :icon="Plus" @click="openPCreate">
            新增商品
          </el-button>
        </div>

        <div class="page-card">
          <el-table v-loading="pLoading" :data="products" stripe style="width: 100%">
            <el-table-column prop="name" label="商品名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="spec" label="规格" width="130" show-overflow-tooltip />
            <el-table-column prop="category" label="类别" width="80" align="center" />
            <el-table-column prop="unit" label="单位" width="70" align="center" />
            <el-table-column label="进价" width="90" align="right">
              <template #default="{ row }">{{ fmtMoney(row.purchasePrice) }}</template>
            </el-table-column>
            <el-table-column label="售价" width="90" align="right">
              <template #default="{ row }">{{ fmtMoney(row.salePrice) }}</template>
            </el-table-column>
            <el-table-column label="预警库存" width="90" align="right">
              <template #default="{ row }">{{ fmtQty(row.warningStock) }}</template>
            </el-table-column>
            <el-table-column label="当前库存" width="100" align="right">
              <template #default="{ row }">
                <b :class="{ 'low-num': Number(row.stock) <= Number(row.warningStock) }">
                  {{ fmtQty(row.stock) }}
                </b>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="userStore.isAdmin" label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openPEdit(row)">编辑</el-button>
                <el-button link type="danger" @click="removeProduct(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无商品档案" />
            </template>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- 供应商档案 -->
      <el-tab-pane label="供应商档案" name="supplier">
        <div class="page-card query-bar">
          <div style="flex: 1"></div>
          <el-button v-if="userStore.isAdmin" type="primary" :icon="Plus" @click="openSCreate">
            新增供应商
          </el-button>
        </div>
        <div class="page-card">
          <el-table v-loading="sLoading" :data="suppliers" stripe style="width: 100%">
            <el-table-column prop="name" label="供应商名称" min-width="180" show-overflow-tooltip />
            <el-table-column prop="contact" label="联系人" width="120">
              <template #default="{ row }">{{ row.contact || '-' }}</template>
            </el-table-column>
            <el-table-column prop="phone" label="电话" width="140">
              <template #default="{ row }">{{ row.phone || '-' }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
            <el-table-column v-if="userStore.isAdmin" label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openSEdit(row)">编辑</el-button>
                <el-button link type="danger" @click="removeSupplier(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无供应商" />
            </template>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- 客户档案 -->
      <el-tab-pane label="客户档案" name="customer">
        <div class="page-card query-bar">
          <div style="flex: 1"></div>
          <el-button v-if="userStore.isAdmin" type="primary" :icon="Plus" @click="openCCreate">
            新增客户
          </el-button>
        </div>
        <div class="page-card">
          <el-table v-loading="cLoading" :data="customers" stripe style="width: 100%">
            <el-table-column prop="name" label="客户姓名" min-width="130" show-overflow-tooltip />
            <el-table-column prop="phone" label="电话" width="140">
              <template #default="{ row }">{{ row.phone || '-' }}</template>
            </el-table-column>
            <el-table-column prop="address" label="地址" min-width="170" show-overflow-tooltip />
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? '正常' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="userStore.isAdmin" label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openCEdit(row)">编辑</el-button>
                <el-button link type="danger" @click="removeCustomer(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无客户" />
            </template>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 商品弹窗 -->
    <el-dialog v-model="pDialog" :title="pTitle" width="560px" destroy-on-close>
      <el-form ref="pFormRef" :model="pForm" :rules="pRules" label-width="90px">
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="pForm.name" placeholder="如：尿素" clearable />
        </el-form-item>
        <el-form-item label="规格" prop="spec">
          <el-input v-model="pForm.spec" placeholder="如：50kg/袋" clearable />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="单位" prop="unit">
              <el-input v-model="pForm.unit" placeholder="袋 / 瓶 / 包" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类别" prop="category">
              <el-select v-model="pForm.category" style="width: 100%">
                <el-option
                  v-for="o in categoryOptions.filter((x) => x.value)"
                  :key="o.value"
                  :label="o.label"
                  :value="o.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="进价">
              <el-input-number v-model="pForm.purchasePrice" :min="0" :precision="2" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="售价">
              <el-input-number v-model="pForm.salePrice" :min="0" :precision="2" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="预警库存">
              <el-input-number v-model="pForm.warningStock" :min="0" :precision="2" :step="5" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="pEditingId == null ? '期初库存' : '当前库存'">
              <el-input-number
                v-model="pForm.openingStock"
                :min="0"
                :precision="2"
                :step="10"
                :disabled="pEditingId != null"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态">
          <el-radio-group v-model="pForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="pForm.remark" placeholder="选填" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pDialog = false">取消</el-button>
        <el-button type="primary" :loading="pSaving" @click="saveProduct">保存</el-button>
      </template>
    </el-dialog>

    <!-- 供应商弹窗 -->
    <el-dialog v-model="sDialog" :title="sTitle" width="480px" destroy-on-close>
      <el-form ref="sFormRef" :model="sForm" :rules="sRules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="sForm.name" placeholder="供应商名称" clearable />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="sForm.contact" placeholder="选填" clearable />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="sForm.phone" placeholder="选填" clearable />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="sForm.remark" placeholder="选填" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sDialog = false">取消</el-button>
        <el-button type="primary" :loading="sSaving" @click="saveSupplier">保存</el-button>
      </template>
    </el-dialog>

    <!-- 客户弹窗 -->
    <el-dialog v-model="cDialog" :title="cTitle" width="520px" destroy-on-close>
      <el-form ref="cFormRef" :model="cForm" :rules="cRules" label-width="80px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="cForm.name" placeholder="客户姓名" clearable />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="cForm.phone" placeholder="选填" clearable />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="cForm.address" placeholder="选填" clearable />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="cForm.remark" placeholder="选填" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="cForm.status">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cDialog = false">取消</el-button>
        <el-button type="primary" :loading="cSaving" @click="saveCustomer">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.low-num {
  color: #e05050;
}
</style>
