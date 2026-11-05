# 后端接口清单与前端联调对齐表

交接版本：2026-11-05 / API v1.1.0 / 患者与医生。适用对象：前端设计、开发、接口测试人员。

**本清单反映当前实现，不将规划合同当作可用接口。** 当前有36个P0业务路由，其中任务查询暂时只有404行为；另有签名图片PUT/GET。完整规划合同中的40个P1/P2操作尚未启用。

## 1. 交接内容与接入地址

| 内容 | 地址或文件 | 用途 |
|---|---|---|
| 本清单 | 本文件 | 页面与接口对应、请求与响应、验收对齐 |
| 详细字段字典 | [frontend-field-reference.md](frontend-field-reference.md) | 每个DTO的必填、可空、类型、枚举、限制，包含嵌套字段 |
| 当前P0 OpenAPI快照 | [frontend-p0.openapi.json](frontend-p0.openapi.json) | 可导入Apifox/Postman/Swagger工具；已标明实现范围 |
| Swagger测试页面 | `http://127.0.0.1:8080/swagger-ui/index.html` | 后端启动后，可填写参数并直接执行 |
| 在线文档JSON | `http://127.0.0.1:8080/v3/api-docs` | 在线P0展示版本；见下文与规划合同的差异 |
| API基础地址 | `http://127.0.0.1:8080/api/v1` | 下表业务路径均在此前缀之后 |
| 图片样本 | [sample-20x20.png](testing/sample-20x20.png)、[样本参数](testing/sample-image.json) | 无需自行计算文件哈希的图片上传测试 |
| 操作教程 | [Swagger说明](swagger-ui.md)、[完整调试流程](api-testing.md) | 登录授权、上传、同步和错误排查 |

以上是**后端所在电脑的本机地址**。前端人员在另一台电脑打开127.0.0.1，会访问自己的电脑，不能连接本项目。

当前服务默认仅监听127.0.0.1，没有配置浏览器跨域CORS。联调前先确认：

- 前后端同机、不同端口：前端开发服务器使用同源代理，转发 `/api` 到本机8080；图片传输也必须能访问后端 `/objects`。本机Swagger本身与API同源。
- 跨电脑、真机或局域网：需后端负责人另行确认监听地址、实际可达URL、访问方式及代理/CORS；同时设置 `APP_PUBLIC_URL`，保证签名地址中的主机是客户端可访问的地址。仅修改前端baseURL不能修正后端返回的127.0.0.1图片地址。
- 登录使用API账号。初始账号是 `local-owner`；密码由后端负责人通过约定方式提供，本清单不包含真实密码。不要使用数据库root或derm_app账号登录。

环境对齐栏：

| 待确认项 | 当前状态 / 填写位置 |
|---|---|
| 共享测试环境API基础地址 | 待双方填写；目前仅提供上述本机配置 |
| 前端运行地址、代理或CORS方式 | 待双方填写 |
| 手机/其他电脑访问方式 | 待双方填写，不能直接使用127.0.0.1 |
| 测试账号交付及数据范围 | 由后端负责人提供；患者仅本人；医生全库只读 |
| 后端版本、前端版本、测试日期 | API v1.0.0 / 前端待填 / 日期待填 |

## 2. 页面功能与接口对应

| 前端页面或动作 | 对应接口编号 | 设计与交互要求 |
|---|---|---|
| 登录、登录态恢复、退出 | A01～A04 | 保存双令牌；刷新需串行；退出后清除登录态 |
| 个人资料、改密码 | A04～A06 | 资料修改带ETag；改密码验证当前密码，其他会话失效 |
| 患者列表、搜索、详情、新增、编辑、删除 | P01～P05 | 姓名搜索、游标分页；生日允许空；删除患者影响全部子资源 |
| 病灶列表、详情、新增、编辑、删除 | L01～L05 | 病灶绑定患者；删除病灶影响其采集 |
| 采集记录、图片归档、标注编辑 | C01～C05、F01～F07、O01～O02 | 先上传并确认图片，再创建采集；图片与病灶必须属于同一患者 |
| 初次同步、离线恢复、后续增量 | S01～S04 | 快照完整分页后取得同步游标，随后拉取变更 |
| 功能开关、服务状态 | M01～M02 | 按capabilities隐藏未启用能力；健康检查不代表数据库全链路验收 |
| 任务进度 | J01 | 当前没有异步任务，不设计为P0操作的必经步骤 |
| 医院工作区、授权、审核、短信、云草稿、服务器算法 | 附录中的未启用接口 | 当前不可按成功流程验收；页面应隐藏或标为未开放 |

