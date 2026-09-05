<template>
  <div class="page-shell">
    <a-alert v-if="filterInfo" :message="filterInfo" type="info" show-icon closable class="filter-alert" @close="clearFilter" />
    <a-card class="content-card" :bordered="false">
      <div class="page-toolbar">
        <div class="page-toolbar__title"><h2>订单管理</h2><p>查看、筛选并处理平台打印订单</p></div>
        <div class="toolbar-actions">
          <a-input-search v-model:value="searchKeyword" placeholder="订单号、取件码或用户" allow-clear style="width: 230px" />
          <a-select v-model:value="filterStatus" placeholder="全部状态" allow-clear style="width: 130px">
            <a-select-option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option>
          </a-select>
          <a-button @click="loadOrders"><ReloadOutlined />刷新</a-button>
        </div>
      </div>

      <a-table class="desktop-table" :columns="columns" :data-source="filteredOrders" :loading="loading" row-key="id" :scroll="{ x: 1180 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'user'">{{ record.userName || '未设置' }}</template>
          <template v-else-if="column.key === 'print'">{{ getPrintSummary(record) }}</template>
          <template v-else-if="column.key === 'store'">{{ record.storeName || '未设置' }}</template>
          <template v-else-if="column.key === 'price'"><span class="price-text">￥{{ record.totalPrice }}</span></template>
          <template v-else-if="column.key === 'status'"><a-tag :color="getStatusColor(record.orderStatus)">{{ getStatusText(record.orderStatus) }}</a-tag></template>
          <template v-else-if="column.key === 'action'">
            <div class="table-actions">
              <a-button type="link" size="small" @click="viewDetail(record.id)">详情</a-button>
              <a-button v-if="record.orderStatus < 3" v-permission="'print:order:update'" type="link" size="small" @click="updateStatus(record)">更新状态</a-button>
              <a-button v-else v-permission="'print:order:delete'" type="link" danger size="small" @click="deleteOrder(record)">删除</a-button>
            </div>
          </template>
        </template>
      </a-table>

      <div class="mobile-list">
        <a-card v-for="order in filteredOrders" :key="order.id" size="small" class="mobile-order-card">
          <div class="mobile-card-head"><strong>{{ order.queueNumber }}</strong><a-tag :color="getStatusColor(order.orderStatus)">{{ getStatusText(order.orderStatus) }}</a-tag></div>
          <div class="mobile-card-row"><span>用户</span><b>{{ order.userName || '未设置' }}</b></div>
          <div class="mobile-card-row"><span>服务</span><b>{{ order.serviceName || '未设置' }}</b></div>
          <div class="mobile-card-row"><span>参数</span><b>{{ getPrintSummary(order) }}</b></div>
          <div class="mobile-card-row"><span>金额</span><b class="price-text">￥{{ order.totalPrice }}</b></div>
          <div class="mobile-card-actions">
            <a-button size="small" @click="viewDetail(order.id)">查看详情</a-button>
            <a-button v-if="order.orderStatus < 3" v-permission="'print:order:update'" type="primary" size="small" @click="updateStatus(order)">更新状态</a-button>
            <a-button v-else v-permission="'print:order:delete'" danger size="small" @click="deleteOrder(order)">删除</a-button>
          </div>
        </a-card>
      </div>
    </a-card>

    <a-modal v-model:open="statusDialogVisible" title="更新订单状态" ok-text="确认更新" cancel-text="取消" @ok="confirmUpdateStatus">
      <a-form layout="vertical">
        <a-form-item label="订单号"><a-input :value="currentOrder.queueNumber" disabled /></a-form-item>
        <a-form-item label="新状态">
          <a-select v-model:value="newStatus" style="width: 100%">
            <a-select-option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="取件码"><a-input v-model:value="fetchCode" placeholder="订单待取件时可填写取件码" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { orderApi } from '@/api'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const orders = ref([])
