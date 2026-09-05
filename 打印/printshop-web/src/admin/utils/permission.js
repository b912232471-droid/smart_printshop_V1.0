import { usePermissionStore } from '@/stores/permission'

export const hasPerm = perm => {
  const store = usePermissionStore()
  return store.hasPerm(perm)
}

const satisfies = (store, value) =>
  Array.isArray(value) ? value.some(perm => store.hasPerm(perm)) : store.hasPerm(value)

export const permissionDirective = {
  mounted(el, binding) {
    if (binding.value === undefined || binding.value === null) return
    const store = usePermissionStore()
    if (!satisfies(store, binding.value)) {
      el.parentNode?.removeChild(el)
    }
  }
}
