import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue'), meta: { title: '登录' } },
  { path: '/mobile', name: 'MobileHome', component: () => import('@/views/mobile/MobileHome.vue'), meta: { title: '手机端' } },
  { path: '/mobile/inbound-scan', name: 'MobileInboundScan', component: () => import('@/views/mobile/MobileInboundScan.vue'), meta: { title: '手机扫码入库' } },
  { path: '/mobile/inbound-orders', name: 'MobileInboundOrders', component: () => import('@/views/mobile/MobileInboundOrders.vue'), meta: { title: '手机入库单' } },
  { path: '/mobile/outbound-scan', name: 'MobileOutboundScan', component: () => import('@/views/mobile/MobileOutboundScan.vue'), meta: { title: '手机带单出库' } },
  { path: '/mobile/outbound-orders', name: 'MobileOutboundOrders', component: () => import('@/views/mobile/MobileOutboundOrders.vue'), meta: { title: '手机出库单' } },
  { path: '/mobile/direct-outbound', name: 'MobileDirectOutbound', component: () => import('@/views/mobile/MobileDirectOutbound.vue'), meta: { title: '手机不带单出库' } },
  { path: '/mobile/repack', name: 'MobileRepack', component: () => import('@/views/mobile/MobileRepack.vue'), meta: { title: '手机转包' } },
  { path: '/mobile/repack-orders', name: 'MobileRepackOrders', component: () => import('@/views/mobile/MobileRepackOrders.vue'), meta: { title: '手机转包单' } },
  { path: '/mobile/return', name: 'MobileReturn', component: () => import('@/views/mobile/MobileReturn.vue'), meta: { title: '手机退库' } },
  { path: '/mobile/kanban', name: 'MobileKanban', component: () => import('@/views/mobile/MobileKanban.vue'), meta: { title: '手机看板查询' } },
  {
    path: '/',
    component: () => import('@/views/Layout.vue'),
    redirect: '/home',
    children: [
      { path: 'home', name: 'Home', component: () => import('@/views/Home.vue'), meta: { title: '首页' } },
      { path: 'ai/admin', name: 'AiWarehouseAdmin', component: () => import('@/views/ai/AiWarehouseAdmin.vue'), meta: { title: 'AI仓库管理员' } },
      { path: 'inbound/order', name: 'InboundOrder', component: () => import('@/views/inbound/InboundOrder.vue'), meta: { title: '入库单管理' } },
      { path: 'order/item-batch', name: 'OrderItemBatchPicker', component: () => import('@/views/common/OrderItemBatchPicker.vue'), meta: { title: '批量添加明细' } },
      { path: 'inbound/kanban', redirect: '/kanban/manage' },
      { path: 'kanban/manage', name: 'KanbanManage', component: () => import('@/views/inbound/KanbanManage.vue'), meta: { title: '看板管理' } },
      { path: 'inbound/scan', name: 'KanbanScan', component: () => import('@/views/inbound/KanbanScan.vue'), meta: { title: '扫码入库' } },
      { path: 'outbound/order', name: 'OutboundOrder', component: () => import('@/views/outbound/OutboundOrder.vue'), meta: { title: '出库单管理' } },
      { path: 'outbound/scan', name: 'OutboundScan', component: () => import('@/views/outbound/OutboundScan.vue'), meta: { title: '带单出库' } },
      { path: 'outbound/direct-scan', name: 'DirectOutboundScan', component: () => import('@/views/outbound/DirectOutboundScan.vue'), meta: { title: '不带单出库' } },
      { path: 'operations/repack', name: 'RepackManage', component: () => import('@/views/operations/RepackManage.vue'), meta: { title: '转包单管理' } },
      { path: 'operations/repack-scan', name: 'RepackScan', component: () => import('@/views/operations/RepackScan.vue'), meta: { title: '转包作业' } },
      { path: 'operations/repack-balance', name: 'RepackBalance', component: () => import('@/views/operations/RepackBalance.vue'), meta: { title: '转包结余' } },
      { path: 'baseinfo/supplier', name: 'SupplierInfo', component: () => import('@/views/baseinfo/SupplierInfo.vue'), meta: { title: '供应商管理' } },
      { path: 'baseinfo/customer', name: 'CustomerInfo', component: () => import('@/views/baseinfo/CustomerInfo.vue'), meta: { title: '客户管理' } },
      { path: 'baseinfo/part', name: 'PartInfo', component: () => import('@/views/baseinfo/PartInfo.vue'), meta: { title: '零件管理' } },
      { path: 'baseinfo/warehouse', name: 'WarehouseInfo', component: () => import('@/views/baseinfo/WarehouseInfo.vue'), meta: { title: '仓库管理' } },
      { path: 'baseinfo/location', name: 'LocationInfo', component: () => import('@/views/baseinfo/LocationInfo.vue'), meta: { title: '库位管理' } },
      { path: 'baseinfo/container', name: 'ContainerInfo', component: () => import('@/views/baseinfo/ContainerInfo.vue'), meta: { title: '器具管理' } },
      { path: 'inventory/stock', name: 'StockSummary', component: () => import('@/views/inventory/StockSummary.vue'), meta: { title: '库存监控' } },
      { path: 'inventory/report', name: 'InventoryReport', component: () => import('@/views/inventory/InventoryReport.vue'), meta: { title: '总库存报表' } },
      { path: 'inventory/trace', name: 'InventoryTrace', component: () => import('@/views/inventory/InventoryTrace.vue'), meta: { title: '库存追溯' } },
      { path: 'system/user', name: 'UserManage', component: () => import('@/views/children/UserManage.vue'), meta: { title: '用户管理' } },
      { path: 'system/role', name: 'RoleManage', component: () => import('@/views/children/RoleManage.vue'), meta: { title: '角色权限' } }
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) next('/login')
  else if (to.path === '/login' && token) next('/')
  else next()
})

export default router
