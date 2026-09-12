<template>
  <div class="page-shell">
    <a-card class="content-card" :bordered="false">
      <div class="page-toolbar">
        <div class="page-toolbar__title"><h2>角色管理</h2><p>维护角色授权矩阵：角色关联菜单与操作权限点</p></div>
        <a-button v-permission="'print:role:add'" type="primary" @click="openCreate"><PlusOutlined />新增角色</a-button>
      </div>

      <a-table
        class="desktop-table"
        :columns="columns"
        :data-source="roles"
        :loading="loading"
        row-key="id"
        :pagination="false"
        :scroll="{ x: 980 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'roleName'">
            <div class="role-cell"><strong>{{ record.roleName }}</strong><span>{{ record.roleKey }}</span></div>
          </template>
          <template v-else-if="column.key === 'builtin'">
            <a-tag v-if="record.builtin === 1" color="gold">内置</a-tag>
            <a-tag v-else>自定义</a-tag>
          </template>
          <template v-else-if="column.key === 'dataScope'">
            <a-tag :color="{ ALL: 'green', STORE: 'blue', SELF: 'default' }[record.dataScope] || 'default'">{{ dataScopeText(record.dataScope) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-badge :status="record.status === 1 ? 'success' : 'error'" :text="record.status === 1 ? '启用' : '停用'" />
          </template>
          <template v-else-if="column.key === 'action'">
            <div class="table-actions">
              <a-button v-permission="'print:role:update'" type="link" size="small" @click="openEdit(record)">编辑授权</a-button>
              <a-button v-permission="'print:role:update'" type="link" size="small" :disabled="isBuiltinSuperadmin(record) || !canToggle(record)" @click="toggleStatus(record)">{{ record.status === 1 ? '停用' : '启用' }}</a-button>
              <a-button v-permission="'print:role:delete'" type="link" danger size="small" :disabled="record.builtin === 1 || (record.userCount || 0) > 0" @click="deleteRole(record)">删除</a-button>
            </div>
          </template>
        </template>
      </a-table>

      <div class="mobile-list">
        <a-card v-for="record in roles" :key="record.id" size="small" class="mobile-role-card">
          <div class="role-cell"><strong>{{ record.roleName }}</strong><span>{{ record.roleKey }}</span></div>
          <div class="mobile-meta">
            <span>{{ record.builtin === 1 ? '内置' : '自定义' }}</span>
            <span>{{ dataScopeText(record.dataScope) }}</span>
            <span>{{ record.status === 1 ? '启用' : '停用' }}</span>
            <span>{{ record.userCount || 0 }} 个账户</span>
          </div>
          <div class="mobile-actions">
            <a-button v-permission="'print:role:update'" size="small" @click="openEdit(record)">编辑授权</a-button>
            <a-button v-permission="'print:role:delete'" danger size="small" :disabled="record.builtin === 1 || (record.userCount || 0) > 0" @click="deleteRole(record)">删除</a-button>
          </div>
        </a-card>
      </div>
    </a-card>

    <a-modal v-model:open="modal.visible" :title="modal.isEdit ? `编辑角色：${modal.form.roleName || ''}` : '新增角色'" ok-text="保存" cancel-text="取消" :confirm-loading="modal.saving" width="640px" @ok="submit">
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="角色名称" required><a-input v-model:value="modal.form.roleName" :maxlength="32" /></a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="角色标识" required :extra="modal.isEdit ? '角色标识创建后不可修改' : '小写字母开头，字母/数字/下划线'">
              <a-input v-model:value="modal.form.roleKey" :disabled="modal.isEdit" placeholder="如 store_manager" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="显示顺序"><a-input-number v-model:value="modal.form.roleSort" :min="0" :max="999" style="width: 100%" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="数据范围" extra="数据权限暂未启用（预留）">
              <a-select v-model:value="modal.form.dataScope">
                <a-select-option value="ALL">全部数据</a-select-option>
                <a-select-option value="STORE">本门店</a-select-option>
                <a-select-option value="SELF">仅本人</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="状态">
              <a-radio-group v-model:value="modal.form.status">
                <a-radio :value="1">启用</a-radio>
                <a-radio :value="0">停用</a-radio>
              </a-radio-group>
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="备注"><a-input v-model:value="modal.form.remark" :maxlength="255" /></a-form-item>
        <a-form-item label="菜单与权限授权" required>
          <a-tree
            v-if="menuTreeData.length"
            v-model:checkedKeys="modal.checkedKeys"
            v-model:expandedKeys="modal.expandedKeys"
            checkable
            :tree-data="menuTreeData"
            :field-names="{ children: 'children', title: 'title', key: 'key' }"
            @check="onCheck"
          />
          <a-empty v-else description="暂无可授权菜单" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { roleApi } from '@/api'

const loading = ref(false)
const roles = ref([])
const menuList = ref([])

const modal = reactive({
  visible: false,
  saving: false,
  isEdit: false,
  id: null,
  form: { roleKey: '', roleName: '', roleSort: 0, dataScope: 'SELF', status: 1, remark: '' },
  checkedKeys: [],
  halfCheckedKeys: [],
  expandedKeys: []
})

const columns = [
  { title: '角色', key: 'roleName', width: 200 },
  { title: '类型', key: 'builtin', width: 90 },
  { title: '数据范围', key: 'dataScope', width: 110 },
  { title: '账户数', dataIndex: 'userCount', width: 90 },
  { title: '状态', key: 'status', width: 90 },
  { title: '备注', dataIndex: 'remark', ellipsis: true },
  { title: '更新时间', dataIndex: 'updatedAt', width: 170 },
  { title: '操作', key: 'action', width: 230, fixed: 'right' }
]

const dataScopeText = scope => ({ ALL: '全部数据', STORE: '本门店', SELF: '仅本人' }[scope] || scope)
const isBuiltinSuperadmin = record => record.builtin === 1 && record.roleKey === 'superadmin'
const canToggle = record => !isBuiltinSuperadmin(record)

const menuTreeData = computed(() => {
  const nodes = new Map()
  menuList.value.forEach(menu => nodes.set(menu.id, { key: menu.id, title: menu.menuName, perms: menu.perms, children: [] }))
  const roots = []
  nodes.forEach(node => {
    node.title = node.perms ? `${node.title}（${node.perms}）` : node.title
  })
  menuList.value.forEach(menu => {
    const node = nodes.get(menu.id)
    if (menu.parentId && nodes.has(menu.parentId)) {
      nodes.get(menu.parentId).children.push(node)
    } else {
      roots.push(node)
    }
  })
  return roots
})

const isLeafId = computed(() => {
  const parentIds = new Set(menuList.value.map(menu => menu.parentId).filter(Boolean))
  return id => !parentIds.has(id)
})

const loadRoles = async () => {
  loading.value = true
  try {
    roles.value = (await roleApi.list())?.data || []
  } finally {
    loading.value = false
  }
}

const loadMenuTree = async () => {
  if (menuList.value.length) return
  menuList.value = (await roleApi.menuTree())?.data || []
}

const openCreate = async () => {
  modal.isEdit = false
  modal.id = null
  modal.form = { roleKey: '', roleName: '', roleSort: roles.value.length + 1, dataScope: 'SELF', status: 1, remark: '' }
  await loadMenuTree()
  modal.checkedKeys = []
  modal.halfCheckedKeys = []
  modal.expandedKeys = menuList.value.filter(menu => !menu.parentId).map(menu => menu.id)
  modal.visible = true
}

const openEdit = async record => {
  modal.isEdit = true
  modal.id = record.id
  modal.form = {
    roleKey: record.roleKey,
    roleName: record.roleName,
    roleSort: record.roleSort ?? 0,
    dataScope: record.dataScope || 'SELF',
    status: record.status ?? 1,
    remark: record.remark || ''
  }
  await loadMenuTree()
  const data = (await roleApi.menus(record.id))?.data || {}
  const assigned = new Set(data.menuIds || [])
  // 仅勾选叶子节点，父节点勾选态由树组件按子节点推导
  modal.checkedKeys = (data.menuIds || []).filter(id => isLeafId.value(id))
  modal.halfCheckedKeys = deriveHalfChecked(assigned)
  modal.expandedKeys = menuList.value.filter(menu => !menu.parentId).map(menu => menu.id)
  modal.visible = true
}

// 依据已授权集合推导半选父节点（部分子节点被授权），保存时需一并提交
const deriveHalfChecked = assigned => {
  const half = []
  const evalNode = node => {
    if (!node.children?.length) return assigned.has(node.key) ? 1 : 0
    const total = node.children.length
    const selected = node.children.reduce((sum, child) => sum + evalNode(child), 0)
    if (selected > 0 && selected < total) half.push(node.key)
    return selected
  }
  menuTreeData.value.forEach(evalNode)
  return half
}

const onCheck = (_checkedKeys, event) => {
  modal.halfCheckedKeys = event?.halfCheckedKeys || []
}

const collectMenuIds = () => {
  const checked = Array.isArray(modal.checkedKeys) ? modal.checkedKeys : modal.checkedKeys?.checked || []
  return [...new Set([...checked, ...modal.halfCheckedKeys])]
}

const submit = async () => {
  if (!modal.form.roleName?.trim()) return message.warning('请输入角色名称')
  if (!modal.isEdit && !/^[a-z][a-z0-9_]{1,31}$/.test(modal.form.roleKey || '')) {
    return message.warning('角色标识须为小写字母开头，2-32 位字母/数字/下划线')
  }
  modal.saving = true
  try {
    const payload = { ...modal.form, menuIds: collectMenuIds() }
    if (modal.isEdit) await roleApi.update(modal.id, payload)
    else await roleApi.create(payload)
    message.success(modal.isEdit ? '角色授权已更新' : '角色已创建')
    modal.visible = false
    loadRoles()
  } finally {
    modal.saving = false
  }
}

const toggleStatus = async record => {
  const next = record.status === 1 ? 0 : 1
  await roleApi.update(record.id, {
    roleName: record.roleName,
    roleSort: record.roleSort,
    dataScope: record.dataScope,
    status: next,
    remark: record.remark,
    menuIds: null
  })
  message.success(next === 1 ? '角色已启用' : '角色已停用，该角色下账户权限即刻失效')
  loadRoles()
}

const deleteRole = record => {
  Modal.confirm({
    title: '删除角色',
    content: `确定删除角色“${record.roleName}（${record.roleKey}）”吗？`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      await roleApi.delete(record.id)
      message.success('删除成功')
      loadRoles()
    }
  })
}

onMounted(loadRoles)
</script>

<style scoped lang="less">
.role-cell { display: flex; flex-direction: column; }
.role-cell strong { color: #262626; font-size: 13px; }
.role-cell span { color: #8c8c8c; font-size: 11px; }
.mobile-role-card { border: 1px solid #e5e7eb; }
.mobile-meta { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 8px; color: #8c8c8c; font-size: 12px; }
.mobile-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; padding-top: 10px; border-top: 1px solid #f0f0f0; }
@media (min-width: 901px) {
  .mobile-list { display: none; }
}
@media (max-width: 900px) {
  .desktop-table { display: none; }
}
</style>
