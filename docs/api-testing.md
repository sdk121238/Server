# 后端接口测试与调试文档

适用日期：2026-11-05。对应当前 Java 后端的**个人工作区 P0 实现**，覆盖 35 个合同操作，另含签名图片上传、下载操作。

本文按现有代码编写。医院机构与授权、审核、短信注册与找回密码、云草稿、分片上传、批量同步和服务器算法等 P1/P2 功能尚未启用，不能按完整合同中出现的所有接口进行成功验收。应登录后先查看 `/system/capabilities`。

**浏览器直接调试：** 启动后打开 **http://127.0.0.1:8080/swagger-ui/index.html**。参见 [Swagger 网页操作说明](swagger-ui.md)，可直接填写参数并执行请求，也可以上传测试图片。下文 Postman 集合为可选辅助方式。

## 1. 调试文件与准备工作

| 文件 | 用途 |
|---|---|
| [Postman 请求集合](testing/followup-p0.postman_collection.json) | 导入后按顺序点击 Send，自动保存令牌、ID 和版本号 |
| [测试图片 sample-20x20.png](testing/sample-20x20.png) | 图片上传使用的原始 PNG 文件，20 × 20 像素 |
| [测试图片参数](testing/sample-image.json) | 文件大小、SHA-256、类型及尺寸，与集合中的上传参数一致 |
| [可视化启动说明](gui-running.md) | 使用 IDEA、文件资源管理器启动数据库和后端 |
| [完整 OpenAPI 合同](backend/backend-v1.openapi.json) | 查看字段定义或导入其他接口工具，包含尚未实现的扩展接口 |

先按启动说明运行项目数据库，再在 IDEA 中运行 **Backend Local - JAR**。浏览器访问：

```text
http://127.0.0.1:8080/api/v1/system/health
```

预期 HTTP `200`，正文含 `"status":"UP"`。此接口用于确认 HTTP 服务可用，不代表数据库和图片存储的全部操作均正常。

| 项目 | 本机配置 |
|---|---|
| API 基础地址 | `http://127.0.0.1:8080/api/v1` |
| 项目 MySQL | `127.0.0.1:3307`，数据库 `derm_followup` |
| 数据库用户名 | `derm_app`，仅用于 Workbench / 后端数据库连接 |
| 初始 API 账号 | `local-owner` |
| API 登录密码 | 本机 `scripts/local.ps1` 中 `BOOTSTRAP_PASSWORD` 的值；已经通过接口改过密码则用新密码 |

API 调试使用 **8080** 端口。Workbench 使用 **3307** 端口。数据库的 `root` 密码、`DB_PASSWORD` / `appPassword` 均不能用于 API 登录。本文件和请求集合不保存真实密码。

## 2. 使用 Postman 图形界面调试

### 2.1 导入与配置

1. 打开 Postman，点击 **Import → Files**，选择项目中的 `docs/testing/followup-p0.postman_collection.json`。
2. 在左侧 **Collections** 找到“皮肤病随访后端 P0 调试”。点击集合名称，打开 **Variables**。
3. 确认 `baseUrl` 是上表地址，`account` 是 `local-owner`。在本地变量值中填入 `password`，点击 **Save**。也可以在本地环境中单独定义同名 `password`，并选中该环境。
4. 保持自动生成的 `accessToken`、`patientId`、`patientEtag` 等变量名只在集合中定义，避免同名环境变量覆盖自动保存的结果。
5. 展开文件夹，先逐条发送“01 启动检查与登录”，再发送“02 患者与病灶”。每次检查状态码、响应正文和 **Test Results**。
6. “03 图片上传与采集”的第 03 条请求，需要进入 **Body → binary → Select File**，选择随附的 `sample-20x20.png`，再点击 **Send**。不要选择 form-data。
7. 完成图片与采集后再测试“04 快照与增量同步”。“05 按需测试”和“06 清理测试记录与退出”按本文说明单独执行。

集合带有请求脚本，会生成 UUID 和各操作独立的幂等键；成功响应会保存令牌、资源 ID、ETag 和签名地址。登录、刷新、健康检查和签名对象读写已设置 **No Auth**；其他请求继承集合的 **Bearer Token**。