## 3. 统一约定

### 3.1 请求头与数据格式

下表标记：**B**＝`Authorization: Bearer <accessToken>`；**K**＝`Idempotency-Key`；**V**＝`If-Match`。`—`表示无需该请求头，不代表接口没有参数。

| 项目 | 前端必须遵守的规则 |
|---|---|
| 普通JSON正文 | `Content-Type: application/json`；无正文接口选择Body none |
| PATCH正文 | `Content-Type: application/merge-patch+json` |
| K幂等键 | 1～128字符，建议UUID；一次业务操作一个键；网络重试保持同键同方法同地址同正文 |
| V资源版本 | 先查询同一资源，复制响应头ETag，例如 `"1"`，保留双引号；不能填数字1、星号或别的资源版本 |
| UUID | 患者、病灶、采集的创建ID由客户端生成；上传、文件、快照和导入计划ID由服务端生成 |
| 时间 | Unix毫秒整数，不能使用秒；生日为 `YYYY-MM-DD` |
| 响应结构 | 直接返回资源/集合，没有统一的 `data` 包装；204无正文，不调用JSON解析 |
| PATCH语义 | 未传＝保持；可空字段传null＝清空；嵌套对象递归合并，数组整体替换 |
| 字段白名单 | 创建/补丁只传对应DTO允许的字段；不能把读取响应原样作为创建正文提交 |
| 空值 | `null`表示未知或无值，不能自动转0、空串或false；必填与允许null是两个不同维度 |
| 版本 | 患者/病灶/采集有独立revision；资料版本只在ETag头；采集另有contentRevision |
| 问题定位 | 保存状态码、错误code和响应头X-Request-Id；不在公共问题单粘贴令牌和签名ticket |

服务端会持久化部分确定失败的幂等响应。已经收到失败并修复业务条件后，使用新K重提；复用旧K可能持续返回原失败。续签图片地址、重新建快照也应换新K，避免拿到过期的旧响应。

### 3.2 列表、排序与分页

普通列表返回 `{"items":[],"nextPageToken":null}`。不返回page/total/totalPages；前端使用“加载更多”或游标导航，不能直接按页码跳转。

- `limit` 为1～100，普通列表及快照默认50，增量默认100。
- 后续页保留原筛选条件，携带返回的 `pageToken`；末页 `nextPageToken` 为null。普通分页令牌约24小时有效。
- 患者/病灶按ID升序；采集按 `capturedAt,id` 升序。当前没有倒序、姓名排序、任意sort参数。
- 患者 `q` 为姓名包含匹配，最长128字符。
- 采集时间过滤：`fromAt`包含，`toAt`不包含；两者同时给出时起点必须小于终点。
- 分页不是冻结视图；需要一致初始数据时使用同步快照。

## 4. 可联调业务接口清单（36项）

类型名与 [详细字段字典](frontend-field-reference.md) 对应。路径中的 `{...Id}` 均为必填UUID。表内成功码按当前代码列出，不把规划的202分支当作已实现行为。

### 4.1 认证与账号（6项）

| 编号 | 方法与路径 | 用途 | 请求头 | 输入 | 当前响应 / 前端检查点 |
|---|---|---|---|---|---|
| A01 | `POST /auth/login` | 账号密码登录 | — | Login：account、password必填；requestedRole可选 | 200 Tokens；保存profile、accessToken、refreshToken、expiresAt |
| A02 | `POST /auth/refresh` | 轮换双令牌 | — | Refresh：refreshToken必填 | 200 Tokens；原refreshToken失效，必须同时更新两个令牌 |
| A03 | `POST /auth/logout` | 退出会话 | B | Refresh：当前会话refreshToken | 204；本地清理登录态 |
| A04 | `GET /auth/me` | 当前公开资料 | B | 无正文 | 200 Profile，响应头ETag；不返回密码信息 |
| A05 | `PATCH /users/me` | 修改显示名 | B、V | ProfilePatch：仅displayName可编辑，1～128字符 | 200 Profile，更新资料ETag；不允许改角色、机构 |
| A06 | `POST /users/me/password` | 修改登录密码 | B、K | PasswordChange：currentPassword、newPassword | 204；其他会话失效，当前会话保留 |

