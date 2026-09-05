<template>
  <div class="page-shell">
    <div class="page-toolbar">
      <div class="page-toolbar__title"><h2>订单详情</h2><p>订单 {{ order.queueNumber || route.params.id }} 的完整信息</p></div>
      <div class="toolbar-actions"><a-button @click="router.back()"><ArrowLeftOutlined />返回</a-button><a-button v-permission="'print:order:update'" type="primary" @click="showUpdateDialog">更新状态</a-button></div>
    </div>

    <a-spin :spinning="loading">
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :xl="16">
          <a-card class="detail-card" title="基本信息" :bordered="false">
            <template #extra><a-tag :color="getStatusColor(order.orderStatus)">{{ getStatusText(order.orderStatus) }}</a-tag></template>
            <a-descriptions :column="{ xs: 1, sm: 2 }" bordered size="middle">
              <a-descriptions-item label="订单号">{{ order.queueNumber || '-' }}</a-descriptions-item>
              <a-descriptions-item label="下单用户">{{ order.userName || '未设置' }}</a-descriptions-item>
              <a-descriptions-item label="用户手机">{{ order.userPhone || '未绑定' }}</a-descriptions-item>
              <a-descriptions-item label="服务项目">{{ order.serviceName || `服务ID: ${order.serviceId || '-'}` }}</a-descriptions-item>
              <a-descriptions-item label="打印参数" :span="2">{{ getPrintSummary(order) }}</a-descriptions-item>
              <a-descriptions-item label="预约时间">{{ order.appointTime || '-' }}</a-descriptions-item>
              <a-descriptions-item label="打印门店">{{ order.storeName || '未设置' }}</a-descriptions-item>
              <a-descriptions-item v-if="order.storeAddress" label="门店地址" :span="2">{{ order.storeAddress }}</a-descriptions-item>
              <a-descriptions-item label="取件码">{{ order.fetchCode || '暂无' }}</a-descriptions-item>
              <a-descriptions-item label="订单金额"><span class="price-text">￥{{ order.totalPrice || 0 }}</span></a-descriptions-item>
              <a-descriptions-item v-if="order.remark" label="备注" :span="2">{{ order.remark }}</a-descriptions-item>
            </a-descriptions>
          </a-card>
        </a-col>

        <a-col :xs="24" :xl="8">
          <a-card class="detail-card" title="订单文件" :bordered="false">
            <a-empty v-if="!files.length" description="暂无上传文件" />
            <div v-else class="file-list">
              <div v-for="file in files" :key="file.id" class="file-item">
                <div class="file-icon"><FileOutlined /></div>
                <div class="file-copy"><strong>{{ file.fileName }}</strong><span>{{ formatFileSize(file.fileSize) }}</span></div>
                <a-dropdown>
                  <a-button type="text"><MoreOutlined /></a-button>
                  <template #overlay><a-menu><a-menu-item @click="previewFile(file)"><EyeOutlined />预览</a-menu-item><a-menu-item @click="downloadFile(file)"><DownloadOutlined />下载</a-menu-item></a-menu></template>
                </a-dropdown>
              </div>
            </div>
          </a-card>
        </a-col>
      </a-row>
    </a-spin>

    <a-modal v-model:open="dialogVisible" title="更新订单状态" ok-text="确认更新" cancel-text="取消" @ok="confirmUpdate">
      <a-form layout="vertical">
        <a-form-item label="新状态"><a-select v-model:value="newStatus" style="width: 100%"><a-select-option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option></a-select></a-form-item>
        <a-form-item label="取件码"><a-input v-model:value="fetchCode" placeholder="可选" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { ArrowLeftOutlined, DownloadOutlined, EyeOutlined, FileOutlined, MoreOutlined } from '@ant-design/icons-vue'
import { fileApi, orderApi } from '@/api'

const route = useRoute(); const router = useRouter()
const loading = ref(false); const order = ref({}); const files = ref([]); const dialogVisible = ref(false); const newStatus = ref(0); const fetchCode = ref('')
const statusOptions = [{ value: 0, label: '待处理' }, { value: 1, label: '打印中' }, { value: 2, label: '待取件' }, { value: 3, label: '已完成' }, { value: 4, label: '已取消' }]
const getStatusText = status => statusOptions.find(item => item.value === status)?.label || '未知'
const getStatusColor = status => ({ 0: 'orange', 1: 'blue', 2: 'cyan', 3: 'green', 4: 'red' }[status] || 'default')
const getPrintSummary = data => `${({ A4: 'A4', A3: 'A3', PHOTO_6IN: '6寸照片', ID_PHOTO: '证件照' }[data?.paperSize] || 'A4')} · ${data?.colorMode === 'COLOR' ? '彩色' : '黑白'} · ${data?.duplex === 1 ? '双面' : '单面'} · ${data?.pageCount || 1}页 × ${data?.copies || 1}份`
const formatFileSize = size => !size ? '未知大小' : size < 1024 ? `${size} B` : size < 1024 * 1024 ? `${(size / 1024).toFixed(1)} KB` : `${(size / 1024 / 1024).toFixed(1)} MB`
const loadOrderDetail = async () => { loading.value = true; try { const [orderData, fileData] = await Promise.all([orderApi.getById(route.params.id), fileApi.getByOrderId(route.params.id)]); order.value = orderData; files.value = fileData || [] } finally { loading.value = false } }
const previewFile = async file => { const ext = file.fileName.slice(file.fileName.lastIndexOf('.')).toLowerCase(); if (!['.jpg','.jpeg','.png','.gif','.bmp','.webp','.pdf'].includes(ext)) return message.warning('该类型不支持在线预览，请下载后查看'); const blob = await fileApi.download(file.id); const url = URL.createObjectURL(blob); window.open(url, '_blank'); setTimeout(() => URL.revokeObjectURL(url), 60000) }
const downloadFile = async file => { const blob = await fileApi.download(file.id); const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = file.fileName; link.click(); URL.revokeObjectURL(url); message.success(`已开始下载 ${file.fileName}`) }
const showUpdateDialog = () => { newStatus.value = order.value.orderStatus; fetchCode.value = order.value.fetchCode || ''; dialogVisible.value = true }
const confirmUpdate = async () => { await orderApi.updateStatus(order.value.id, newStatus.value, fetchCode.value); message.success('状态更新成功'); dialogVisible.value = false; loadOrderDetail() }
onMounted(loadOrderDetail)
</script>

<style scoped lang="less">
.detail-card { height: 100%; border: 1px solid #f0f0f0; }
.file-list { display: grid; gap: 8px; }
.file-item { display: flex; align-items: center; gap: 10px; padding: 10px; border: 1px solid #f0f0f0; border-radius: 4px; }
.file-icon { width: 34px; height: 34px; display: grid; place-items: center; color: #1677ff; background: #e6f4ff; border-radius: 4px; font-size: 17px; }
.file-copy { min-width: 0; flex: 1; display: flex; flex-direction: column; }
.file-copy strong { overflow: hidden; color: #262626; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.file-copy span { color: #8c8c8c; font-size: 11px; }
</style>