**首次调试请逐条点击 Send。** 整个集合包含条件分页、文件选择、修改密码和删除操作，不适合不加选择地全部运行。若改用 Apifox，可导入完整 OpenAPI 合同，然后按本文手动配置变量与请求；不要假定 Postman 脚本在其他工具中一定有相同效果。

### 2.2 重试和重新开始一次流程

集合中的 `idem_patientCreate`、`idem_uploadCreate` 等键在第一次发送对应请求时生成，随后保持不变，便于模拟网络重试。

- **上次响应未知，需要重试同一业务请求**：保持请求方法、地址、正文和幂等键不变。
- **要修改正文后重新提交，或已收到确定失败并修正条件**：清空对应 `idem_*` 集合变量的值，保存后再发送，脚本会生成新键。例如上传确认先失败、补传文件后，应清空 `idem_uploadComplete`。
- **开始全新一轮数据测试**：重新导入一份干净的集合最方便；也可清空旧的 `patientId`、`lesionId`、`captureId`、上传及同步变量，并清空 `idem_*`。重新登录，然后从创建患者开始。
- **每次重新申请上传签名或新建快照**：清空 `idem_uploadSign` 或 `idem_snapshotCreate` 后再发送。复用旧键会重放旧响应，其中的地址或快照可能已经过期。

已删除的 UUID 仍然占用，不能通过重新创建同一 ID 恢复记录。创建接口重放返回 `201` 也可能只是原响应，不代表又创建了一条数据。

## 3. 所有请求的通用规则

本文后续路径均相对于 `{{baseUrl}}`；签名 `uploadUrl`、`downloadUrl` 是完整地址，直接使用，不要再拼 `/api/v1`。

| 请求头 | 使用方式 |
|---|---|
| `Authorization` | 除登录、刷新、健康检查及签名对象读写外，使用 `Bearer {{accessToken}}` |
| `Content-Type: application/json` | 有 JSON 正文的普通 POST；上传完成必须发送 `{}` |
| `Content-Type: application/merge-patch+json` | PATCH 请求；工具可能默认填 application/json，需要手动更改 |
| `Idempotency-Key` | 下文表格标“幂等键”的 POST 必填，1～128 字符；不同业务操作使用不同键 |
| `If-Match` | 修改资料、修改/删除患者、病灶、采集时必填；直接使用该资源最新的响应头 ETag，如 `"1"`，双引号也要保留 |

注意以下字段规则：

- 创建患者、病灶、采集的 `id` 由客户端生成 UUID；上传会话、文件、导入计划、快照 ID 由服务端生成。不要把病灶 ID 与患者 ID 混用。
- `capturedAt`、`createdAt`、`expiresAt` 等为 Unix **毫秒**时间戳；`birthDate` 是 `YYYY-MM-DD`。正文中的数字、布尔值、`null` 不加引号。
- 不能附加 DTO 未定义的字段，例如自填 `ownerUserId`、`revision`、`reviewStatus`；创建与读取的字段集合不同。
- 单资源响应直接是资源对象，没有统一 `data` 包装。一般列表是 `{"items": [...], "nextPageToken": null}`。
- PATCH 未传的字段保持原值；嵌套对象递归合并，数组整体替换。仅支持字段定义允许的 `null`，例如 `{"birthDate":null}`；不要将 `null` 用于必填非空字段。
- 患者、病灶、采集各有独立 `revision`。资料版本从 ETag 读取，资料正文没有 `revision`。不要混用这些版本。
- 普通分页 `limit` 为 1～100，默认 50；增量同步默认 100。后续页面使用返回的 `nextPageToken`，保留原筛选条件；`null` 表示结束。
- 需要记录问题时保存 HTTP 状态、错误正文及响应头 `X-Request-Id`，不要把登录密码、完整令牌或签名地址发到公开渠道。

## 4. 最短业务闭环：登录 → 患者 → 病灶 → 图片 → 采集

### 4.1 登录并取得令牌