登录角色由数据库返回PATIENT或HOSPITAL_STAFF，organizationId为null；requestedRole不能自行授予医生身份。访问令牌约15分钟、刷新令牌约30天有效；令牌应视为不透明字符串，不依赖JWT解码。多个请求遇到过期时只发一次刷新，刷新成功后的请求共用新令牌；重放已使用的旧refreshToken会撤销整个令牌族。

新密码至少12字符、UTF-8不超过72字节。初始账号开通由后端受控完成，当前没有自助注册或短信找回密码入口。

### 4.2 患者（5项）

| 编号 | 方法与路径 | 用途 | 请求头 | 输入 | 当前响应 / 前端检查点 |
|---|---|---|---|---|---|
| P01 | `GET /patients` | 患者列表、姓名搜索 | B | Query：q?、scope=all/mine?、limit?、pageToken? | 200 Page&lt;Patient&gt;；空数组为无数据 |
| P02 | `POST /patients` | 新建患者或导入 | B、K | PatientCreate：id、name必填；birthDate?、phoneNumber?、gender?、birthYearMonth?、attendingDoctorId?、clientCreatedAt?、importId? | 201 Patient，ETag；birthDate省略时返回null |
| P03 | `GET /patients/{patientId}` | 患者详情 | B | Path：patientId | 200 Patient，ETag |
| P04 | `PATCH /patients/{patientId}` | 编辑患者 | B、V | PatientPatch：name?、birthDate?、phoneNumber?、gender?、birthYearMonth?、attendingDoctorId? | 200 Patient，ETag；清空生日传null |
| P05 | `DELETE /patients/{patientId}` | 删除患者及子资源 | B、V | Path：patientId；无正文 | 204；原患者、病灶、采集详情变404；同步有墓碑 |

姓名1～128字符。创建请求不能传revision/createdAt/updatedAt；clientCreatedAt是可选的客户端时间输入，不替代服务端时间。软删除后的UUID不能重新创建。

### 4.3 病灶（5项）

| 编号 | 方法与路径 | 用途 | 请求头 | 输入 | 当前响应 / 前端检查点 |
|---|---|---|---|---|---|
| L01 | `GET /patients/{patientId}/lesions` | 患者的病灶列表 | B | Path：patientId；Query：limit?、pageToken? | 200 Page&lt;Lesion&gt; |
| L02 | `POST /lesions` | 新建病灶 | B、K | LesionCreate：id、patientId、bodySite必填；note?、clientCreatedAt?、importId? | 201 Lesion，ETag |
| L03 | `GET /lesions/{lesionId}` | 病灶详情 | B | Path：lesionId | 200 Lesion，ETag |
| L04 | `PATCH /lesions/{lesionId}` | 修改部位、备注 | B、V | LesionPatch：bodySite?、note? | 200 Lesion，ETag |
| L05 | `DELETE /lesions/{lesionId}` | 删除病灶及其采集 | B、V | Path：lesionId；无正文 | 204；不删除其患者 |

bodySite为1～128字符；note最多2000字符，可空串，不能用null清空，省略时默认为空串。

### 4.4 采集（5项）

| 编号 | 方法与路径 | 用途 | 请求头 | 输入 | 当前响应 / 前端检查点 |
|---|---|---|---|---|---|
| C01 | `GET /lesions/{lesionId}/captures` | 历史采集列表 | B | Path：lesionId；Query：fromAt?、toAt?、limit?、pageToken? | 200 Page&lt;Capture&gt;；时间及ID升序 |
| C02 | `POST /captures` | 保存正式采集 | B、K | CaptureCreate，必填字段见下文 | 201 Capture，ETag；revision/contentRevision初始1 |
| C03 | `GET /captures/{captureId}` | 采集详情 | B | Path：captureId | 200 Capture，ETag |
| C04 | `PATCH /captures/{captureId}` | 修改图片引用、标注或分析等内容 | B、V | CapturePatch，不能改id/lesionId/revision/reviewStatus | 200 Capture，ETag；内容变化更新contentRevision |
| C05 | `DELETE /captures/{captureId}` | 删除正式采集 | B、V | Path：captureId；无正文 | 204；不立即物理删除图片文件 |

创建必填：`id`、`lesionId`、`imageFileId`、`capturedAt`、`deviceModel`、`source`、`assetKind`、`referenceOnly`。`referenceOnly`允许null但字段必须出现。

可选：`qualityNote`、`calibration`、`analysis`、`annotation`、`metadata`、`importId`。仅照片采集可以将calibration/analysis/annotation设为null；qualityNote默认null，metadata默认空对象。

