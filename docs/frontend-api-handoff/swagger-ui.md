# 使用 Swagger 网页测试后端

## 打开页面

按 [可视化运行步骤](gui-running.md) 启动项目数据库和后端，然后在浏览器打开：

**http://127.0.0.1:8080/swagger-ui/index.html**

也可使用 `http://127.0.0.1:8080/swagger-ui.html`，会自动跳转。不要把 `/api/v1` 加在 Swagger 页面地址前。

页面展示当前已实现的 35 个 P0 合同操作，另有图片二进制上传、下载操作。网页中的请求直接访问当前后端，**Execute 会实际读取或修改数据库**。请使用虚构患者数据。

## 页面布局与搜索

- **左侧API汇总**：按模块分组，显示请求方法、接口名称和路径。可折叠模块，或输入名称、路径、GET/POST等方法筛选，支持组合搜索，例如 `POST /patients`。
- **右侧测试区**：只显示当前选中的接口。参数编辑默认开启，填好后直接点击 **Execute**；下方显示状态码、响应正文、响应头和耗时。
- **切换接口**：当前页面内已填写的参数和测试响应会保留；刷新页面会重新初始化。接口链接可以复制给他人，在同一可达后端上打开时直接选中相应接口。
- **使用说明**：点击右上角展开登录、版本控制和图片流程提示；测试PNG下载也移至这里。
- **快捷键**：按 `Ctrl + K`（Mac为`Command + K`）聚焦搜索；窄屏通过“接口列表”按钮打开导航。

如果运行的是旧程序，在 IDEA 中先停止，打开右侧 **Maven → Lifecycle → package**，双击打包；成功后重新运行 **Backend Local - JAR**。页面资源已随 JAR 打包，运行时无需访问 CDN，也不用另外安装 Swagger、Java 或数据库。

## 第一次测试：健康检查和登录

1. 在左侧“运维”中选择 `GET /system/health`，也可以搜索 `system/health`。
2. 在右侧点击 **Execute**。在下方 **Server response** 查看状态，预期 `200`，正文含 `"status":"UP"`。
3. 在左侧“认证”中选择 `POST /auth/login`，右侧会直接显示可编辑正文。
4. 将请求正文的 `account` 填为 `local-owner`；`password` 填本机 `scripts/local.ps1` 中的 `BOOTSTRAP_PASSWORD`，已经改过密码则填新密码。这里不用 MySQL 的 root 或 derm_app 密码。
5. 点击 **Execute**。成功后在 **Response body** 复制 `accessToken` 的值，不复制引号。
6. 点击右侧测试区顶部 **Authorize**，在 `bearerAuth` 输入框粘贴令牌，点击 **Authorize → Close**。**只填令牌本身，不要再加 `Bearer `。**
7. 执行 `GET /auth/me`，预期 `200`。其他受保护接口现在也能携带登录凭证调用。

页面刷新后需重新授权；访问令牌约15分钟有效。可使用 `/auth/refresh` 刷新并重新授权，或重新登录。每次刷新后必须使用新的 refreshToken，重复旧值会撤销整个令牌族。

## 创建、查询和修改数据

按患者 → 病灶 → 图片 → 采集的顺序操作。在左侧选择接口后，右侧显示对应参数、请求示例和响应类型。

- **创建患者**：`POST /patients`。示例中已生成 UUID 和独立的 `Idempotency-Key`，可直接使用；成功后复制响应 `id`，供后续请求引用。
- **创建病灶**：`POST /lesions`，将 `patientId` 改成实际患者 ID，成功后复制病灶 ID。
- **查询详情**：在 GET 路径参数中填写实际 ID。修改前，在实际响应的 **Response headers** 中找到 `etag`，例如 `"1"`。
- **修改/删除**：将该资源最新 ETag 原样填写到 `If-Match`，保留双引号。患者、病灶、采集各有自己的版本，不能混用。
- **新建业务请求**：使用新的 `Idempotency-Key`。只因网络中断而重试同一请求时保留原键和正文；改变正文或修复已确定的失败后，换新键。

页面加载时会生成一组彼此关联的示例 UUID，刷新页面会换一组。**实际父子引用以已成功创建的响应为准**，不要在刷新后继续使用新示例 ID 引用旧数据。上传 ID、文件 ID 等由服务端生成，须从响应复制。

`204` 表示成功且没有响应正文；`201` 表示创建成功；`412` 表示版本冲突，应重新查询并核对内容；`428` 表示缺少 If-Match。

## 在 Swagger 中上传图片

整个流程可以在浏览器中完成：

1. 打开右上角“使用说明”，点击 **下载20×20测试PNG**，保存原始图片，不要截图或重新编码。
2. 执行 `POST /file-uploads`。将示例的 `patientId` 改为实际患者 ID，其他字段保留即可：随附图片是 PNG、83字节、20×20，示例已填好 SHA-256。
3. 在返回正文找到 `uploadUrl`。例如其结构是：

   ```text
   http://127.0.0.1:8080/objects/上传会话UUID?ticket=完整签名值
   ```

4. 在左侧“图片传输”选择 `PUT /objects/{objectId}`。将 `/objects/` 后、`?` 前的 UUID 填入 `objectId`，将 `ticket=` 后的完整值填入 `ticket`。这两个框都不填写完整 URL。
5. 请求正文类型选择 **image/png**，通过文件选择按钮选中原始测试 PNG，点击 **Execute**。预期 `204`。
6. 执行 `POST /file-uploads/{uploadId}/complete`：路径填上传会话 ID，填写新幂等键，请求正文保持 `{}`。预期 `200`、`state: READY`。
7. 执行 `POST /captures`：`lesionId` 填实际病灶 ID，`imageFileId` 填上传响应的 **fileId**；可以保留示例的空分析和标注。预期 `201`。

上传 ID 与文件 ID 不是同一个值。签名上传地址约5分钟有效；超时可在尚未完成的会话上执行 GET `/file-uploads/{uploadId}` 或 POST `/file-uploads/{uploadId}/sign-single` 获取新签名。续签请求应换新幂等键，避免重放已过期的旧响应。

实际照片需要同时修改大小、哈希、类型和宽高，不能套用测试图参数。最大100MiB，支持 JPEG/PNG，当前不支持 WebP 或分片上传。PUT 成功后必须 complete；文件 READY 后不可覆盖。

下载时先执行 `POST /files/{fileId}/download-url`，再直接在浏览器打开返回的 `url`；也可在“图片传输”的 GET 中填写文件 ID 与对应 ticket。下载签名约2分钟有效。

## 注意事项与更多示例

- `/users/me/password` 会更改真实 API 登录密码；DELETE 患者会级联软删除子病灶和采集，测试前核对 ID。
- `/jobs/{jobId}` 当前没有可查询的异步任务，合法未知 UUID 返回404是预期行为。
- Swagger 文档 JSON 地址为 `/v3/api-docs`。它基于合同的副本生成，不修改用于业务校验的原合同。
- 更完整的同步流程、异常用例和错误码见 [接口测试文档](api-testing.md)。该文档中的 Postman 是可选辅助方式。

页面配置参考 [Swagger UI 官方配置说明](https://swagger.io/docs/open-source-tools/swagger-ui/usage/configuration/)。本项目关闭外部在线校验器及跨刷新保存授权令牌。