`POST /auth/login`，无需 Authorization：

```json
{
  "account": "{{account}}",
  "password": "{{password}}"
}
```

预期 `200`，响应结构如下，示例中的省略号不是实际令牌：

```json
{
  "profile": {
    "id": "用户UUID",
    "account": "local-owner",
    "displayName": "local-owner",
    "role": "PATIENT",
    "organizationId": null,
    "permissions": ["patient:create", "patient:read"]
  },
  "accessToken": "...",
  "refreshToken": "...",
  "expiresAt": 1700000900000
}
```

`permissions` 这里只展示部分项目。集合自动保存两个令牌；手动调试时复制到变量中。访问令牌约 15 分钟有效，刷新令牌约 30 天；两者不是数据库账号密码。`requestedRole` 即使填 `HOSPITAL_STAFF` 也不会获得医院权限。

接着发送 `GET /auth/me`，预期 `200`，记下响应头 `ETag`；发送 `GET /system/capabilities`，当前应包括：

```json
{
  "apiVersion": "1.0.0",
  "maxUploadBytes": 104857600,
  "multipartEnabled": false,
  "maxSyncBatch": 0,
  "maxPageSize": 100,
  "cloudDraftsEnabled": false,
  "serverAlgorithmsEnabled": false,
  "acceptedMimeTypes": ["image/jpeg", "image/png"]
}
```

### 4.2 创建和修改患者

`POST /patients`，带 Bearer、JSON 类型和新的 `Idempotency-Key`：

```json
{
  "id": "{{patientId}}",
  "name": "接口调试患者",
  "birthDate": "1990-01-01"
}
```

集合会自动生成 `patientId`。预期 `201`，返回对象含原 ID、`revision: 1`、服务端时间和 `deletedAt: null`，响应头 `ETag: "1"`。

| 操作 | 请求 | 预期 |
|---|---|---|
| 读取详情 | `GET /patients/{{patientId}}` | `200`，ID 与创建结果一致 |
| 搜索 | `GET /patients?q=接口调试&limit=10` | `200`，`items` 中包含测试患者 |
| 修改姓名和清空生日 | `PATCH /patients/{{patientId}}`，If-Match 使用最新患者 ETag | `200`；实际发生修改则 revision 递增 |

PATCH 示例：

```json
{
  "name": "接口调试患者（已修改）",
  "birthDate": null
}
```

修改后保存新的 ETag。发送完全相同、未改变数据的补丁时，版本可能保持不变。

### 4.3 创建和修改病灶

`POST /lesions`，带幂等键：

```json
{
  "id": "{{lesionId}}",
  "patientId": "{{patientId}}",
  "bodySite": "左手背",
  "note": "调试用虚构数据"
}
```

预期 `201`，保存病灶 ETag。用 `GET /lesions/{{lesionId}}` 查看详情，用 `GET /patients/{{patientId}}/lesions?limit=10` 查看患者的病灶列表。

`PATCH /lesions/{{lesionId}}`，带病灶自己的 If-Match：

```json
{
  "note": "调试备注已修改"
}
```

预期 `200`，保存最新病灶 ETag。创建接口的 `patientId` 必须是当前账号可访问的患者。

### 4.4 创建图片上传会话

`POST /file-uploads`，带幂等键。以下参数仅适用于随附的**原始**测试文件：

```json
{
  "patientId": "{{patientId}}",
  "sizeBytes": 83,
  "sha256": "0aedc9bece813993a4241852d4826e275f95d7cd3877a25e052922f2b84e3c0c",
  "mimeType": "image/png",
  "width": 20,
  "height": 20,
  "strategy": "SINGLE"
}
```

预期 `201`，保存响应的 `id → uploadId`、`fileId`、`uploadUrl` 及 `requiredHeaders`。此时文件尚未 READY，不能创建引用它的采集。

使用自己的图片时，所有参数必须改成实际文件值。在 Windows 文件属性中查看大小时使用“大小”的字节数，不能使用“占用空间”；在“详细信息”中查看宽高。需要 SHA-256 时，可在 PowerShell 可选执行：