| 对齐项 | 当前行为 |
|---|---|
| source | CAMERA / GALLERY / UNKNOWN |
| assetKind | CAPTURE_IMAGE / ANALYSIS_COPY / UNKNOWN |
| reviewStatus | 当前固定NOT_SUBMITTED，不存在P0审核成功流程 |
| 有效面积 | analysis.isValid=true时，需正面积、合法尺度和非退化不自交轮廓，referenceOnly不能为true |
| 坐标 | 使用归档图片像素坐标；imageWidth/imageHeight与实际文件一致，显示缩放后需换算回原图 |
| 改尺度/轮廓 | 未同时传完整重算analysis（isValid、areaMm2、algorithmVersion）时，旧analysis清空为null |
| 空/无效分析 | 不把null或isValid=false结果补0绘图；referenceOnly=null也不表示采集QC通过 |
| 图库来源 | 不能把metadata.liveQc.liveGateExecuted填为true |
| 算法来源 | 后端保存及校验客户端结果，目前不执行服务器图像算法 |

### 4.5 文件与上传会话（7项）

| 编号 | 方法与路径 | 用途 | 请求头 | 输入 | 当前响应 / 前端检查点 |
|---|---|---|---|---|---|
| F01 | `POST /file-uploads` | 创建单文件上传会话 | B、K | UploadCreate：patientId、sizeBytes、sha256、mimeType、width、height、strategy全部必填 | 201 Upload；保存uploadId、fileId、uploadUrl、requiredHeaders |
| F02 | `GET /file-uploads/{uploadId}` | 查询会话及可用上传地址 | B | Path：uploadId | 200 Upload；未完成会话可返回新签名 |
| F03 | `POST /file-uploads/{uploadId}/sign-single` | 给同一会话续签 | B、K | Path：uploadId；无正文 | 201 UploadUrl；使用url，不是uploadUrl字段 |
| F04 | `POST /file-uploads/{uploadId}/complete` | 确认上传并校验真实图片 | B、K | UploadComplete：发送JSON `{}` | 200 File；state=READY才可被采集引用 |
| F05 | `DELETE /file-uploads/{uploadId}` | 中止未完成会话 | B | Path：uploadId；无正文 | 204；READY会话不能中止 |
| F06 | `GET /files/{fileId}` | 查询文件元数据 | B | Path：fileId | 200 File；不返回服务器文件路径 |
| F07 | `POST /files/{fileId}/download-url` | 申请短期下载URL | B | Path：fileId；无正文、无K | 200 DownloadUrl，包含url/expiresAt/sha256/sizeBytes/mimeType |

上传前通过M01查询允许的格式、大小与策略。

当前策略仅SINGLE，MIME仅image/jpeg、image/png；最大104857600字节（100MiB），宽高各不超过20000，总像素不超过40000000。sha256是原始文件字节的64位小写十六进制摘要。声明参数必须与实际上传字节一致。

上传会话当前会出现PENDING、UPLOADING、READY、ABORTED；文件元数据在complete成功前仍为PENDING，之后READY。会话expiresAt约24小时；上传urlExpiresAt最多5分钟；下载expiresAt约2分钟。不可将这些过期时间混为一谈。

### 4.6 导入与同步（4项）

| 编号 | 方法与路径 | 用途 | 请求头 | 输入 | 当前响应 / 前端检查点 |
|---|---|---|---|---|---|
| S01 | `POST /offline-imports` | 确认离线旧数据导入范围 | B、K | ImportCreate：clientDatasetId、confirmedPatientIds必填；targetOrganizationId当前省略或null | 201 ImportPlan；只建计划，不自动建患者 |
| S02 | `POST /sync/snapshots` | 建立一致初始快照 | B、K | 无正文 | 201 Snapshot：id、expiresAt；快照约24小时有效 |
| S03 | `GET /sync/snapshots/{snapshotId}` | 分页读取冻结快照 | B | Path：snapshotId；Query：limit?、pageToken? | 200 SnapshotPage；只有末页nextCursor非null |
| S04 | `GET /sync/changes` | 增量拉取 | B | Query：cursor必填，limit? | 200 Changes：items、nextCursor、hasMore |

confirmedPatientIds为1～100个UUID。后续创建患者/病灶/采集可带importId，服务端检查所属患者是否在确认范围，计划约30天有效。

