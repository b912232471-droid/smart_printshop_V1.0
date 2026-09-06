<template>
  <div>
    <section class="home-banner">
      <div class="banner-content">
        <span class="banner-kicker">智慧在线打印</span>
        <h1>上传文件，选好参数，到店直接取</h1>
        <p>文档、照片和证件照均可在线预约，价格明细与订单进度清晰可查。</p>
        <div class="banner-actions"><a-button type="primary" size="large" @click="router.push('/client/services')"><PrinterOutlined /> 开始打印</a-button><a-button ghost size="large" @click="router.push('/client/orders')">查看订单</a-button></div>
      </div>
      <div class="queue-panel"><span>当前排队</span><strong>{{ queueCount }}</strong><small>单 · 预计等待 {{ waitTime }}</small></div>
    </section>

    <div class="quick-grid">
      <button v-for="item in quickActions" :key="item.title" class="surface quick-item" @click="router.push(item.path)">
        <span :class="['quick-icon', item.color]"><component :is="item.icon" /></span>
        <span><strong>{{ item.title }}</strong><small>{{ item.desc }}</small></span><RightOutlined />
      </button>
    </div>

    <div class="section-title"><h2>常用打印服务</h2><router-link to="/client/services">查看全部 <RightOutlined /></router-link></div>
    <div v-if="loading" class="loading-block"><a-spin /></div>
    <div v-else class="service-grid">
      <article v-for="service in services.slice(0, 6)" :key="service.id" class="surface service-card">
        <div class="service-card-top"><span class="service-icon"><FileTextOutlined /></span><a-tag>{{ service.category || '打印' }}</a-tag></div>
        <h3>{{ service.name }}</h3><p>{{ service.description || '专业设备打印，在线预约更省时间' }}</p>
        <div class="service-bottom"><strong>¥{{ Number(service.price || 0).toFixed(2) }}<small> 起</small></strong><a-button type="primary" ghost @click="book(service.id)">立即预约</a-button></div>
      </article>
    </div>
  </div>
</template>

