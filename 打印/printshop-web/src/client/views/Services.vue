<template>
  <div>
    <div class="page-heading"><div><h1>打印服务</h1><p>选择服务后配置文件、纸张、单双面与取件门店</p></div></div>
    <div class="service-filter surface"><a-segmented v-model:value="category" :options="categories" /><a-input-search v-model:value="keyword" allow-clear placeholder="搜索服务名称" /></div>
    <div v-if="loading" class="loading-block"><a-spin /></div>
    <div v-else-if="filtered.length" class="service-list">
      <article v-for="service in filtered" :key="service.id" class="surface service-row">
        <span class="row-icon"><PrinterOutlined /></span><div class="row-copy"><div><h3>{{ service.name }}</h3><a-tag>{{ service.category || '打印' }}</a-tag></div><p>{{ service.description || '标准在线打印服务' }}</p></div>
        <div class="row-price"><strong>¥{{ Number(service.price || 0).toFixed(2) }}</strong><small>基础单价</small></div><a-button type="primary" @click="router.push(`/client/booking/${service.id}`)">预约</a-button>
      </article>
    </div>
    <div v-else class="surface empty-state"><InboxOutlined style="font-size:36px;color:#bfbfbf"/><h3>没有匹配的服务</h3><p>调整分类或搜索关键词后再试</p></div>
  </div>
</template>
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { InboxOutlined, PrinterOutlined } from '@ant-design/icons-vue'
import { serviceApi } from '@client/api'
const route=useRoute(),router=useRouter(),loading=ref(true),services=ref([]),keyword=ref(''),category=ref(String(route.query.category||'全部'))
const categories=['全部','文档','照片','证件照','复印']
const filtered=computed(()=>services.value.filter(item=>(category.value==='全部'||item.category===category.value)&&(!keyword.value||`${item.name}${item.description||''}`.toLowerCase().includes(keyword.value.toLowerCase()))))
watch(category,value=>router.replace({query:value==='全部'?{}:{category:value}}))
onMounted(async()=>{try{services.value=await serviceApi.getAll()||[]}finally{loading.value=false}})
</script>
<style scoped>
.service-filter{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:12px 14px}.service-filter .ant-input-search{max-width:280px}.service-list{display:grid;margin-top:12px;gap:10px}.service-row{display:flex;align-items:center;gap:16px;padding:16px 18px}.row-icon{display:grid;width:44px;height:44px;flex:0 0 44px;place-items:center;border-radius:4px;color:#1677ff;font-size:20px;background:#e6f4ff}.row-copy{min-width:0;flex:1}.row-copy>div{display:flex;align-items:center;gap:8px}.row-copy h3{color:#262626;font-size:15px}.row-copy p{margin-top:5px;color:#8c8c8c;font-size:12px}.row-price{width:100px;text-align:right}.row-price strong,.row-price small{display:block}.row-price strong{color:#f5222d;font-size:17px}.row-price small{color:#a3a3a3;font-size:11px}.dark .row-copy h3{color:rgba(255,255,255,.88)}.dark .row-icon{background:rgba(22,119,255,.14)}
@media(max-width:650px){.service-filter{align-items:stretch;flex-direction:column}.service-filter .ant-input-search{max-width:none}.service-row{align-items:flex-start;flex-wrap:wrap}.row-copy{width:calc(100% - 60px);flex:auto}.row-price{margin-left:60px;text-align:left;flex:1}}
</style>