快照项和增量项都是Change：entityType、entityId、operation、revision、payload。当前实体类型为PATIENT/LESION/CAPTURE，操作为UPSERT/DELETE；UPSERT携带完整资源，DELETE的payload为null。前端先应用全部快照页，再使用末页nextCursor拉增量；每页均替换为返回的nextCursor，包括空页；hasMore=true时继续拉取。增量游标约90天有效。

普通分页token、快照分页token与增量cursor不能互换，也不能跨账号使用。410 SYNC_RESET_REQUIRED时重新建快照。当前没有可用的POST /sync/push，离线写回需调用各资源的创建/补丁/删除接口。

### 4.7 能力、健康与任务（3项）

| 编号 | 方法与路径 | 用途 | 请求头 | 输入 | 当前响应 / 前端检查点 |
|---|---|---|---|---|---|
| M01 | `GET /system/capabilities` | 获取能力开关 | B | 无正文 | 200 Capabilities；见下方固定能力 |
| M02 | `GET /system/health` | 最小健康状态 | — | 无正文 | 200 Health：status=UP、serverTime |
| J01 | `GET /jobs/{jobId}` | 任务查询预留 | B | Path：合法jobId；无正文 | 当前无任务，返回404 NOT_FOUND，不返回成功任务对象 |

当前能力：`multipartEnabled=false`、`cloudDraftsEnabled=false`、`serverAlgorithmsEnabled=false`、`maxSyncBatch=0`、`maxPageSize=100`、`acceptedMimeTypes=["image/jpeg","image/png"]`、`maxUploadBytes=104857600`。

### 4.8 医生目录（新增1项）

| 编号 | 方法与路径 | 用途 | 请求头 | 输入 | 当前响应 / 前端检查点 |
|---|---|---|---|---|---|
| D01 | `GET /doctors` | 主治医师下拉选择 | B | q?、limit?、pageToken? | 200 DoctorPage；仅有效医生id/displayName |

患者仅访问自己账号；医生可读全库患者及子记录和图片，但不能跨账号修改。`GET /patients?scope=mine`仅筛选当前医生的主治患者；默认all为全部。主治关联不改变所有权。年龄只读、按出生信息计算，所有新增字段及年月兼容规则见 [患者与医生说明](patient-doctor-guide.md)。个人同步继续只包含本人拥有的数据。

## 5. 图片二进制传输（额外2项）

这两项**不在 `/api/v1` 前缀下**，使用服务端返回的完整签名URL；不附Bearer。

| 编号 | 方法与地址 | 输入 | 响应 | 前端必须检查 |
|---|---|---|---|---|
| O01 | `PUT <uploadUrl>` | 按requiredHeaders设置Content-Type，正文是原始File/Blob字节 | 204无正文 | 不能传JSON、文件路径、Base64或form-data；之后还须F04 |
| O02 | `GET <downloadUrl>` | 直接使用F07返回的url | 200图片二进制 | 处理签名过期，重新申请；fileId才是持久引用 |

在Swagger中，“图片传输”的 `/objects/{objectId}` 只是这两项签名请求的填参形式：上传objectId是uploadId，下载objectId是fileId；ticket取各自URL的ticket查询参数。不要拿同一个ticket在上传和下载间混用。

最短闭环：**A01登录 → P02患者 → L02病灶 → F01会话 → O01字节PUT → F04确认READY → C02保存采集 → F07签名 → O02看图**。

## 6. 请求与响应对齐样例

以下占位符需替换；UUID、令牌不能直接使用中文占位值请求服务器。

登录：

```json
{"account":"local-owner","password":"<API登录密码>"}
```

登录响应结构（permissions省略了具体内容，非实际完整响应）：

```json
{
  "profile":{"id":"<userId>","account":"local-owner","displayName":"local-owner","role":"PATIENT","organizationId":null,"permissions":[]},
  "accessToken":"<不透明访问令牌>",
  "refreshToken":"<不透明刷新令牌>",
  "expiresAt":1793854600000
}
```

使用随附原始PNG初始化上传，patientId改成真实患者UUID：

```json
{
  "patientId":"<patientId>",
  "sizeBytes":83,
  "sha256":"0aedc9bece813993a4241852d4826e275f95d7cd3877a25e052922f2b84e3c0c",
  "mimeType":"image/png",
  "width":20,
  "height":20,
  "strategy":"SINGLE"
}
```

仅照片采集：