const searchKeyword = ref('')
const filterStatus = ref(route.query.status !== undefined ? Number(route.query.status) : undefined)
const filterInfo = ref('')
const statusDialogVisible = ref(false)
const currentOrder = ref({})
const newStatus = ref(0)
const fetchCode = ref('')
const statusOptions = [
  { value: 0, label: '待处理' }, { value: 1, label: '打印中' }, { value: 2, label: '待取件' },
  { value: 3, label: '已完成' }, { value: 4, label: '已取消' }
]
const columns = [
  { title: '订单号', dataIndex: 'queueNumber', key: 'queueNumber', width: 145, fixed: 'left' },
  { title: '用户', key: 'user', width: 110 }, { title: '服务项目', dataIndex: 'serviceName', key: 'serviceName', width: 130 },
  { title: '打印参数', key: 'print', width: 260 }, { title: '门店', key: 'store', width: 140 },
  { title: '金额', key: 'price', width: 90 }, { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'action', width: 170, fixed: 'right' }
]
const getStatusText = status => statusOptions.find(item => item.value === status)?.label || '未知'
const getStatusColor = status => ({ 0: 'orange', 1: 'blue', 2: 'cyan', 3: 'green', 4: 'red' }[status] || 'default')
const getColorModeText = mode => ({ BLACK_WHITE: '黑白', COLOR: '彩色' }[mode] || '黑白')
const getPaperSizeText = size => ({ A4: 'A4', A3: 'A3', PHOTO_6IN: '6寸照片', ID_PHOTO: '证件照' }[size] || 'A4')
const getPrintSummary = order => `${getPaperSizeText(order?.paperSize)} · ${getColorModeText(order?.colorMode)} · ${order?.duplex === 1 ? '双面' : '单面'} · ${order?.pageCount || 1}页 × ${order?.copies || 1}份`

const filteredOrders = computed(() => orders.value.filter(order => {
  const userId = route.query.userId
  if (userId && String(order.userId) !== String(userId)) return false
  if (filterStatus.value !== undefined && filterStatus.value !== null && order.orderStatus !== filterStatus.value) return false
  if (!searchKeyword.value) return true
  const keyword = searchKeyword.value.toLowerCase()
  return [order.queueNumber, order.fetchCode, order.userName].some(value => String(value || '').toLowerCase().includes(keyword))
}))

const loadOrders = async () => {
  loading.value = true
  try {
    orders.value = await orderApi.getAll() || []
    if (route.query.userId) {
      const user = orders.value.find(item => String(item.userId) === String(route.query.userId))
      filterInfo.value = `正在查看用户“${user?.userName || route.query.userId}”的订单`
    } else if (route.query.status !== undefined) filterInfo.value = `正在查看${getStatusText(Number(route.query.status))}订单`
    else filterInfo.value = ''
  } finally { loading.value = false }
}
const clearFilter = async () => { filterInfo.value = ''; filterStatus.value = undefined; searchKeyword.value = ''; await router.replace('/orders'); loadOrders() }
const viewDetail = id => router.push(`/orders/${id}`)
const updateStatus = order => { currentOrder.value = order; newStatus.value = order.orderStatus; fetchCode.value = order.fetchCode || ''; statusDialogVisible.value = true }
const confirmUpdateStatus = async () => { await orderApi.updateStatus(currentOrder.value.id, newStatus.value, fetchCode.value); message.success('状态更新成功'); statusDialogVisible.value = false; loadOrders() }
const deleteOrder = order => Modal.confirm({ title: '删除订单', content: `确定删除订单 ${order.queueNumber} 吗？删除后无法恢复。`, okText: '删除', okType: 'danger', cancelText: '取消', onOk: async () => { await orderApi.deleteOrder(order.id); message.success('订单删除成功'); loadOrders() } })
onMounted(loadOrders)
</script>

<style scoped lang="less">
.filter-alert { margin-bottom: 12px; }
.mobile-order-card { border: 1px solid #e5e7eb; }
.mobile-card-head, .mobile-card-row, .mobile-card-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.mobile-card-head { margin-bottom: 10px; }
.mobile-card-row { padding: 5px 0; font-size: 13px; }
.mobile-card-row span { color: #8c8c8c; }
.mobile-card-row b { max-width: 70%; color: #404040; text-align: right; font-weight: 500; }
.mobile-card-actions { justify-content: flex-end; margin-top: 10px; padding-top: 10px; border-top: 1px solid #f0f0f0; }
</style>