```powershell
(Get-FileHash -LiteralPath 'C:\图片目录\test.png' -Algorithm SHA256).Hash.ToLowerInvariant()
```

不想执行命令时直接使用随附测试图片。不要用截图、另存、压缩或改格式后的图片搭配原哈希。当前仅支持 JPEG / PNG、SINGLE，文件最大 100 MiB，宽高各不超过 20000，总像素不超过 4000 万。

### 4.5 上传原始图片，再确认上传

在 Postman 中打开集合的 PUT 请求：

1. 方法设为 **PUT**，URL 使用完整 `{{uploadUrl}}`。
2. **Authorization → No Auth**。
3. **Headers** 中使用响应 `requiredHeaders` 的值，测试图片为 `Content-Type: image/png`。
4. **Body → binary → Select File**，选择项目里的 `docs/testing/sample-20x20.png`。
5. 点击 **Send**，预期 `204`，无响应正文。

上传地址位于 `/objects/...`，不在 `/api/v1` 下。它不是 JSON 接口，不能提交文件路径、Base64 字符串或 multipart/form-data。PUT 成功仅表示字节已接收，随后还需确认：

`POST /file-uploads/{{uploadId}}/complete`，带一个独立幂等键和 JSON 正文：

```json
{}
```

预期 `200`，返回文件元数据，`state` 必须为 `READY`。确认时会核验真实文件大小、SHA-256、类型、尺寸和可解码性。

签名上传地址最多 5 分钟有效，上传会话约 24 小时有效。签名过期而会话未完成时，可 `GET /file-uploads/{{uploadId}}` 取得新地址，或使用带新幂等键的 `POST /file-uploads/{{uploadId}}/sign-single`，后者预期 `201`。重新 PUT 后再确认。READY 文件不可覆盖；需要换图时创建新上传会话。

### 4.6 查看和下载图片

- `GET /files/{{fileId}}`：预期 `200`，核对 `state`、`sha256`、`sizeBytes`、宽高。
- `POST /files/{{fileId}}/download-url`：带 Bearer，无需正文或幂等键，预期 `200`。保存响应 `url → downloadUrl`。
- `GET {{downloadUrl}}`：直接使用完整地址、No Auth，预期 `200`，返回图片二进制。也可复制完整地址到浏览器查看或下载。

下载签名约 2 分钟有效，过期后重新申请；不要将短期地址当成文件的永久 ID。删除患者后，原文件地址也不可继续访问。

### 4.7 创建、读取和修改采集

`POST /captures`，带新的幂等键；必须先完成上传并取得 READY 文件：

```json
{
  "id": "{{captureId}}",
  "lesionId": "{{lesionId}}",
  "imageFileId": "{{fileId}}",
  "capturedAt": 1700000000000,
  "deviceModel": "Postman-Debug",
  "source": "GALLERY",
  "assetKind": "CAPTURE_IMAGE",
  "referenceOnly": false,
  "qualityNote": "合成图片，仅用于接口联调",
  "calibration": null,
  "analysis": null,
  "annotation": null,
  "metadata": {}
}
```

这里的时间是便于复现的固定历史毫秒值，实际客户端应填真实采集时间。`referenceOnly` 必须出现，可为布尔值或 `null`。

预期 `201`，`revision: 1`、`contentRevision: 1`、`reviewStatus: NOT_SUBMITTED`。文件必须属于病灶所在的同一患者；其他患者的文件不能引用。

- `GET /captures/{{captureId}}`：读取并保存最新采集 ETag。
- `GET /lesions/{{lesionId}}/captures?limit=10&fromAt=0&toAt=2000000000000`：按采集时间、ID 升序返回列表。时间范围为 `fromAt` 包含、`toAt` 不包含，起点必须小于终点。
- `PATCH /captures/{{captureId}}`：带采集 ETag，正文 `{"qualityNote":"已完成接口联调"}`，预期 `200`。实际内容变化会递增 `revision`、`contentRevision` 并重置审核状态。

### 4.8 可选：标注与面积校验