```json
{
  "id":"<客户端新UUID>",
  "lesionId":"<lesionId>",
  "imageFileId":"<READY的fileId>",
  "capturedAt":1793853700000,
  "deviceModel":"Frontend-Debug",
  "source":"GALLERY",
  "assetKind":"CAPTURE_IMAGE",
  "referenceOnly":false,
  "calibration":null,
  "analysis":null,
  "annotation":null,
  "metadata":{}
}
```

统一错误响应结构：

```json
{
  "code":"REVISION_CONFLICT",
  "message":"记录已变更，请先拉取最新版本",
  "requestId":"<请求UUID>",
  "details":{"resourceId":"<资源UUID>","currentRevision":2}
}
```

## 7. 错误码与前端处理对齐

| HTTP / code | 触发示例 | 前端处理 |
|---|---|---|
| 400 / VALIDATION_ERROR | 漏K、JSON重复字段/语法错误、If-Match格式错误 | 修正请求；不要盲目自动重试 |
| 401 / INVALID_CREDENTIALS | 登录密码或改密时原密码错误 | 表单提示，区别于会话过期 |
| 401 / SESSION_EXPIRED | 缺访问令牌、过期、撤销 | 串行刷新或回到登录；不要循环刷新 |
| 403 / FORBIDDEN | 签名无效、导入范围不符、个人工作区传机构目标 | 说明操作不可用；签名问题重新申请地址 |
| 404 / NOT_FOUND | 不存在、不可访问、已删除；或当前任务不存在 | 缺省页/刷新列表；不要区分并暴露他人资源是否存在 |
| 409 / IDEMPOTENCY_KEY_REUSED | 同方法同路径同K换正文 | 新业务换K；原请求重试保持原正文 |
| 409 / ID_CONFLICT | 新K创建已占用UUID | 核对记录归属，为新记录生成新UUID |
| 409 / FILE_NOT_READY | 字节未上传或文件未确认 | 先完成上传确认，再用新K重提采集 |
| 409 / INVALID_TRANSITION | READY后再PUT、续签、中止 | 新建上传会话，不能覆盖已确认文件 |
| 410 / SYNC_RESET_REQUIRED | 游标/快照过期或绑定不符 | 同步重建快照；普通列表从首页重拉 |
| 410 / UPLOAD_EXPIRED、IMPORT_EXPIRED | 上传或导入计划超时 | 新K创建新会话/计划 |
| 412 / REVISION_CONFLICT | 使用旧ETag修改 | 拉取新资源，对比差异再提交，不静默覆盖 |
| 413 / FILE_TOO_LARGE | 上传字节超过声明/上限，JSON超过1MiB | 限制文件/正文大小，修正声明 |
| 415 / UNSUPPORTED_MEDIA_TYPE | PATCH类型错、WebP或图片真实类型不符 | 按接口规定的类型发送 |
| 422 / VALIDATION_ERROR | 缺DTO字段、未知字段、日期/枚举/尺寸/几何不合法 | 对应字段或操作提示，保留用户输入 |
| 422 / CHECKSUM_MISMATCH | 文件字节与sha256不符 | 重算/核对原图、重新上传；确认请求用新K |
| 422 / INVALID_REFERENCE | 采集引用其他患者的文件 | 修正patient/lesion/file关联 |
| 428 / PRECONDITION_REQUIRED | 修改/删除缺If-Match | 先查资源取得ETag |
| 429 / RATE_LIMITED | 频繁登录 | 按Retry-After等待，禁用密集重复提交 |
| 501 / NOT_IMPLEMENTED | 调用尚未启用的合同操作 | 隐藏入口或显示未开放，不将其作为P0成功验收 |
| 500 / SERVER_ERROR | 未处理内部错误 | 保留输入，记录请求ID给后端排查 |

## 8. 前后端联调勾选表

每项实际测试后填写“通过/失败/阻塞”、测试人、版本、日期及问题单。本表初始未执行，不能当成已验收报告。

