<template>
  <div class="page-shell">
    <a-card class="content-card" :bordered="false">
      <div class="page-toolbar">
        <div class="page-toolbar__title"><h2>门店管理</h2><p>维护打印门店、设备、服务范围和地图位置</p></div>
        <div class="toolbar-actions"><a-button :loading="loading" @click="loadStores"><ReloadOutlined />刷新</a-button><a-button v-permission="'print:store:add'" type="primary" @click="showAddDialog"><PlusOutlined />新增门店</a-button></div>
      </div>
      <a-table class="desktop-table" :columns="columns" :data-source="stores" :loading="loading" row-key="id" :scroll="{ x: 1160 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'store'"><div class="store-cell"><a-image :width="48" :height="48" :src="getImageUrl(record.imageUrl)" :preview="false" fallback="/images/store-default.jpg" /><div><strong>{{ record.shortName || record.name }}</strong><span>{{ record.name }}</span></div></div></template>
          <template v-else-if="column.key === 'address'"><div class="address-cell"><span>{{ record.address }}</span><small>{{ record.latitude }}, {{ record.longitude }}</small></div></template>
          <template v-else-if="column.key === 'services'"><a-space wrap><a-tag v-for="item in splitServices(record.services)" :key="item">{{ item }}</a-tag></a-space></template>
          <template v-else-if="column.key === 'status'"><a-badge :status="record.status === 1 ? 'success' : 'default'" :text="record.status === 1 ? '营业' : '停业'" /></template>
          <template v-else-if="column.key === 'action'"><div class="table-actions"><a-button v-permission="'print:store:update'" type="link" size="small" @click="editStore(record)">编辑</a-button><a-button v-permission="'print:store:delete'" type="link" danger size="small" @click="deleteStore(record)">删除</a-button></div></template>
        </template>
      </a-table>
      <div class="mobile-list"><a-card v-for="store in stores" :key="store.id" size="small" class="mobile-store-card"><div class="store-cell"><a-image :width="48" :height="48" :src="getImageUrl(store.imageUrl)" :preview="false" /><div><strong>{{ store.shortName || store.name }}</strong><span>{{ store.address }}</span></div><a-badge :status="store.status === 1 ? 'success' : 'default'" /></div><div class="mobile-store-meta"><span>{{ store.phone || '未填写电话' }}</span><span>{{ store.hours }}</span></div><div class="mobile-actions"><a-button v-permission="'print:store:update'" size="small" @click="editStore(store)">编辑</a-button><a-button v-permission="'print:store:delete'" danger size="small" @click="deleteStore(store)">删除</a-button></div></a-card></div>
    </a-card>

    <a-modal v-model:open="dialogVisible" :title="isEdit ? '编辑门店' : '新增门店'" width="900px" ok-text="保存" cancel-text="取消" @ok="submitForm" @after-close="resetForm">
      <a-form layout="vertical">
        <a-row :gutter="16"><a-col :xs="24" :md="12"><a-form-item label="门店全称" required><a-input v-model:value="form.name" /></a-form-item></a-col><a-col :xs="24" :md="12"><a-form-item label="门店简称" required><a-input v-model:value="form.shortName" /></a-form-item></a-col></a-row>
        <a-row :gutter="16"><a-col :xs="24" :md="12"><a-form-item label="设备编码"><a-input v-model:value="form.deviceCode" /></a-form-item></a-col><a-col :xs="24" :md="12"><a-form-item label="联系电话"><a-input v-model:value="form.phone" /></a-form-item></a-col></a-row>
        <a-row :gutter="16"><a-col :xs="24" :md="16"><a-form-item label="详细地址" required><a-input v-model:value="form.address" /></a-form-item></a-col><a-col :xs="24" :md="8"><a-form-item label="营业时间"><a-input v-model:value="form.hours" /></a-form-item></a-col></a-row>
        <a-form-item label="提供服务"><a-checkbox-group v-model:value="selectedServices" :options="serviceOptions" /></a-form-item>
        <a-row :gutter="16"><a-col :xs="24" :md="8"><a-form-item label="营业状态"><a-radio-group v-model:value="form.status"><a-radio :value="1">营业</a-radio><a-radio :value="0">停业</a-radio></a-radio-group></a-form-item></a-col><a-col :xs="24" :md="8"><a-form-item label="排序"><a-input-number v-model:value="form.sortOrder" :min="0" style="width: 100%" /></a-form-item></a-col><a-col :xs="24" :md="8"><a-form-item label="门店图片"><a-upload name="file" :action="uploadUrl" :headers="uploadHeaders" :show-upload-list="false" :before-upload="beforeUpload" @change="handleUploadChange"><a-button><UploadOutlined />上传图片</a-button></a-upload></a-form-item></a-col></a-row>
        <div class="map-section">
          <div class="map-toolbar">
            <strong>地图位置</strong>
            <a-auto-complete
              v-model:value="searchKeyword"
              :options="searchResults"
              :loading="searching"
              placeholder="搜索地点（学校、商场、地铁站...）"
              class="poi-search"
              allow-clear
              @search="onSearchPoi"
              @select="onSelectPoi"
            >
              <template #option="option">
                <div class="poi-option"><strong>{{ option.label }}</strong><small>{{ option.address }}</small></div>
              </template>
            </a-auto-complete>
            <div class="coord-inputs"><a-input-number v-model:value="form.latitude" :precision="7" style="width: 150px" /><a-input-number v-model:value="form.longitude" :precision="7" style="width: 150px" /><a-button @click="updateMapCenter">定位</a-button></div>
          </div>
          <div id="map-container" class="map-container"><div v-if="!tencentMapKey" class="map-placeholder"><EnvironmentOutlined /><span>请配置 VITE_TENCENT_MAP_KEY 后使用地图选点</span></div></div>
        </div>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { EnvironmentOutlined, PlusOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { storeApi, toPrintApiUrl } from '@/api'

const loading = ref(false); const stores = ref([]); const dialogVisible = ref(false); const isEdit = ref(false); const selectedServices = ref(['文件', '照片', '证件照', '复印'])
const serviceOptions = ['文件', '照片', '证件照', '复印']; const uploadUrl = '/api/print/file/store-image'; const uploadHeaders = ref({}); const tencentMapKey = import.meta.env.VITE_TENCENT_MAP_KEY || ''
let map = null; let marker = null; let mapSdkLoading = null
const defaultForm = () => ({ id: null, name: '', shortName: '', deviceCode: '', address: '', imageUrl: '/images/store-default.jpg', latitude: 22.8240, longitude: 108.3662, phone: '', hours: '09:00-21:00', services: '文件,照片,证件照,复印', status: 1, sortOrder: 0 })
const form = ref(defaultForm())
const columns = [{ title: '门店', key: 'store', width: 220 }, { title: '地址与坐标', key: 'address', width: 300 }, { title: '电话', dataIndex: 'phone', key: 'phone', width: 130 }, { title: '营业时间', dataIndex: 'hours', key: 'hours', width: 120 }, { title: '服务', key: 'services', width: 230 }, { title: '状态', key: 'status', width: 90 }, { title: '操作', key: 'action', width: 130 }]
const splitServices = services => String(services || '').split(',').map(item => item.trim()).filter(Boolean)
const loadStores = async () => { loading.value = true; try { stores.value = await storeApi.getAll() || [] } finally { loading.value = false } }
const loadTencentMapSdk = () => { if (window.TMap) return Promise.resolve(); if (!tencentMapKey) return Promise.reject(new Error('missing map key')); if (mapSdkLoading) return mapSdkLoading; mapSdkLoading = new Promise((resolve, reject) => { const script = document.createElement('script'); script.id = 'tencent-map-sdk'; script.src = `https://map.qq.com/api/gljs?v=1.exp&key=${encodeURIComponent(tencentMapKey)}`; script.onload = resolve; script.onerror = reject; document.head.appendChild(script) }); return mapSdkLoading }
const initMap = async () => { if (!tencentMapKey) return false; try { await loadTencentMapSdk() } catch { message.error('地图 API 加载失败，请检查腾讯地图 Key'); return false } if (map) map.destroy(); const TMap = window.TMap; const center = new TMap.LatLng(Number(form.value.latitude), Number(form.value.longitude)); map = new TMap.Map('map-container', { center, zoom: 15, draggable: true }); marker = new TMap.MultiMarker({ map, geometries: [{ id: 'marker1', position: center }] }); map.on('click', event => { const lat = event.latLng.getLat(); const lng = event.latLng.getLng(); form.value.latitude = Number(lat.toFixed(7)); form.value.longitude = Number(lng.toFixed(7)); marker.updateGeometries([{ id: 'marker1', position: new TMap.LatLng(lat, lng) }]); message.success('位置已选择') }); return true }
const updateMapCenter = () => { if (!map || !window.TMap) return; const lat = Number(form.value.latitude); const lng = Number(form.value.longitude); if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return message.warning('经纬度范围不正确'); const center = new window.TMap.LatLng(lat, lng); map.setCenter(center); marker?.updateGeometries([{ id: 'marker1', position: center }]) }
const searchKeyword = ref(''); const searchResults = ref([]); const searching = ref(false); let searchTimer = null; let jsonpSeq = 0
const callJsonp = url => new Promise((resolve, reject) => {
  const cbName = `__qqMapCb_${jsonpSeq++}_${Date.now().toString(36)}`
  const script = document.createElement('script'); let done = false
  const cleanup = () => { try { delete window[cbName] } catch (e) { window[cbName] = undefined } if (script.parentNode) script.parentNode.removeChild(script) }
  window[cbName] = data => { done = true; resolve(data); cleanup() }
  script.onerror = () => { if (!done) { reject(new Error('jsonp error')); cleanup() } }
  script.src = url + (url.indexOf('?') >= 0 ? '&' : '?') + `output=jsonp&callback=${encodeURIComponent(cbName)}`
  document.head.appendChild(script)
  setTimeout(() => { if (!done) { reject(new Error('jsonp timeout')); cleanup() } }, 5000)
})
const onSearchPoi = value => {
  if (searchTimer) clearTimeout(searchTimer)
  if (!value || !tencentMapKey) { searchResults.value = []; return }
  searchTimer = setTimeout(async () => {
    searching.value = true
    try {
      const url = `https://apis.map.qq.com/ws/place/v1/suggestion?keyword=${encodeURIComponent(value)}&key=${encodeURIComponent(tencentMapKey)}`
      const data = await callJsonp(url)
      if (data && data.status === 0 && Array.isArray(data.data)) {
        searchResults.value = data.data.slice(0, 10).map(item => ({ value: item.title, label: item.title, address: item.address || '', latitude: item.location.lat, longitude: item.location.lng }))
      } else {
        searchResults.value = []
        if (data && data.status === 121) message.warning('该 Key 今日调用量已达上限，请明天重试或更换 Key')
        else if (data && data.message) message.warning(`地点搜索失败：${data.message}`)
      }
    } catch (e) { searchResults.value = []; message.warning('地点搜索请求失败，请检查网络或 Key 配置') } finally { searching.value = false }
  }, 350)
}
const onSelectPoi = (_value, option) => { if (!option) return; form.value.latitude = Number(option.latitude.toFixed(7)); form.value.longitude = Number(option.longitude.toFixed(7)); if (!form.value.address && option.address) form.value.address = option.address; updateMapCenter() }
const showAddDialog = () => { isEdit.value = false; form.value = defaultForm(); selectedServices.value = serviceOptions.slice(); dialogVisible.value = true; nextTick(initMap) }
const editStore = row => { isEdit.value = true; form.value = { ...row, latitude: Number(row.latitude), longitude: Number(row.longitude) }; selectedServices.value = splitServices(row.services); dialogVisible.value = true; nextTick(initMap) }
const deleteStore = row => Modal.confirm({ title: '删除门店', content: `确定删除门店“${row.shortName || row.name}”吗？`, okText: '删除', okType: 'danger', cancelText: '取消', onOk: async () => { await storeApi.delete(row.id); message.success('删除成功'); loadStores() } })
const submitForm = async () => { if (!form.value.name || !form.value.shortName || !form.value.address) return message.warning('请填写门店全称、简称和地址'); form.value.services = selectedServices.value.join(','); if (isEdit.value) await storeApi.update(form.value); else await storeApi.create(form.value); message.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadStores() }
const resetForm = () => { form.value = defaultForm(); selectedServices.value = serviceOptions.slice(); searchKeyword.value = ''; searchResults.value = []; if (map) { map.destroy(); map = null; marker = null } }
const getImageUrl = imageUrl => { if (!imageUrl || imageUrl === '/images/store-default.jpg') return '/images/store-default.jpg'; if (imageUrl.startsWith('http')) { try { return toPrintApiUrl(new URL(imageUrl).pathname) } catch { return imageUrl } } return toPrintApiUrl(imageUrl) }
const beforeUpload = file => { const token = localStorage.getItem('admin_token'); uploadHeaders.value = token ? { Authorization: `Bearer ${token}` } : {}; if (!file.type.startsWith('image/')) { message.error('只能上传图片文件'); return false } if (file.size >= 2 * 1024 * 1024) { message.error('图片大小不能超过 2MB'); return false } return true }
const handleUploadChange = info => { if (info.file.status === 'done') { const response = info.file.response; if (response?.code === 200) { form.value.imageUrl = response.imageUrl; message.success('图片上传成功') } else message.error(response?.message || '上传失败') } else if (info.file.status === 'error') message.error('图片上传失败') }
onMounted(loadStores)
</script>

<style scoped lang="less">
.store-cell { display: flex; align-items: center; gap: 10px; }.store-cell :deep(img) { border-radius: 4px; object-fit: cover; }.store-cell > div,.address-cell { min-width: 0; display: flex; flex-direction: column; }.store-cell strong { color: #262626; }.store-cell span,.address-cell small { overflow: hidden; color: #8c8c8c; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }.map-section { border: 1px solid #e5e7eb; border-radius: 4px; overflow: hidden; }.map-toolbar { min-height: 48px; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 12px; background: #fafafa; border-bottom: 1px solid #e5e7eb; }.map-toolbar > div { display: flex; gap: 8px; }.poi-search { flex: 1 1 240px; min-width: 200px; max-width: 360px; }.poi-option { display: flex; flex-direction: column; gap: 2px; }.poi-option small { color: #8c8c8c; font-size: 11px; }.coord-inputs { display: flex; gap: 8px; }.map-container { height: 320px; position: relative; background: #f5f7fa; }.map-placeholder { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 10px; color: #8c8c8c; font-size: 14px; }.map-placeholder > :first-child { font-size: 28px; }.mobile-store-meta,.mobile-actions { display: flex; align-items: center; justify-content: space-between; margin-top: 10px; }.mobile-store-meta { color: #8c8c8c; font-size: 12px; }.mobile-actions { justify-content: flex-end; gap: 8px; padding-top: 10px; border-top: 1px solid #f0f0f0; }
@media (max-width: 700px) { .map-toolbar { align-items: flex-start; flex-direction: column; }.map-toolbar > div, .coord-inputs { width: 100%; flex-wrap: wrap; }.poi-search { max-width: none; }.map-toolbar .ant-input-number { flex: 1; min-width: 120px; } }
</style>
