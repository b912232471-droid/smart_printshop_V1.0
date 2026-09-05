<template>
  <div class="dashboard page-shell">
    <div class="welcome-row">
      <div>
        <h2>{{ greeting }}，{{ username }}</h2>
        <p>这里是智慧打印平台的今日运营概览。</p>
      </div>
      <a-button type="primary" @click="router.push('/orders')"><FileTextOutlined />查看全部订单</a-button>
    </div>

    <a-row :gutter="[16, 16]" class="stats-row">
      <a-col v-for="item in statsCards" :key="item.label" :xs="12" :sm="12" :lg="6">
        <a-card class="stat-card" :bordered="false" @click="item.action">
          <div class="stat-icon" :class="item.color"><component :is="item.icon" /></div>
          <div class="stat-data">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <small>{{ item.description }}</small>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :xl="17">
        <a-card title="最近订单" class="data-card" :bordered="false">
          <template #extra><a @click="router.push('/orders')">查看更多</a></template>
          <a-table
            :columns="columns"
            :data-source="recentOrders"
            :loading="loading"
            :pagination="false"
            row-key="id"
            :scroll="{ x: 720 }"
            size="middle"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'price'"><span class="price-text">￥{{ record.totalPrice }}</span></template>
              <template v-else-if="column.key === 'status'">
                <a-tag :color="getStatusColor(record.orderStatus)">{{ getStatusText(record.orderStatus) }}</a-tag>
              </template>
              <template v-else-if="column.key === 'action'"><a @click="router.push(`/orders/${record.id}`)">查看详情</a></template>
            </template>
          </a-table>
        </a-card>
      </a-col>

      <a-col :xs="24" :xl="7">
        <a-card title="快捷入口" class="data-card shortcut-card" :bordered="false">
          <button v-for="item in shortcuts" :key="item.label" type="button" @click="router.push(item.path)">
            <span class="shortcut-icon"><component :is="item.icon" /></span>
            <span><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span>
            <RightOutlined />
          </button>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { AppstoreOutlined, CheckCircleOutlined, ClockCircleOutlined, EnvironmentOutlined, FileTextOutlined, PayCircleOutlined, RightOutlined, TeamOutlined } from '@ant-design/icons-vue'
import { orderApi } from '@/api'

const router = useRouter()
const username = localStorage.getItem('admin_user') || '管理员'
const loading = ref(false)
const orders = ref([])
const greeting = computed(() => new Date().getHours() < 12 ? '早上好' : new Date().getHours() < 18 ? '下午好' : '晚上好')
const stats = computed(() => ({
  total: orders.value.length,
  completed: orders.value.filter(item => item.orderStatus === 3).length,
  pending: orders.value.filter(item => item.orderStatus === 0).length,
  amount: orders.value.reduce((sum, item) => sum + Number(item.totalPrice || 0), 0).toFixed(2)
}))
const statsCards = computed(() => [
  { label: '总订单', value: stats.value.total, description: '平台累计订单', color: 'blue', icon: FileTextOutlined, action: () => router.push('/orders') },
  { label: '已完成', value: stats.value.completed, description: '已完成打印交付', color: 'green', icon: CheckCircleOutlined, action: () => router.push({ path: '/orders', query: { status: 3 } }) },
  { label: '待处理', value: stats.value.pending, description: '需要及时处理', color: 'orange', icon: ClockCircleOutlined, action: () => router.push({ path: '/orders', query: { status: 0 } }) },
  { label: '模拟金额', value: `￥${stats.value.amount}`, description: '订单金额合计', color: 'red', icon: PayCircleOutlined, action: () => router.push('/orders') }
])
const recentOrders = computed(() => orders.value.slice(0, 6))
const columns = [
  { title: '订单号', dataIndex: 'queueNumber', key: 'queueNumber', width: 145 },
  { title: '用户', dataIndex: 'userName', key: 'userName', width: 110 },
  { title: '服务项目', dataIndex: 'serviceName', key: 'serviceName' },
  { title: '金额', key: 'price', width: 100 },
  { title: '状态', key: 'status', width: 100 },
  { title: '操作', key: 'action', width: 80 }
]
const shortcuts = [
  { label: '服务管理', description: '维护打印服务与价格', path: '/services', icon: AppstoreOutlined },
  { label: '门店管理', description: '维护门店信息与位置', path: '/stores', icon: EnvironmentOutlined },
  { label: '用户管理', description: '查看平台用户资料', path: '/users', icon: TeamOutlined }
]
const getStatusText = status => ({ 0: '待处理', 1: '打印中', 2: '待取件', 3: '已完成', 4: '已取消' }[status] || '未知')
const getStatusColor = status => ({ 0: 'orange', 1: 'blue', 2: 'cyan', 3: 'green', 4: 'red' }[status] || 'default')