| 编号 | 联调项 | 验收要点 | 结果 / 请求ID |
|---|---|---|---|
| T01 | 地址与访问方式 | 双方使用同一后端；代理或CORS已明确；签名URL可访问 | 待填写 |
| T02 | 登录及资料 | A01/A04成功；显示名、数据库实际角色、organizationId=null对齐 | 待填写 |
| T03 | 未登录与错误密码 | 保护接口401；错误密码INVALID_CREDENTIALS | 待填写 |
| T04 | 自动刷新与退出 | 同时更新双令牌；避免并发刷新；退出后保护接口401 | 待填写 |
| T05 | 患者创建、查询、搜索 | 返回201/200；ID与列表一致；生日可空 | 待填写 |
| T06 | 病灶父子关联 | 新病灶出现在指定患者下；父患者错误时正确处理404 | 待填写 |
| T07 | 分页与空列表 | limit边界、nextPageToken、末页null；不依赖total/page | 待填写 |
| T08 | 修改与版本保存 | PATIENT/LESION/CAPTURE ETag分别保存，新版本用于后续写入 | 待填写 |
| T09 | 版本冲突 | 漏V返回428，旧V返回412；页面不静默覆盖 | 待填写 |
| T10 | 幂等网络重试 | 相同K同正文只创建一次；不同正文同K返回409 | 待填写 |
| T11 | 图片真实字节闭环 | F01→O01→F04成功，File READY后可建采集 | 待填写 |
| T12 | 图片跨患者与未READY | 分别422 INVALID_REFERENCE、409 FILE_NOT_READY | 待填写 |
| T13 | 哈希错误、类型错误、超量 | 按422/415/413处理，不误显示上传成功 | 待填写 |
| T14 | 上传续签与下载过期 | 新K续签；上传ID不变；下载重新申请url | 待填写 |
| T15 | 仅照片与空分析 | 空值不补0，不误绘有效趋势，不伪造QC通过 | 待填写 |
| T16 | 标注与重算 | 图像坐标一致；改尺度/轮廓不重算时旧analysis清空 | 待填写 |
| T17 | 快照与增量 | 读完全部快照页取得cursor；hasMore循环、空页更新cursor | 待填写 |
| T18 | 删除与墓碑 | 删除患者级联子资源，详情404，增量含payload=null的DELETE | 待填写 |
| T19 | 异常提示与问题单 | 能区分401/404/409/412/422，保留X-Request-Id | 待填写 |
| T20 | 功能边界 | 不显示短信、机构成员、审核、云草稿、批量push、服务器算法成功入口 | 待填写 |
| T21 | 可选旧数据导入 | confirmedPatientIds范围、importId、计划过期处理对齐 | 待填写 |
| T22 | 可选修改密码 | 在指定测试账号执行，更新本地登录密码，其他会话失效 | 待填写 |

## 9. 与原规划合同的差异

| 项目 | 规划合同允许 | 当前实现 / 前端按此验收 |
|---|---|---|
| 操作范围 | 76个业务HTTP操作 | 36个P0路由，40个P1/P2尚未启用 |
| 删除患者 | 204或202异步Job | 当前同步204，不会生成任务 |
| 完成上传 | 200 File或202 Upload | 当前同步校验，成功200 File READY |
| 任务查询 | 存在任务时200 Job | 当前无任务，只有未知任务404 |
| 登录角色/工作区 | 合同包含医院角色 | PATIENT本人数据，HOSPITAL_STAFF全库只读 |
| 上传策略/格式 | 合同含MULTIPART、WebP | 当前仅SINGLE、JPEG/PNG |
| 同步实体/操作 | 合同预留更多实体及ACCESS_REVOKED | 当前发出PATIENT/LESION/CAPTURE、UPSERT/DELETE |
| 服务器地址 | 原合同使用example.invalid占位 | 在线Swagger使用当前服务；交接快照给本机地址，跨机需另约定 |

随附的 `frontend-p0.openapi.json` 是当前实现对齐快照，已去除尚未启用操作，并修正上表的202/任务/枚举差异；它不替代、不改写原规划合同。在线 `/v3/api-docs` 主要是P0路由展示，部分Schema仍保留规划枚举和响应分支，前端应结合capabilities、本清单和交接快照确认支持范围。

## 附录：未启用的40个合同操作

以下清单由原合同提取。受保护路由先鉴权；鉴权通过后未启用功能返回501 NOT_IMPLEMENTED，公开的未启用路由也返回501。不要将其作为当前已完成能力。