下列补丁仅适用于随附 20 × 20 图片，使用当前采集 ETag：

```json
{
  "calibration": {
    "markerId": "debug-marker",
    "markerWidthMm": 10,
    "pixelsPerMm": 2,
    "qualityScore": 1
  },
  "annotation": {
    "points": [
      {"x": 0, "y": 0},
      {"x": 10, "y": 0},
      {"x": 10, "y": 10},
      {"x": 0, "y": 10}
    ],
    "imageWidth": 20,
    "imageHeight": 20,
    "edited": true
  },
  "analysis": {
    "isValid": true,
    "areaMm2": 25,
    "algorithmVersion": "manual-debug"
  }
}
```

预期 `200`：轮廓面积 100 像素²，尺度 2 像素/mm，所以面积是 25 mm²。标注宽高要等于真实文件宽高，点必须在图内，轮廓不能自交；有效分析必须有合法尺度和标注，且不能标为仅参考。

再只修改 `{"calibration":{"pixelsPerMm":3}}`，不附完整重算分析时，预期原 `analysis` 被清空为 `null`。这是防止旧面积误用的行为。服务器目前不自动运行图像分析算法。

## 5. 同步测试：先快照，再增量

1. 发送 `POST /sync/snapshots`，带新幂等键，**无正文**。预期 `201`，保存返回 `id → snapshotId`。
2. 发送 `GET /sync/snapshots/{{snapshotId}}?limit=100`。
3. 若响应 `nextPageToken` 非 `null`，发送 `GET /sync/snapshots/{{snapshotId}}?limit=100&pageToken={{snapshotPageToken}}`，重复直到末页。只有末页提供 `nextCursor`，集合会将其保存为 `syncCursor`。
4. 此时第一次发送 `GET /sync/changes?cursor={{syncCursor}}&limit=100`，若期间没有业务写入，`items` 可能为空，这是正常结果。
5. 修改一次测试患者或采集，再发送增量请求，预期出现对应 `UPSERT`，`payload` 是完整资源。
6. 每次读取都保存返回的 `nextCursor`；`hasMore: true` 时继续拉取，直到 `false`。空页同样更新游标。
7. 清理阶段删除患者后，在退出登录前继续读取增量，预期看到患者及仍存在的子病灶、采集的 `DELETE` 事件，`payload: null`。

快照响应结构：

```json
{
  "snapshotId": "快照UUID",
  "items": [],
  "nextPageToken": null,
  "nextCursor": "服务端返回的同步游标"
}
```

增量响应结构：

```json
{
  "items": [
    {
      "entityType": "PATIENT",
      "entityId": "患者UUID",
      "operation": "DELETE",
      "revision": 3,
      "payload": null
    }
  ],
  "nextCursor": "下一次使用的游标",
  "hasMore": false
}
```

快照约 24 小时有效，普通分页令牌约 24 小时，增量游标约 90 天。普通分页令牌、快照分页令牌、同步游标不能互换；跨账号或更改原筛选条件也不能复用。收到 `410 SYNC_RESET_REQUIRED` 时重新创建快照并完整分页，不要手动编造游标。

若要重新生成快照，请换新幂等键；复用原键得到的仍是原来那个冻结快照。

## 6. 其他接口调试

### 6.1 刷新、资料修改、密码修改和退出

| 操作 | 请求和正文 | 特殊要求 | 预期 |
|---|---|---|---|
| 刷新令牌 | `POST /auth/refresh`，`{"refreshToken":"{{refreshToken}}"}` | 无需 Bearer；成功后同时替换 accessToken 和 refreshToken | `200` |
| 修改显示名 | `PATCH /users/me`，`{"displayName":"接口调试用户"}` | merge-patch 类型，If-Match 使用 `/auth/me` 的 ETag | `200`，保存新资料 ETag |
| 修改密码 | `POST /users/me/password`，`{"currentPassword":"{{password}}","newPassword":"{{newPassword}}"}` | Bearer + 幂等键；新密码至少 12 字符，UTF-8 不超过 72 字节 | `204` |
| 退出 | `POST /auth/logout`，`{"refreshToken":"{{refreshToken}}"}` | Bearer 与 refreshToken 必须对应同一当前会话 | `204` |

