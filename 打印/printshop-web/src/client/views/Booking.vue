<template>
  <div>
    <div class="page-heading">
      <div>
        <h1>预约下单</h1>
        <p>确认打印参数、取件门店和预约时间</p>
      </div>
      <a-button @click="router.back()"><ArrowLeftOutlined /> 返回</a-button>
    </div>
    <a-spin :spinning="loading">
      <div class="booking-grid">
        <div class="booking-main">
          <!-- 当前服务摘要 -->
          <section class="surface form-section service-summary">
            <span class="summary-icon"><PrinterOutlined /></span>
            <div class="summary-text">
              <small>当前服务</small>
              <h2>{{ service.name || '打印服务' }}</h2>
              <p>{{ service.description || '在线预约打印服务' }}</p>
            </div>
            <strong class="summary-price">¥{{ unitPrice.toFixed(2) }}<small> / 基础单价</small></strong>
          </section>

          <!-- 上传文件 -->
          <section class="surface form-section upload-section">
            <div class="form-title">
              <div>
                <h2>上传文件</h2>
                <p>支持 PDF、Office 文档、图片和 TXT 文件</p>
              </div>
              <a-tag color="blue">单文件最大限制以后端配置为准</a-tag>
            </div>
            <a-upload-dragger :file-list="fileList" :before-upload="selectFile" :max-count="1" @remove="removeFile">
              <p class="ant-upload-drag-icon"><InboxOutlined /></p>
              <p class="ant-upload-text">点击或拖拽文件到这里</p>
              <p class="ant-upload-hint">文件仅用于本次打印订单</p>
            </a-upload-dragger>
          </section>

          <!-- 打印参数：vertical 表单，label 在上控件全宽，避免字体重叠 -->
          <section class="surface form-section">
            <div class="form-title">
              <div>
                <h2>打印参数</h2>
                <p>金额按基础单价 × 页数 × 份数预估</p>
              </div>
            </div>
            <a-form layout="vertical" class="parameter-grid">
              <a-form-item label="纸张规格"><a-select v-model:value="form.paperSize" :options="paperOptions" /></a-form-item>
              <a-form-item label="色彩模式"><a-segmented v-model:value="form.colorMode" block :options="colorOptions" /></a-form-item>
              <a-form-item label="单双面"><a-segmented v-model:value="form.duplex" block :options="duplexOptions" /></a-form-item>
              <a-form-item label="文件页数"><a-input-number v-model:value="form.pageCount" :min="1" :max="500" class="full-width" /></a-form-item>
              <a-form-item label="打印份数"><a-input-number v-model:value="form.copies" :min="1" :max="99" class="full-width" /></a-form-item>
            </a-form>
          </section>

          <!-- 取件安排 -->
          <section class="surface form-section">
            <div class="form-title">
              <div>
                <h2>取件安排</h2>
                <p>请选择营业门店和到店时间</p>
              </div>
            </div>
            <a-form layout="vertical" class="parameter-grid pickup-grid">
              <a-form-item label="取件门店"><a-select v-model:value="form.storeId" show-search option-filter-prop="label" :options="storeOptions" placeholder="请选择门店" /></a-form-item>
              <a-form-item label="预约日期"><a-date-picker v-model:value="appointDate" :disabled-date="disablePast" class="full-width" /></a-form-item>
              <a-form-item label="预约时间"><a-time-picker v-model:value="appointTime" format="HH:mm" :minute-step="15" class="full-width" /></a-form-item>
            </a-form>
          </section>
        </div>

        <aside class="surface order-summary">
          <h2>订单明细</h2>
          <div class="summary-line"><span>服务</span><b>{{ service.name || '-' }}</b></div>
          <div class="summary-line"><span>纸张</span><b>{{ form.paperSize }}</b></div>
          <div class="summary-line"><span>色彩 / 单双面</span><b>{{ colorLabel }} / {{ form.duplex ? '双面' : '单面' }}</b></div>
          <div class="summary-line"><span>页数 × 份数</span><b>{{ form.pageCount }} × {{ form.copies }}</b></div>
          <div class="summary-total"><span>预估合计</span><strong>¥{{ totalPrice }}</strong></div>
          <p>最终金额以后端订单核算和门店确认为准。</p>
          <a-button type="primary" size="large" block :loading="submitting" @click="submitOrder"><CheckOutlined /> 确认预约</a-button>
        </aside>
      </div>
    </a-spin>
  </div>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { ArrowLeftOutlined, CheckOutlined, InboxOutlined, PrinterOutlined } from '@ant-design/icons-vue'
