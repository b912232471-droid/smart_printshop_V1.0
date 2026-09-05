# Print Shop Web 重构进度与开发规范（Skill Prompt）

## 当前进度
- 完成 Web 管理端视觉/动效重构的页面：`App.vue`、`Layout.vue`、`Login.vue`、`Dashboard.vue`、`OrderList.vue`、`OrderDetail.vue`、`UserList.vue`、`AdminList.vue`、`StoreList.vue`、`ServiceList.vue`。
- 已统一的设计语言：浅灰+紫色渐变、玻璃质感卡片、柔光阴影、渐变主按钮、表格去边框卡片化、移动端卡片上滑入场、页面切换淡入上滑。
- 业务逻辑未改动：API 调用、数据模型、生命周期判断、事件/绑定 (`@click`、`v-model`、`{{}}`) 均保持原样。

## 必守开发要求
- **业务零修改**：不得改 API、store、生命周期逻辑判断。
- **绑定不变**：保留所有 `v-model`/插值/事件名/参数。
- **仅改 UI/动画**：可调样式、布局、动画；不改数据与交互逻辑。
- **风格基准**：现代 SaaS，玻璃/柔光，圆角 12–20px，紫色系主题，渐变按钮，卡片/表格去边框。
- **动效基准**：入场淡入上滑，阶梯 delay；按钮轻浮动+按压缩放；对话框/抽屉毛玻璃+scale+opacity。
- **响应式**：桌面保侧边栏；移动端用底部 Tabbar，内容上下留白；卡片点击态缩放。
- **代码注释**：仅在动画/UI关键块标注简短注释（中文），避免冗余。
- **资源限制**：保持 ASCII；不动 API 路径；不写破坏性命令。

## 下一阶段目标（小程序端）
1) 重构微信小程序页面（`miniprogram-1/pages`）：`index`、`booking`、`location`、`service-list`、`profile`、`orders`、`order-detail`，沿用同一视觉/动效基准（Apple 级留白、卡片、按钮渐变、hover-class 0.95 缩放、列表滑入等）。
2) 保持原有 `wxml` 绑定（`wx:for`、`bindtap` 等）与 `js` 逻辑不变，仅改 `.wxss` 样式和必要的结构包裹。
3) 为常用组件补充可复用的样式变量（色彩、圆角、阴影、渐变）和动画 keyframes，集中在公共样式文件（如 `app.wxss` 或单独 `styles/variables.wxss`）。

## 复用提示（给下一次对话直接使用）
```
你是本项目的 UI/动画重构助手。遵循“业务零修改、绑定不变、只改样式与动画”的原则。当前 Web 端页面已全部重构完毕（见 skill 文档），请继续处理小程序端页面（index/booking/location/service-list/profile/orders/order-detail），保持 Apple 级留白、卡片+渐变按钮、柔光阴影、页面/列表上滑淡入、按钮按压缩放 0.95，保持所有 wx:for/bindtap/数据字段不变。仅输出改动后的 .wxml/.wxss 片段或完整文件，并在注释中标注【UI美化部分】/【动画部分】。
```