刷新成功后旧 refreshToken 已被消耗；**再次提交旧值会使整个令牌族失效**，包括刚刷新出的令牌。不要并发刷新或将旧值反复点击发送。只有有意验证重放保护时才测试此场景，之后重新登录。

改密码会改变本机真实 API 账号密码，且撤销其他登录会话。集合默认不提供新密码，需主动设置 `newPassword`；成功后同步修改调试工具中的 `password`。只改 `BOOTSTRAP_PASSWORD` 不会重置数据库内已有账号。

退出后再次访问 `/auth/me` 应得到 `401`；继续其他测试需要重新登录。

### 6.2 旧数据导入范围确认

`POST /offline-imports`，带幂等键：

```json
{
  "clientDatasetId": "{{clientDatasetId}}",
  "confirmedPatientIds": ["{{importPatientId}}"]
}
```

预期 `201`，返回 `id`、`allowedPatientIds`、`expiresAt`。集合会生成数据集 UUID 和一个待导入患者 UUID，保存响应 `id → importId`。

随后可用新幂等键发送 `POST /patients`：

```json
{
  "id": "{{importPatientId}}",
  "name": "旧数据导入测试患者",
  "importId": "{{importId}}"
}
```

预期 `201`。计划只表示范围确认，不会自动生成患者。导入其他未确认的患者应得到 `403`；计划约 30 天有效。个人工作区不要填写非空 `targetOrganizationId`。这条额外患者需单独使用其最新 ETag 删除，不在主流程的 `patientId` 清理范围内。

### 6.3 中止上传与任务查询

- `DELETE /file-uploads/{{uploadId}}`：仅对未完成上传测试，预期 `204`，无需 If-Match；READY 上传会返回 `409 INVALID_TRANSITION`。测试中止后需创建新的上传会话来继续主流程。
- `GET /jobs/{{jobId}}`：传一个合法但不存在的 UUID，预期 `404 NOT_FOUND`。当前 P0 操作同步执行，不会生成异步任务；不能把这个 404 视为后端没有启动。

## 7. 完整 P0 接口速查（35 项）

“鉴权”均指 Bearer；“版本”均指 If-Match；“幂等”均指 Idempotency-Key。没有正文的请求在工具中选择 Body → none。