import { fileApi, imageGenApi, orderApi, serviceApi, storeApi } from '@client/api'
import { useImageGenDraftStore } from '@client/stores/imageGenDraft'
const route=useRoute(),router=useRouter(),loading=ref(true),submitting=ref(false),service=ref({}),stores=ref([]),fileList=ref([]),appointDate=ref(dayjs()),appointTime=ref(dayjs().add(1,'hour').startOf('hour')),draft=useImageGenDraftStore(),boundGenId=ref(null)
const form=reactive({paperSize:'A4',colorMode:'BLACK_WHITE',duplex:0,pageCount:1,copies:1,storeId:undefined})
const paperOptions=[{label:'A4',value:'A4'},{label:'A3',value:'A3'},{label:'6 寸照片',value:'PHOTO_6IN'},{label:'证件照',value:'ID_PHOTO'}]
const colorOptions=[{label:'黑白',value:'BLACK_WHITE'},{label:'彩色',value:'COLOR'}],duplexOptions=[{label:'单面',value:0},{label:'双面',value:1}]
const unitPrice=computed(()=>Number(service.value.price||0)),totalPrice=computed(()=>(unitPrice.value*form.pageCount*form.copies).toFixed(2)),colorLabel=computed(()=>form.colorMode==='COLOR'?'彩色':'黑白')
const storeOptions=computed(()=>stores.value.map(item=>({label:`${item.shortName||item.name} · ${item.address||''}`,value:item.id})))
const disablePast=current=>current&&current<dayjs().startOf('day')
function selectFile(file){fileList.value=[file];return false} function removeFile(){fileList.value=[];boundGenId.value=null}
async function submitOrder(){
 if(!fileList.value.length)return message.warning('请先选择打印文件');if(!form.storeId)return message.warning('请选择取件门店');if(!appointDate.value||!appointTime.value)return message.warning('请选择预约时间')
 submitting.value=true
 try{const orderId=await orderApi.create({serviceId:Number(route.params.serviceId),storeId:form.storeId,appointTime:`${appointDate.value.format('YYYY-MM-DD')} ${appointTime.value.format('HH:mm')}:00`,copies:form.copies,pageCount:form.pageCount,duplex:form.duplex,colorMode:form.colorMode,paperSize:form.paperSize});await fileApi.upload(fileList.value[0],orderId);if(boundGenId.value){imageGenApi.bindOrder(boundGenId.value,orderId).catch(()=>{})}draft.clear();boundGenId.value=null;message.success('预约成功');router.replace(`/client/orders/${orderId}`)}finally{submitting.value=false}
}
onMounted(async()=>{try{[service.value,stores.value]=await Promise.all([serviceApi.getById(route.params.serviceId),storeApi.getActive()]);if(stores.value.length)form.storeId=stores.value[0].id;const name=service.value.name||'';if(name.includes('彩色'))form.colorMode='COLOR';if(name.includes('A3'))form.paperSize='A3';else if(name.includes('证件照'))form.paperSize='ID_PHOTO';else if(name.includes('照片'))form.paperSize='PHOTO_6IN'}finally{loading.value=false}
 if(draft.blob&&draft.blob.size){boundGenId.value=draft.genId;fileList.value=[new File([draft.blob],draft.filename||'ai-image.png',{type:'image/png'})];message.info('已带入 AI 生成图片')}})