onMounted(async () => {
  loading.value = true
  try { orders.value = await orderApi.getAll() || [] } finally { loading.value = false }
})
</script>

<style scoped lang="less">
.welcome-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; padding: 4px 2px; }
.welcome-row h2 { margin: 0 0 5px; color: #262626; font-size: 20px; font-weight: 600; }
.welcome-row p { margin: 0; color: #8c8c8c; }
.stats-row { margin-bottom: 16px; }
.stat-card { height: 118px; border: 1px solid #f0f0f0; cursor: pointer; transition: box-shadow .2s, transform .2s; }
.stat-card:hover { transform: translateY(-2px); box-shadow: 0 6px 18px rgba(0,0,0,.07); }
.stat-card :deep(.ant-card-body) { height: 100%; display: flex; align-items: center; gap: 15px; padding: 18px; }
.stat-icon { width: 48px; height: 48px; flex: 0 0 48px; display: grid; place-items: center; border-radius: 6px; font-size: 23px; }
.stat-icon.blue { color: #1677ff; background: #e6f4ff; }
.stat-icon.green { color: #52c41a; background: #f6ffed; }
.stat-icon.orange { color: #fa8c16; background: #fff7e6; }
.stat-icon.red { color: #f5222d; background: #fff1f0; }
.stat-data { min-width: 0; display: flex; flex-direction: column; }
.stat-data span { color: #8c8c8c; font-size: 13px; }
.stat-data strong { margin: 2px 0; overflow: hidden; color: #262626; font-size: 24px; line-height: 30px; text-overflow: ellipsis; white-space: nowrap; }
.stat-data small { color: #bfbfbf; font-size: 11px; }
.data-card { height: 100%; border: 1px solid #f0f0f0; }
.shortcut-card button { width: 100%; display: flex; align-items: center; gap: 12px; padding: 13px 4px; border: 0; border-bottom: 1px solid #f0f0f0; background: transparent; text-align: left; cursor: pointer; }
.shortcut-card button:last-child { border-bottom: 0; }
.shortcut-card button:hover strong, .shortcut-card button:hover > :last-child { color: #1677ff; }
.shortcut-icon { width: 36px; height: 36px; flex: 0 0 36px; display: grid; place-items: center; border-radius: 4px; color: #1677ff; background: #e6f4ff; font-size: 17px; }
.shortcut-card button > span:nth-child(2) { flex: 1; display: flex; flex-direction: column; }
.shortcut-card strong { color: #262626; font-size: 14px; }
.shortcut-card small { margin-top: 2px; color: #8c8c8c; font-size: 12px; }
@media (max-width: 600px) { .welcome-row { align-items: flex-start; gap: 12px; } .welcome-row .ant-btn { display: none; } .stat-card :deep(.ant-card-body) { gap: 10px; padding: 12px; } .stat-icon { width: 38px; height: 38px; flex-basis: 38px; font-size: 18px; } .stat-data strong { font-size: 19px; } }
</style>