| # | 方法与路径 | 鉴权 | 其他请求要求 | 成功/当前预期 |
|---|---|---|---|---|
| 1 | `GET /system/health` | 否 | 无正文 | 200 |
| 2 | `GET /system/capabilities` | 是 | 无正文 | 200 |
| 3 | `POST /auth/login` | 否 | JSON 账号密码 | 200 |
| 4 | `POST /auth/refresh` | 否 | JSON refreshToken | 200 |
| 5 | `POST /auth/logout` | 是 | JSON refreshToken | 204 |
| 6 | `GET /auth/me` | 是 | 返回资料 ETag | 200 |
| 7 | `PATCH /users/me` | 是 | 版本、merge-patch | 200 |
| 8 | `POST /users/me/password` | 是 | 幂等、JSON | 204 |
| 9 | `GET /patients` | 是 | 可选 q、limit、pageToken | 200 |
| 10 | `POST /patients` | 是 | 幂等、JSON | 201 |
| 11 | `GET /patients/{patientId}` | 是 | 返回 ETag | 200 |
| 12 | `PATCH /patients/{patientId}` | 是 | 版本、merge-patch | 200 |
| 13 | `DELETE /patients/{patientId}` | 是 | 版本、无正文 | 204 |
| 14 | `GET /patients/{patientId}/lesions` | 是 | 可选 limit、pageToken | 200 |
| 15 | `POST /lesions` | 是 | 幂等、JSON | 201 |
| 16 | `GET /lesions/{lesionId}` | 是 | 返回 ETag | 200 |
| 17 | `PATCH /lesions/{lesionId}` | 是 | 版本、merge-patch | 200 |
| 18 | `DELETE /lesions/{lesionId}` | 是 | 版本、无正文 | 204 |
| 19 | `GET /lesions/{lesionId}/captures` | 是 | 可选 fromAt、toAt、limit、pageToken | 200 |
| 20 | `POST /captures` | 是 | 幂等、JSON | 201 |
| 21 | `GET /captures/{captureId}` | 是 | 返回 ETag | 200 |
| 22 | `PATCH /captures/{captureId}` | 是 | 版本、merge-patch | 200 |
| 23 | `DELETE /captures/{captureId}` | 是 | 版本、无正文 | 204 |
| 24 | `POST /file-uploads` | 是 | 幂等、JSON | 201 |
| 25 | `GET /file-uploads/{uploadId}` | 是 | 无正文 | 200 |
| 26 | `POST /file-uploads/{uploadId}/sign-single` | 是 | 幂等、无正文 | 201 |
| 27 | `POST /file-uploads/{uploadId}/complete` | 是 | 幂等、JSON `{}` | 200 |
| 28 | `DELETE /file-uploads/{uploadId}` | 是 | 无正文 | 204 |
| 29 | `GET /files/{fileId}` | 是 | 无正文 | 200 |
| 30 | `POST /files/{fileId}/download-url` | 是 | 无正文 | 200 |
| 31 | `POST /offline-imports` | 是 | 幂等、JSON | 201 |
| 32 | `POST /sync/snapshots` | 是 | 幂等、无正文 | 201 |
| 33 | `GET /sync/snapshots/{snapshotId}` | 是 | 可选 limit、pageToken | 200 |
| 34 | `GET /sync/changes` | 是 | 必填 cursor，可选 limit | 200 |
| 35 | `GET /jobs/{jobId}` | 是 | 合法未知 UUID | 404，当前没有任务 |

补充对象接口：`PUT {{uploadUrl}}` 预期 204，`GET {{downloadUrl}}` 预期 200；它们使用 URL 内的签名，不使用 Bearer，不计入上表的合同操作数量。

## 8. 异常测试与排查

错误正文通常为：

```json
{
  "code": "REVISION_CONFLICT",
  "message": "记录已变更，请先拉取最新版本",
  "requestId": "服务端请求UUID",
  "details": {
    "resourceId": "对应资源UUID",
    "currentRevision": 2
  }
}
```

