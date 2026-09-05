<template>
  <div class="page-shell">
    <a-card class="content-card" :bordered="false">
      <div class="page-toolbar">
        <div class="page-toolbar__title">
          <h2>服务管理</h2>
          <p>配置平台可售打印服务与价格</p>
        </div>
        <div class="toolbar-actions">
          <a-input
            v-model:value="searchKeyword"
            allow-clear
            placeholder="搜索服务名称或分类"
            style="width: 230px"
          >
            <template #prefix><SearchOutlined /></template>
          </a-input>
          <a-button :loading="loading" @click="loadServices"><ReloadOutlined />刷新</a-button>
          <a-button type="primary" @click="showAddDialog"><PlusOutlined />新增服务</a-button>
        </div>
      </div>

      <a-table
        :columns="columns"
        :data-source="filteredServices"
        :loading="loading"
        row-key="id"
        :scroll="{ x: 920 }"
        :pagination="{ pageSize: 10, showSizeChanger: false }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <div class="service-name-cell">
              <span class="service-list-icon"><PrinterOutlined /></span>
              <div>
                <strong>{{ record.name }}</strong>
                <small>ID: {{ record.id }}</small>
              </div>
            </div>
          </template>
          <template v-else-if="column.key === 'category'">
            <a-tag :color="getCategoryColor(record.category)">{{ record.category || '未分类' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'description'">
            <span class="service-description">{{ record.description || '暂无服务描述' }}</span>
          </template>
          <template v-else-if="column.key === 'price'">
            <span class="price-text">￥{{ formatPrice(record.price) }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <div class="table-actions">
              <a-button type="link" size="small" @click="editService(record)">编辑</a-button>
              <a-button type="link" danger size="small" @click="deleteService(record.id)">删除</a-button>
            </div>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="dialogVisible"
      :title="isEdit ? '编辑服务' : '新增服务'"
      ok-text="保存"
      cancel-text="取消"
      @ok="saveService"
    >
      <a-form layout="vertical">
        <a-form-item label="服务名称" required>
          <a-input v-model:value="form.name" placeholder="例如：A4 文档打印" />
        </a-form-item>
        <a-form-item label="服务分类" required>
          <a-select v-model:value="form.category" placeholder="请选择分类">
            <a-select-option value="文件打印">文件打印</a-select-option>
            <a-select-option value="照片打印">照片打印</a-select-option>
            <a-select-option value="证件照">证件照</a-select-option>
            <a-select-option value="复印扫描">复印扫描</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="价格" required>
          <a-input-number v-model:value="form.price" :min="0" :precision="2" addon-before="￥" style="width: 100%" />
        </a-form-item>
        <a-form-item label="服务描述">
          <a-textarea v-model:value="form.description" :rows="4" placeholder="请输入服务说明" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, PrinterOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { serviceApi } from '@/api'

const loading = ref(false)
const services = ref([])
const searchKeyword = ref('')
const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref({ id: null, name: '', category: '', price: 0, description: '' })

const columns = [
  { title: '服务名称', key: 'name', width: 250, fixed: 'left' },
  { title: '服务分类', key: 'category', width: 140 },
  { title: '服务描述', key: 'description' },
  { title: '价格', key: 'price', width: 120, sorter: (a, b) => Number(a.price || 0) - Number(b.price || 0) },
  { title: '操作', key: 'action', width: 140, fixed: 'right' }
]

const filteredServices = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) return services.value
  return services.value.filter(service =>
    [service.name, service.category, service.description]
      .some(value => String(value || '').toLowerCase().includes(keyword))
  )
})

const getCategoryColor = category => ({
  文件打印: 'blue',
  照片打印: 'purple',
  证件照: 'cyan',
  复印扫描: 'green',
  文档: 'blue',
  照片: 'purple'
}[category] || 'default')

const formatPrice = price => Number(price || 0).toFixed(2).replace(/\.00$/, '')

const loadServices = async () => {
  loading.value = true
  try {
    services.value = await serviceApi.getAll() || []
  } finally {
    loading.value = false
  }
}

const showAddDialog = () => {
  isEdit.value = false
  form.value = { id: null, name: '', category: '', price: 0, description: '' }
  dialogVisible.value = true
}

const editService = service => {
  isEdit.value = true
  form.value = { ...service }
  dialogVisible.value = true
}

const saveService = async () => {
  if (!form.value.name?.trim()) return message.warning('请输入服务名称')
  if (!form.value.category) return message.warning('请选择服务分类')
  if (Number(form.value.price) < 0) return message.warning('请输入有效价格')
  if (isEdit.value) await serviceApi.update(form.value)
  else await serviceApi.create(form.value)
  message.success('保存成功')
  dialogVisible.value = false
  loadServices()
}

const deleteService = id => Modal.confirm({
  title: '删除服务',
  content: '确定删除该服务吗？',
  okText: '删除',
  okType: 'danger',
  cancelText: '取消',
  onOk: async () => {
    await serviceApi.delete(id)
    message.success('删除成功')
    loadServices()
  }
})

onMounted(loadServices)
</script>

<style scoped lang="less">
.service-name-cell { display: flex; align-items: center; gap: 11px; }
.service-list-icon { width: 34px; height: 34px; flex: 0 0 34px; display: grid; place-items: center; border: 1px solid #bae0ff; border-radius: 4px; color: #1677ff; background: #e6f4ff; font-size: 17px; }
.service-name-cell > div { min-width: 0; display: flex; flex-direction: column; }
.service-name-cell strong { overflow: hidden; color: #262626; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.service-name-cell small { color: #8c8c8c; font-size: 11px; }
.service-description { display: block; max-width: 520px; overflow: hidden; color: #595959; text-overflow: ellipsis; white-space: nowrap; }
.price-text { font-size: 14px; }
</style>
