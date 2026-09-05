# Printshop Web

智慧在线打印平台的统一 Web 应用。管理端和用户端共用同一个 Vue 3、Vite、Router 和登录页面。

## 页面入口

- `/login`：统一登录页，可选择用户端或管理端
- `/client`：用户端首页
- `/dashboard`：管理端工作台

用户端和管理端运行在同一个端口，不需要分别启动。

## 本地开发

```bash
npm install
npm run dev
```

默认访问地址为 `http://127.0.0.1:3000/`，`/api` 会代理到 `http://localhost:8080` 的 Gateway。

## 构建

```bash
npm run build
```

统一构建产物位于 `dist/`，生产环境由 Nginx 托管并将所有页面路由回退到 `index.html`。
