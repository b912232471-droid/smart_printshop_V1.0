# QQ 邮箱验证码配置

系统使用 QQ 邮箱提供的免费 SMTP 服务发送验证码，不依赖付费短信或邮件平台。验证码用于用户注册、用户/管理员找回密码、用户邮箱绑定，以及超级管理员创建新管理员。

## 1. 获取 QQ SMTP 授权码

1. 登录 QQ 邮箱网页版，进入“设置 -> 账号”。
2. 开启 SMTP 服务。
3. 按 QQ 邮箱提示生成授权码。授权码不是 QQ 登录密码。

## 2. 配置本地或服务器环境

在实际使用的 `.env` 文件中设置：

```properties
PRINTSHOP_MAIL_ENABLED=true
PRINTSHOP_MAIL_HOST=smtp.qq.com
PRINTSHOP_MAIL_PORT=465
PRINTSHOP_MAIL_USERNAME=你的QQ号@qq.com
PRINTSHOP_MAIL_AUTH_CODE=你的SMTP授权码
PRINTSHOP_MAIL_FROM=你的QQ号@qq.com
```

不要提交包含真实授权码的 `.env` 文件。修改配置后重新创建 `print-service` 容器。

## 3. 首个管理员

管理员也只能使用 QQ 邮箱登录。全新环境可临时配置：

```properties
PRINTSHOP_BOOTSTRAP_ADMIN_ENABLED=true
PRINTSHOP_BOOTSTRAP_ADMIN_EMAIL=管理员QQ号@qq.com
PRINTSHOP_BOOTSTRAP_ADMIN_PASSWORD=至少8位的强密码
```

首个管理员创建成功后立即把 `PRINTSHOP_BOOTSTRAP_ADMIN_ENABLED` 改回 `false`，并清空引导密码。若当前库只有不带有效 QQ 邮箱的历史管理员，引导程序会额外创建配置的恢复超级管理员；它不会覆盖或删除历史数据。

## 4. 安全限制

- 验证码有效期默认 5 分钟，发送冷却默认 60 秒。
- 单个邮箱每天默认最多发送 20 次。
- Redis 只保存验证码哈希；验证成功后以原子操作删除，不能重复使用。
- 只接受 `@qq.com` 邮箱，邮箱密文存储，查询使用 HMAC 盲索引。