</script>
<style scoped>
/* 布局骨架 */
.booking-grid { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 16px; align-items: start; }
.booking-main { display: grid; gap: 12px; }
.form-section { padding: 20px; }

/* 当前服务摘要：三段弹性布局，价格不被挤掉 */
.service-summary { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.summary-icon { display: grid; width: 48px; height: 48px; flex: 0 0 48px; place-items: center; border-radius: 4px; color: #1677ff; font-size: 22px; background: #e6f4ff; }
.summary-text { flex: 1 1 200px; min-width: 0; }
.summary-text small, .summary-text p { color: #8c8c8c; font-size: 12px; }
.summary-text h2 { margin: 3px 0; color: #262626; font-size: 17px; word-break: break-word; }
.summary-price { flex: 0 0 auto; color: #f5222d; font-size: 20px; white-space: nowrap; }
.summary-price small { font-weight: 400; }

/* 段落标题 */
.form-title { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.form-title h2, .order-summary h2 { color: #262626; font-size: 16px; }
.form-title p { margin-top: 4px; color: #8c8c8c; font-size: 12px; }

/* 上传卡片：固定高度，标题在上、拖拽区撑满剩余空间 */
.upload-section { min-height: 240px; display: flex; flex-direction: column; }
.upload-section .form-title { flex: 0 0 auto; }
.upload-section :deep(.ant-upload-wrapper) { width: 100%; flex: 1 1 auto; display: flex; }
.upload-section :deep(.ant-upload.ant-upload-drag) { width: 100%; flex: 1 1 auto; display: flex; }
.upload-section :deep(.ant-upload.ant-upload-drag .ant-upload-btn) { flex: 1 1 auto; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; padding: 12px; }
.upload-section :deep(.ant-upload-drag-icon) { margin: 0; color: #1677ff; line-height: 1; }
.upload-section :deep(.ant-upload-drag-icon .anticon) { font-size: 36px; }
.upload-section :deep(.ant-upload-text) { margin: 0; color: #434343; font-size: 14px; }
.upload-section :deep(.ant-upload-hint) { margin: 0; color: #8c8c8c; font-size: 12px; }

/* 参数网格：vertical 表单，label 在上控件全宽 */
.parameter-grid { display: grid; grid-template-columns: repeat(2, 1fr); column-gap: 18px; row-gap: 4px; }
.parameter-grid :deep(.ant-form-item) { margin-bottom: 12px; }
.pickup-grid { grid-template-columns: 2fr 1fr 1fr; }
.full-width { width: 100%; }

/* 订单明细侧栏 */
.order-summary { position: sticky; top: 80px; padding: 20px; }
.order-summary h2 { margin-bottom: 18px; }
.summary-line { display: flex; justify-content: space-between; gap: 12px; padding: 10px 0; border-bottom: 1px solid #f0f0f0; font-size: 12px; }
.summary-line span { color: #8c8c8c; flex: 0 0 auto; }
.summary-line b { color: #434343; text-align: right; max-width: 60%; word-break: break-word; }
.summary-total { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 20px 0 8px; }
.summary-total span { font-weight: 600; }
.summary-total strong { color: #f5222d; font-size: 24px; }
.order-summary > p { margin-bottom: 18px; color: #a3a3a3; font-size: 11px; line-height: 1.6; }

/* 深色模式 */
.dark .service-summary h2,
.dark .form-title h2,
.dark .order-summary h2,
.dark .summary-line b { color: rgba(255, 255, 255, .88); }
.dark .summary-line { border-color: #303030; }
.dark .summary-icon { background: rgba(22, 119, 255, .14); }

/* 响应式 */
@media (max-width: 1050px) {
  .booking-grid { grid-template-columns: 1fr; }
  .order-summary { position: static; }
  .pickup-grid { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 640px) {
  .parameter-grid, .pickup-grid { grid-template-columns: 1fr; }
  .service-summary { flex-direction: column; align-items: flex-start; }
  .summary-price { align-self: flex-end; }
}
</style>
