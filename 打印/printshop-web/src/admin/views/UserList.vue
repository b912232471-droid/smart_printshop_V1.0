<template>
  <div class="page-shell">
    <a-card class="content-card" :bordered="false">
      <div class="page-toolbar">
        <div class="page-toolbar__title"><h2>用户管理</h2><p>查看平台注册用户及其订单</p></div>
        <div class="toolbar-actions"><a-input-search v-model:value="searchKeyword" placeholder="用户名或手机号" allow-clear style="width: 230px" /><a-button @click="loadUsers"><ReloadOutlined />刷新</a-button></div>
      </div>
      <a-table class="desktop-table" :columns="columns" :data-source="filteredUsers" :loading="loading" row-key="id" :scroll="{ x: 850 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'user'"><div class="user-cell"><a-avatar :src="record.avatarUrl">{{ initial(record.username) }}</a-avatar><div><strong>{{ record.username || '未设置' }}</strong><span>ID: {{ record.id }}</span></div></div></template>
          <template v-else-if="column.key === 'phone'">{{ record.phone || '未绑定' }}</template>
          <template v-else-if="column.key === 'register'">{{ record.registerTime || record.createdAt || '-' }}</template>
          <template v-else-if="column.key === 'action'"><div class="table-actions"><a-button type="link" size="small" @click="viewUserOrders(record)">查看订单</a-button><a-button v-permission="'print:user:delete'" type="link" danger size="small" @click="deleteUser(record)">删除</a-button></div></template>
        </template>
      </a-table>
      <div class="mobile-list">
        <a-card v-for="user in filteredUsers" :key="user.id" size="small" class="mobile-user-card">
          <div class="user-cell"><a-avatar :size="42" :src="user.avatarUrl">{{ initial(user.username) }}</a-avatar><div><strong>{{ user.username || '未设置' }}</strong><span>{{ user.phone || '未绑定手机号' }}</span></div></div>
          <div class="mobile-actions"><a-button size="small" @click="viewUserOrders(user)">查看订单</a-button><a-button v-permission="'print:user:delete'" danger size="small" @click="deleteUser(user)">删除</a-button></div>
        </a-card>
      </div>
    </a-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { toPrintApiUrl, userApi } from '@/api'

const router = useRouter(); const loading = ref(false); const users = ref([]); const searchKeyword = ref('')
const columns = [{ title: '用户', key: 'user', width: 240 }, { title: '手机号', key: 'phone', width: 150 }, { title: '注册时间', key: 'register', width: 190 }, { title: '操作', key: 'action', width: 160 }]
const filteredUsers = computed(() => !searchKeyword.value ? users.value : users.value.filter(user => [user.username, user.phone].some(value => String(value || '').toLowerCase().includes(searchKeyword.value.toLowerCase()))))
const initial = name => String(name || 'U').slice(0, 1).toUpperCase()
const getAvatarUrl = avatarUrl => { if (!avatarUrl) return ''; if (avatarUrl.startsWith('http')) { try { const url = new URL(avatarUrl); return url.pathname.startsWith('/api/') ? toPrintApiUrl(url.pathname) : avatarUrl } catch { return avatarUrl } } return avatarUrl.startsWith('/api/') ? toPrintApiUrl(avatarUrl) : avatarUrl }
const loadUsers = async () => { loading.value = true; try { users.value = (await userApi.getAll() || []).map(user => ({ ...user, avatarUrl: getAvatarUrl(user.avatarUrl) })) } finally { loading.value = false } }
const viewUserOrders = user => router.push({ path: '/orders', query: { userId: user.id } })
const deleteUser = user => { if (user.id === 1 || user.username === 'admin') return message.warning('无法删除管理员账号'); Modal.confirm({ title: '删除用户', content: `确定删除用户“${user.username || '未设置'}”吗？此操作不可恢复。`, okText: '删除', okType: 'danger', cancelText: '取消', onOk: async () => { await userApi.delete(user.id); message.success('删除成功'); loadUsers() } }) }
onMounted(loadUsers)
</script>

<style scoped lang="less">
.user-cell { display: flex; align-items: center; gap: 11px; }
.user-cell > div { min-width: 0; display: flex; flex-direction: column; }
.user-cell strong { overflow: hidden; color: #262626; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.user-cell span { color: #8c8c8c; font-size: 11px; }
.mobile-user-card { border: 1px solid #e5e7eb; }
.mobile-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; padding-top: 10px; border-top: 1px solid #f0f0f0; }
</style>