| 场景 | 预期状态 / code | 排查或处理 |
|---|---|---|
| 访问健康检查直接连接失败 | 无 HTTP 响应 | 检查 IDEA 是否启动成功、8080 是否被占用 |
| 登录账号或密码错误 | 401 / INVALID_CREDENTIALS | 使用 API 账号密码，不要填数据库密码 |
| 漏 Bearer、访问令牌过期、会话撤销 | 401 / SESSION_EXPIRED | 使用最新刷新令牌刷新，或重新登录 |
| 创建接口漏幂等键 | 400 / VALIDATION_ERROR | Headers 添加该业务请求自己的键 |
| JSON 语法错误或重复字段名 | 400 / VALIDATION_ERROR | 检查逗号、双引号、重复键 |
| 缺字段、未知字段、非法日期、limit=101 | 422 / VALIDATION_ERROR | 按创建/补丁 DTO 修正类型和字段 |
| PATCH/DELETE 漏版本 | 428 / PRECONDITION_REQUIRED | 先 GET 同一资源，复制其 ETag |
| If-Match 填 1 而不是带双引号的 `"1"` | 400 / VALIDATION_ERROR | 原样使用 ETag，不加 W/，不填星号 |
| 使用过期版本修改同一资源 | 412 / REVISION_CONFLICT | 重新 GET，对比并合并修改，再使用新版本 |
| 同方法、同地址、同幂等键，换了正文 | 409 / IDEMPOTENCY_KEY_REUSED | 新业务请求换新键；网络重试保持原正文 |
| 新键创建已存在或已删除的 ID | 409 / ID_CONFLICT | 为新记录生成新 UUID |
| 引用尚未确认的图片创建采集 | 409 / FILE_NOT_READY | 先 PUT，再 complete 到 READY；之后用新键重试创建 |
| 图片属于其他患者 | 422 / INVALID_REFERENCE | 图片上传 patientId 与病灶所属患者一致 |
| 上传时类型错误，或 PATCH 使用 application/json | 415 / UNSUPPORTED_MEDIA_TYPE | 按该请求规定的 Content-Type 发送 |
| complete 时文件 SHA-256 错误 | 422 / CHECKSUM_MISMATCH | 核对原始文件和声明参数，未完成会话补传后用新确认键 |
| 给 READY 上传继续 PUT、续签或中止 | 409 / INVALID_TRANSITION | 新建上传会话，READY 文件不能覆盖 |
| 上传会话过期 | 410 / UPLOAD_EXPIRED | 新键创建新的上传会话 |
| 签名对象 URL 过期或被修改 | 403 / FORBIDDEN | 重新获取签名，不截断或手动拼接 ticket |
| 游标过期、类型不符、跨账号/筛选条件复用 | 410 / SYNC_RESET_REQUIRED | 同步重新建快照；普通列表从首页重新分页 |
| 无权访问或已删除的患者/病灶/采集/文件 | 404 / NOT_FOUND | 核对账号、资源 ID、删除状态，避免用错父资源 |
| 个人工作区填写机构导入目标或越过导入范围 | 403 / FORBIDDEN | 使用已确认个人患者范围 |
| 频繁登录超过限流 | 429 / RATE_LIMITED | 停止密集点击，根据 Retry-After 等待后再试 |
| 登录后调用未启用 P1/P2 路由 | 501 / NOT_IMPLEMENTED | 当前只验收本文列出的 P0；先看 capabilities |
| 服务内部错误 | 500 / SERVER_ERROR | 保存请求 ID，在 IDEA Run 或本机日志中排查，不要连续重复写入 |

可以按以下顺序做最小异常验收：先无令牌读患者，再遗漏幂等键创建，再用正确键成功创建；随后同键同正文重试一次、同键换正文一次；最后对修改请求分别测试漏版本、正确版本和旧版本。跨账号隔离测试需要另一个受控开通的账号，当前没有自助注册入口，不要自行往数据库插入明文密码。

## 9. 测试数据清理与验收记录

只清理本次创建的虚构患者，不要用真实病例做删除测试。

集合“06”可顺序执行：删除测试采集 → 删除病灶 → 删除患者 → 退出。每个 DELETE 使用各自最新 ETag，预期 `204`，没有 JSON 正文。也可只删除测试患者来验证级联删除，此时不要再对已被级联删除的病灶、采集执行删除并期待 204；它们应为 404。

删除是业务软删除，会保留墓碑与同步历史，不等于从数据库物理清除；文件也不会立即物理清理。不要直接删除 MySQL 数据目录或图片目录来“恢复测试环境”。

建议一次调试记录至少填写：

| 检查项 | 预期 | 实际 / 请求 ID |
|---|---|---|
| 健康、登录、me、capabilities | 200，个人角色，扩展关闭 | 待填写 |
| 患者、病灶创建和查询 | 创建201，查询200，父子关联正确 | 待填写 |
| 图片 PUT 与 complete | 204 → 200 READY | 待填写 |
| 文件下载 | 200，文件内容与原始 PNG 一致 | 待填写 |
| 采集创建与修改 | 201 → 200，版本更新 | 待填写 |
| 同键重试 / 换正文 / 旧版本 | 原响应 / 409 / 412 | 待填写 |
| 快照与增量 | 200，游标可连续推进 | 待填写 |
| 删除及墓碑 | 204，详情404，增量含 DELETE | 待填写 |
| 退出后访问 | 204 后受保护接口401 | 待填写 |

本文件是联调步骤，不是已经执行完所有人工用例的验收报告。已有自动化验证及限制见 [实现与验证记录](implementation-status.md)。维护者可运行 `python docs/testing/generate_assets.py` 重新生成集合与固定测试图；脚本会检查集合中的合同路由是否完整覆盖当前 35 个 P0 操作，不会读取本机凭据或连接数据库。