<script setup>
import { markRaw, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { EnvironmentOutlined, FileTextOutlined, PictureOutlined, PrinterOutlined, RightOutlined, ShoppingOutlined, TeamOutlined } from '@ant-design/icons-vue'
import { orderApi, serviceApi } from '@client/api'

const router = useRouter()
const loading = ref(true)
const services = ref([])
const queueCount = ref(0)
const waitTime = ref('无需等待')
const quickActions = [
  { title: '文档打印', desc: '论文、资料、办公文件', path: '/client/services?category=文档', icon: markRaw(FileTextOutlined), color: 'blue' },
  { title: '照片冲印', desc: '多尺寸高清照片', path: '/client/services?category=照片', icon: markRaw(PrinterOutlined), color: 'green' },
  { title: 'AI 图片生成', desc: '海报手抄报一键创作', path: '/client/imagegen', icon: markRaw(PictureOutlined), color: 'purple' },
  { title: '附近门店', desc: '查看营业网点', path: '/client/stores', icon: markRaw(EnvironmentOutlined), color: 'orange' },
  { title: '我的订单', desc: '进度与取件码', path: '/client/orders', icon: markRaw(ShoppingOutlined), color: 'red' }
]

function book(id) { router.push(`/client/booking/${id}`) }
onMounted(async () => {
  const queueRequest = localStorage.getItem('client_token') ? orderApi.getQueueCount() : Promise.resolve(0)
  const [serviceResult, queueResult] = await Promise.allSettled([serviceApi.getAll(), queueRequest])
  if (serviceResult.status === 'fulfilled') services.value = serviceResult.value || []
  if (queueResult.status === 'fulfilled') {
    queueCount.value = Number(queueResult.value || 0)
    waitTime.value = queueCount.value ? `${queueCount.value}-${queueCount.value * 3} 分钟` : '无需等待'
  }
  loading.value = false
})
</script>

<style scoped>
.home-banner { position: relative; display: flex; min-height: 310px; align-items: center; overflow: hidden; padding: 42px 44px; color: #fff; background: #10233f url('/print-banner.png') center/cover no-repeat; }
.home-banner::before { position: absolute; inset: 0; background: linear-gradient(90deg, rgba(0,21,41,.92) 0%, rgba(0,21,41,.65) 58%, rgba(0,21,41,.28) 100%); content: ''; }
.banner-content, .queue-panel { position: relative; z-index: 1; }
.banner-content { max-width: 630px; }.banner-kicker { color: #69b1ff; font-size: 13px; font-weight: 600; }
.banner-content h1 { margin-top: 10px; font-size: 32px; line-height: 1.3; letter-spacing: 0; }.banner-content p { max-width: 560px; margin-top: 12px; color: rgba(255,255,255,.7); line-height: 1.7; }
.banner-actions { display: flex; gap: 10px; margin-top: 26px; }.queue-panel { display: flex; min-width: 156px; margin-left: auto; padding: 20px; border-left: 1px solid rgba(255,255,255,.25); flex-direction: column; }
.queue-panel span { color: rgba(255,255,255,.62); font-size: 12px; }.queue-panel strong { margin: 4px 0; font-size: 44px; line-height: 1; }.queue-panel small { color: rgba(255,255,255,.58); }
.quick-grid { display: grid; margin-top: 16px; grid-template-columns: repeat(4, 1fr); gap: 12px; }.quick-item { display: flex; min-height: 86px; align-items: center; gap: 12px; padding: 16px; text-align: left; }
.quick-item:hover { border-color: #91caff; box-shadow: 0 4px 14px rgba(0,0,0,.05); }.quick-item > span:nth-child(2) { min-width: 0; flex: 1; }.quick-item strong,.quick-item small { display: block; }.quick-item strong { color: #262626; font-size: 14px; }.quick-item small { margin-top: 4px; overflow: hidden; color: #8c8c8c; text-overflow: ellipsis; white-space: nowrap; font-size: 11px; }
.quick-icon { display: grid; width: 40px; height: 40px; flex: 0 0 40px; place-items: center; border-radius: 4px; font-size: 19px; }.quick-icon.blue { color: #1677ff; background: #e6f4ff; }.quick-icon.green { color: #389e0d; background: #f6ffed; }.quick-icon.orange { color: #d46b08; background: #fff7e6; }.quick-icon.red { color: #cf1322; background: #fff1f0; }.quick-icon.purple { color: #7c3aed; background: #f4eeff; }
.quick-item > svg { color: #bfbfbf; }.service-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }.service-card { padding: 18px; }.service-card-top { display: flex; align-items: center; justify-content: space-between; }.service-icon { display: grid; width: 38px; height: 38px; place-items: center; border-radius: 4px; color: #1677ff; background: #e6f4ff; }.service-card h3 { margin-top: 16px; color: #262626; font-size: 15px; }.service-card p { min-height: 40px; margin-top: 6px; color: #8c8c8c; font-size: 12px; line-height: 1.6; }.service-bottom { display: flex; align-items: center; justify-content: space-between; margin-top: 16px; }.service-bottom strong { color: #f5222d; font-size: 18px; }.service-bottom small { font-size: 11px; font-weight: 400; }
.dark .quick-item strong,.dark .service-card h3 { color: rgba(255,255,255,.88); }.dark .quick-icon,.dark .service-icon { background: rgba(22,119,255,.14); }
@media(max-width:1050px){.quick-grid{grid-template-columns:repeat(2,1fr)}.service-grid{grid-template-columns:repeat(2,1fr)}}
@media(max-width:600px){.home-banner{min-height:360px;align-items:flex-start;padding:28px 22px}.banner-content h1{font-size:26px}.queue-panel{position:absolute;right:22px;bottom:22px;padding:10px 0 10px 15px}.queue-panel strong{font-size:30px}.quick-grid,.service-grid{grid-template-columns:1fr}.banner-actions{margin-top:20px}}
</style>
