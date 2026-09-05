<template>
  <div class="schedule-page page-shell">
    <div class="welcome-row">
      <div>
        <h2>教务课表查询</h2>
        <p>查看与管理已绑定的教务账号及课程表</p>
      </div>
    </div>

    <a-card class="info-card" :bordered="false">
      <div class="info-block">
        <div class="info-icon"><ScheduleOutlined /></div>
        <div class="info-text">
          <h3>该功能面向用户端账号</h3>
          <p>课表查询需要绑定个人教务账号，<strong>schedule-service 仅接受用户身份的 JWT</strong>。管理员身份调用会被服务拒绝，因此该入口在管理端只提供入口展示。</p>
          <ul>
            <li>如需体验功能：请前往用户端登录后访问「课表查询」</li>
            <li>如需后台管理：建议未来在 print-service 增设管理员级只读接口后再开放</li>
          </ul>
          <div class="info-actions">
            <a-button type="primary" @click="goClientLogin"><UserOutlined /> 前往用户端登录</a-button>
            <a-button @click="goClientSchedule"><LinkOutlined /> 直接进入用户端课表</a-button>
          </div>
        </div>
      </div>
    </a-card>

    <a-card class="info-card" :bordered="false" title="接口说明">
      <a-descriptions :column="1" size="small" bordered>
        <a-descriptions-item label="绑定教务账号">POST /api/schedule/bind</a-descriptions-item>
        <a-descriptions-item label="查询绑定状态">GET /api/schedule/bind-status</a-descriptions-item>
        <a-descriptions-item label="同步课程">POST /api/schedule/sync</a-descriptions-item>
        <a-descriptions-item label="查询课程">GET /api/schedule</a-descriptions-item>
        <a-descriptions-item label="可用学期">GET /api/schedule/terms</a-descriptions-item>
      </a-descriptions>
    </a-card>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { LinkOutlined, ScheduleOutlined, UserOutlined } from '@ant-design/icons-vue'

const router = useRouter()

function goClientLogin() {
  router.push({ path: '/login', query: { mode: 'client', redirect: '/client/schedule' } })
}

function goClientSchedule() {
  const token = localStorage.getItem('client_token')
  const expiresAt = Number(localStorage.getItem('client_token_expires_at') || 0)
  if (token && (!expiresAt || Date.now() < expiresAt)) {
    router.push('/client/schedule')
  } else {
    goClientLogin()
  }
}
</script>

<style scoped>
.info-card { margin-bottom: 16px; }
.info-block { display: flex; gap: 18px; }
.info-icon { display: grid; width: 56px; height: 56px; flex: 0 0 56px; place-items: center; border-radius: 12px; color: #1677ff; background: #e6f4ff; font-size: 26px; }
.info-text h3 { margin: 0 0 8px; color: #262626; font-size: 16px; }
.info-text p { margin: 0 0 10px; color: #595959; font-size: 13px; line-height: 1.7; }
.info-text ul { margin: 0 0 14px; padding-left: 18px; color: #595959; font-size: 13px; line-height: 1.8; }
.info-actions { display: flex; gap: 8px; flex-wrap: wrap; }
@media (max-width: 600px) {
  .info-block { flex-direction: column; align-items: flex-start; }
}
</style>