| 优先级 | 方法与路径 | 规划用途 | 当前状态 |
|---|---|---|---|
| P1 | `POST /auth/challenges` | 申请注册或恢复验证码 | 未启用，不能按成功流程验收 |
| P1 | `POST /auth/register` | 注册患者账号 | 未启用，不能按成功流程验收 |
| P1 | `POST /auth/password-resets` | 验证并重置密码 | 未启用，不能按成功流程验收 |
| P1 | `GET /users/me/sessions` | 查询本人会话 | 未启用，不能按成功流程验收 |
| P1 | `DELETE /users/me/sessions/{sessionId}` | 撤销指定会话 | 未启用，不能按成功流程验收 |
| P1 | `POST /users/me/active-organization` | 切换活动机构 | 未启用，不能按成功流程验收 |
| P1 | `POST /users/me/closure` | 申请关闭账号 | 未启用，不能按成功流程验收 |
| P1 | `POST /organizations` | 后台受控开通机构 | 未启用，不能按成功流程验收 |
| P1 | `GET /organizations` | 查询可管理或所属机构 | 未启用，不能按成功流程验收 |
| P1 | `GET /organizations/{organizationId}` | 机构详情 | 未启用，不能按成功流程验收 |
| P1 | `PATCH /organizations/{organizationId}` | 修改或停用机构 | 未启用，不能按成功流程验收 |
| P1 | `GET /organizations/{organizationId}/members` | 机构成员列表 | 未启用，不能按成功流程验收 |
| P1 | `POST /organizations/{organizationId}/members` | 添加或邀请工作人员 | 未启用，不能按成功流程验收 |
| P1 | `PATCH /organizations/{organizationId}/members/{memberId}` | 修改成员身份或停用 | 未启用，不能按成功流程验收 |
| P1 | `GET /patients/{patientId}/accounts` | 查询核验后的患者账号关联 | 未启用，不能按成功流程验收 |
| P1 | `POST /patients/{patientId}/accounts` | 核验后关联本人或监护账号 | 未启用，不能按成功流程验收 |
| P1 | `POST /patients/{patientId}/account-link-verifications` | 受控申请病例账号绑定核验 | 未启用，不能按成功流程验收 |
| P1 | `GET /account-link-verifications/{verificationId}` | 查询绑定核验状态 | 未启用，不能按成功流程验收 |
| P1 | `POST /account-link-verifications/{verificationId}/decision` | 受控确认或拒绝绑定核验 | 未启用，不能按成功流程验收 |
| P1 | `DELETE /patients/{patientId}/accounts/{linkId}` | 撤销账号关联 | 未启用，不能按成功流程验收 |
| P1 | `GET /patients/{patientId}/grants` | 查询病例授权 | 未启用，不能按成功流程验收 |
| P1 | `POST /patients/{patientId}/grants` | 授权机构或医生访问 | 未启用，不能按成功流程验收 |
| P1 | `DELETE /patients/{patientId}/grants/{grantId}` | 撤销病例授权 | 未启用，不能按成功流程验收 |
| P1 | `GET /lesions/{lesionId}/trends` | 查询有效面积及色差趋势 | 未启用，不能按成功流程验收 |
| P1 | `POST /file-uploads/{uploadId}/sign-parts` | 申请指定分片签名URL | 未启用，不能按成功流程验收 |
| P1 | `DELETE /files/{fileId}` | 删除未被引用文件 | 未启用，不能按成功流程验收 |
| P1 | `GET /lesions/{lesionId}/drafts` | 跨设备草稿列表 | 未启用，不能按成功流程验收 |
| P1 | `POST /drafts` | 创建云端草稿 | 未启用，不能按成功流程验收 |
| P1 | `GET /drafts/{draftId}` | 获取草稿编辑快照 | 未启用，不能按成功流程验收 |
| P1 | `PATCH /drafts/{draftId}` | 保存结构化编辑快照 | 未启用，不能按成功流程验收 |
| P1 | `DELETE /drafts/{draftId}` | 删除草稿 | 未启用，不能按成功流程验收 |
| P1 | `POST /captures/{captureId}/review-submissions` | 提交指定版本审核 | 未启用，不能按成功流程验收 |
| P1 | `GET /captures/{captureId}/reviews` | 审核历史 | 未启用，不能按成功流程验收 |
| P1 | `GET /reviews` | 机构待审列表 | 未启用，不能按成功流程验收 |
| P1 | `POST /reviews/{reviewId}/decision` | 批准或拒绝固定版本 | 未启用，不能按成功流程验收 |
| P1 | `POST /sync/push` | 批量幂等推送元数据变更 | 未启用，不能按成功流程验收 |
| P1 | `POST /exports` | 申请授权病例数据导出 | 未启用，不能按成功流程验收 |
| P2 | `POST /captures/{captureId}/reanalysis-jobs` | 申请服务器重新分析 | 未启用，不能按成功流程验收 |
| P2 | `POST /alignment-jobs` | 申请服务器两图对齐 | 未启用，不能按成功流程验收 |
| P1 | `GET /audit-events` | 查询受控审计事件 | 未启用，不能按成功流程验收 |
