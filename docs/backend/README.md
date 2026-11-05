# 后端完整接口与数据设计清单
当前v1.1.0扩展：启用PATIENT/HOSPITAL_STAFF医生全库只读；新增GET /doctors、患者phoneNumber/gender/birthYearMonth/attendingDoctorId、只读age与attendingDoctor摘要。详细权限和兼容规则见 [专项说明](../patient-doctor-guide.md)。下文机构成员与授权仍为扩展规划，不能与已启用医生只读混淆。

版本：建议合同 v1.0，2026-11-03；对应客户端 V1.6.0。本文是**供后续后端开发的设计合同**，不是服务器已经上线或客户端已经完成联网的声明。接口路径及新增字段可在实施前统一冻结。

配套文件：

- [逐项接口清单](api-catalog.md)：按模块、优先级列出方法、地址、输入、输出与授权范围。
- [OpenAPI 3.0.3](backend-v1.openapi.json)：与清单同源生成，可用于接口工具导入、服务端桩及客户端 DTO 对接。
- [现有登录约定](../auth-api-contract.md)：保持 login、refresh、logout、me 四个入口兼容。
- [合同生成器](generate_contract.py)：接口目录和 OpenAPI 的共同维护源，运行 `python docs/backend/generate_contract.py` 重新生成；不会生成或启动后端服务。总说明手工维护。

## 1. 当前实现与实施边界

| 功能 | 当前 App 实际状态 | 后端/客户端后续工作 |
|---|---|---|
| 登录/退出/公开用户摘要 | AuthRepository + 离线实现；AuthenticationApi 为 Kotlin 抽象 | 实现 HTTP 适配、服务端验证、Keystore 令牌存储 |
| 患者、病灶、采集、标定、结果、标注 | Room v4 本地保存 | 新增远端 DTO 和服务端归属/版本，扩展 Repository |
| 图片 | 私有目录 JPEG 或相册导入 PNG 分析副本 | 对象存储上传、校验、授权下载、客户端文件缓存 |
| 待上传队列 | 本地状态；NoOpOfflineUploadDataSource 明确返回未配置 | 实现任务调度、重试、文件上传和元数据确认 |
| 远端拉取/增量同步 | 未实现 | 同步游标、快照、墓碑、权限撤回及本地事务应用 |
| 医院权限、审核、账号关联 | 未实现；离线角色仅演示 | 机构成员、患者绑定、病例授权与审核状态机 |
| 云端草稿/云端算法 | 未实现 | 可选扩展；不阻塞基础照片归档 |

患者与医院使用同一套资源；服务端认证和资源授权决定可见范围，不能按客户端选择的角色直接放权。当前本地数据库没有账号分区，因此“换一个登录身份”不等于已有病例变成该用户的数据。

## 2. 优先级与最小开发顺序

- **P0 必需**：认证、用户摘要、受控账号开通、患者/病灶/采集 CRUD、图片单文件上传与授权下载、幂等、版本、初始快照与增量拉取。没有短信服务时由后台受控开通账号，不能让公开注册选择医院身份。
- **P1 协作**：自助注册/找回、机构与病例授权、审核、批量推送、分片上传、跨设备草稿、账号导出/注销。
- **P2 可选**：服务器重新分析、配准任务。当前手机算法已经能本地执行；云端算法并非数据上传前提。

建议顺序：数据库及资源授权 → 登录/用户 → 患者/病灶 → 图片上传 → 采集提交 → 同步拉取与冲突 → 医院协作/审核。第一版可以保留单应用后端加对象存储，不必先拆成多个服务。

## 3. 全局协议

基础路径 `/api/v1`；HTTPS；JSON UTF-8；字段 camelCase；枚举大写；绝对时间 Unix 毫秒；生日为 `YYYY-MM-DD`；面积 mm²；长度 mm；像素坐标使用已归档图像自身坐标。

普通业务接口使用 `Authorization: Bearer <accessToken>`；公开注册/登录/恢复流程按清单例外。登录响应保持现有 `profile/accessToken/refreshToken/expiresAt` 结构，不增加外层 data 包装。单资源直接返回对象；集合返回 `{items,nextPageToken}`；异步任务返回 202 Job；删除通常为 204。

`GET /auth/me` 通过响应 ETag 提供用户资料版本，`PATCH /users/me` 用该值作为 If-Match。AccountProfile JSON 保持现有客户端结构，不要求额外加入 revision 字段。

- 请求可带 `X-Request-Id`；服务端返回 `X-Request-Id`。仅用于排查，不能承载密码/患者姓名。
- 资源响应带 `ETag: "<revision>"`；PATCH/DELETE 以及审核/提交等指定写入必须携带 `If-Match`。缺少为 428，版本过期为 412；客户端保留自己的未同步内容。
- 创建/提交/完成上传/任务接口必须带 `Idempotency-Key`，同作用域、方法、路径和相同请求重试返回原结果；相同 key 不同内容返回 409 IDEMPOTENCY_KEY_REUSED。幂等记录与业务写入同事务；持久期限至少覆盖客户端允许的离线重试窗口。
- 列表使用 `pageToken` 和 `limit`（默认 50，范围 1–100），稳定排序包含 ID；页游标绑定查询条件、调用人和授权范围。普通分页不能替代同步游标。
- 创建患者/病灶/采集接受客户端生成的 UUID。服务端保留这些 ID；ID 冲突且内容不同返回 409，不覆盖、不以同 ID 返回其他用户数据。业务归属来自授权上下文，不能由请求伪造 `ownerUserId/organizationId`。
- 局部更新采用 JSON Merge Patch（`application/merge-patch+json`）：省略表示不变，显式 null 只可清除可空字段；数组整体替换。只允许 DTO 列出的字段，禁止大范围实体字段绑定。嵌套 Patch DTO 的字段递归可选，在合并后的完整资源上执行必填、几何、尺度与业务约束；例如 analysis.colorDifference 可单独更新。数组仍需提交完整数组，不能用缺少 x/y 的点对象作部分数组修改。
- 服务端创建/更新时间以服务端时间为准，拍摄/导入事件时间单独保留；不按客户端 updatedAt 解决冲突。

### 错误格式与处理

```json
{"code":"REVISION_CONFLICT","message":"记录已发生变更，请先拉取最新版本","requestId":"trace-id","details":{"resourceId":"uuid","currentRevision":5}}
```

| HTTP | code 示例 | 客户端动作 |
|---|---|---|
| 400/422 | VALIDATION_ERROR / INVALID_REFERENCE / CHECKSUM_MISMATCH | 修正数据；不可无限自动重试 |
| 401 | INVALID_CREDENTIALS / SESSION_EXPIRED | 登录失败显示原因；会话过期只串行刷新一次 |
| 403/404 | FORBIDDEN / NOT_FOUND | 禁止跨范围查询；按统一策略隐藏不存在/无权资源 |
| 409 | ID_CONFLICT / FILE_NOT_READY / INVALID_TRANSITION / IDEMPOTENCY_KEY_REUSED | 检查依赖或重新协商，不盲目覆盖 |
| 410 | SYNC_RESET_REQUIRED / UPLOAD_EXPIRED | 重建同步快照或重新初始化上传 |
| 412/428 | REVISION_CONFLICT / PRECONDITION_REQUIRED | 拉取冲突对象或补 If-Match |
| 413/415 | FILE_TOO_LARGE / UNSUPPORTED_MEDIA_TYPE | 限制文件尺寸/类型，不重试相同内容 |
| 429 | RATE_LIMITED | 遵守 Retry-After |
| 5xx/连接中断 | SERVER_ERROR / 本机 NETWORK_UNAVAILABLE | 带原幂等键指数退避，设最大重试次数 |

不得向 App 暴露 SQL、密码哈希、令牌或服务器堆栈。对未授权的文件/病例也要执行资源级检查，知道 UUID 不等于获得访问权。

## 4. 账号与机构存储

账号不是患者档案：一个账号可管理被授权的多个患者档案，患者档案也可由医院建立而暂未绑定个人账号。禁止把 patientId 当 userId，或按姓名/手机号相同自动合并。

**初始权限必须闭环**：个人新建 Patient 时，同事务写入 createdByUserId 和个人 ownerScopeId，创建者获得该工作区病例的初始管理权限，但不生成“已核验本人”patient_accounts 关系。医院成员新建时，必须已有机构 patient:create 权限，同事务建立机构 ownerScopeId 及授给创建者的初始 patient_grant；其他成员不会仅凭在同机构就自动可见。旧数据导入按确认的个人/机构范围执行相同初始授权规则。患者、初始授权及同步事件一起提交；创建成功后同一身份必须能够读取患者、创建病灶和申请上传。对已有他人病例仍需单独绑定/授权，不能通过新建同名患者获得访问权。

| 表 | 关键字段 | 约束/用途 |
|---|---|---|
| users | id, normalizedAccount, displayName, status, createdAt, updatedAt, revision | 登录名规范化后唯一；状态 ACTIVE/DISABLED/PENDING/CLOSED；软关闭与病例保留分开 |
| user_credentials | userId, passwordHash, hashAlgorithm, hashParameters, passwordChangedAt | 哈希库生成带盐的单向慢哈希，不存明文/可逆密码；不出现在业务 API |
| auth_sessions | id, userId, refreshTokenHash, tokenFamilyId, expiresAt, revokedAt, deviceId | 保存刷新令牌摘要；刷新轮换与撤销原子处理，重放可撤销整个令牌族 |
| verification_challenges | id, purpose, destinationHash, codeHash, expiresAt, attempts, consumedAt | 注册/恢复限流和一次性消费，响应不枚举账号是否存在 |
| organizations | id, name, status, revision | 医院/机构；机构不存在时不允许客户端自造 |
| organization_memberships | organizationId, userId, membershipRole, status, revision | 管理员受控邀请/停用；角色/权限由服务端计算 |
| patient_accounts | patientId, userId, relation, verifiedAt, revokedAt | 服务端核验/邀请确认后建立本人/监护/受托关联 |
| patient_grants | id, patientId, organizationId, granteeUserId, permissions, status, revision | 病例级读/采集/审核授权，可撤销；机构成员资格与病例授权均检查 |

公开 AccountProfile 只含 `id/account/displayName/role/organizationId/permissions`，与当前 Kotlin 契约兼容。一个用户属于多机构时，当前字段只表示活动机构；切换活动机构后重新返回 profile，撤销/重发相关授权上下文，重新初始化该机构同步范围。组织权限不能只用一个 role 字符串表达。

AccountRole 线上为 PATIENT/HOSPITAL_STAFF；GUEST 仅为本机离线访客。管理员是后台成员权限，不通过公开注册直接授予新的全局管理员角色。

## 5. 业务与文件存储

| 表 | 关键字段 | 约束 |
|---|---|---|
| patients | id, ownerScopeId, createdByUserId, name, birthDate, createdAt, updatedAt, revision, deletedAt | 所有查询附授权范围；PII 不放对象存储 key/日志 |
| lesions | id, patientId, bodySite, note, createdAt, updatedAt, revision, deletedAt | 关联患者有效且可访问 |
| captures | id, lesionId, imageFileId, capturedAt, deviceModel, source, assetKind, referenceOnly, qualityNote, calibration, analysis, annotation, metadata, revision, reviewStatus, createdAt, updatedAt, deletedAt | 文件必须 READY 且属于同授权范围；保留每次修改快照 |
| files | id, ownerScopeId, objectKey, sha256, sizeBytes, mimeType, width, height, state, createdAt | objectKey 内部使用；不返回公开永久 URL；校验实际字节/解码尺寸 |
| file_uploads / upload_parts | id, fileId, objectUploadId, strategy, expiresAt, partNumber, checksum, state | 未完成文件不得被正式采集引用；过期定期清理 |
| capture_revisions | captureId, revision, contentRevision, contentHash, snapshot, createdAt, actorId | 不可变修改历史，供审核/追溯，不覆盖原审核快照 |
| capture_reviews | id, captureId, captureContentRevision, reviewerId, decision, note, createdAt | 审核固定版本；与审核者机构/病例授权相关 |
| sync_changes | commitPosition, scopeId, entityType, entityId, operation, revision, payload | 与资源写入同事务，含删除/撤权墓碑 |
| sync_mutations / idempotency_records | principalId, scopeId, mutationId/key, requestHash, result | 重试不重复创建，记录成功和确定性失败结果 |
| capture_drafts（P1） | id, lesionId, imageFileId, editorState, referenceOnly, qualityNote, revision | 草稿与正式历史分开；客户端结构化版本化编辑字段 |
| jobs / audit_events（P1/P2） | type, state, revisionPins, actorId, resultIds, requestId | 异步算法/导出与审计；不在审计中记录秘密或图片内容 |

标定/分析/轮廓可以放 JSON 列或关联表；无论采用哪种数据库，都保持 API 的结构和不可变审核快照。图片字节放私有对象存储，数据库不存 Base64 图像。数据库与对象存储分开备份，并能验证引用一致性。

## 6. 核心 DTO 字段映射

### Patient、Lesion

Patient 保留当前 `id/name/birthDate/createdAt/updatedAt`；新增服务端 `revision/deletedAt`。当前本地 `version` 不是服务端 revision，不可直接用于 If-Match。Lesion 保留 `id/patientId/bodySite/note/createdAt/updatedAt`，同样新增 revision/deletedAt。

创建可选传原本机 createdAt 作为 `clientCreatedAt`，不要覆盖服务端审计时间。name/bodySite 不允许空白，note 可空串，生日可为 null。读接口返回完整资源，补丁只包含用户可编辑字段。

### Capture（正式记录）

| 字段 | 类型 | 说明 |
|---|---|---|
| id / lesionId | UUID | 沿用本机采集 ID，不能重试时换 ID |
| imageFileId | UUID | 替代本机 imagePath，引用已确认文件 |
| capturedAt | int64 | 当前 CAMERA 为记录拍摄时间；GALLERY 旧版本记录的是导入时间，不得当作原照片拍摄日期 |
| source | CAMERA / GALLERY / UNKNOWN | 当前部分相册来源仅在 deviceModel 文案中记录，迁移无法可靠判断时用 UNKNOWN |
| assetKind | CAPTURE_IMAGE / ANALYSIS_COPY / UNKNOWN | 相册导入会修正方向/按需采样并转 PNG，属于分析副本，不得标成原始传感器/逐字节原文件 |
| referenceOnly / qualityNote | nullable boolean / nullable string | 旧正式记录未完整保存该字段，未知用 null，不可凭空补 false 或推定实时门控通过 |
| deviceModel | string | 保留现有文案，后续将来源从设备型号字段拆开 |
| calibration | object / null | 标定贴/手动尺度快照 |
| analysis | object / null | 无分析允许 null；无效结果必须 isValid=false，空值不能变成0 |
| annotation | object / null | 与归档文件像素坐标对应 |
| metadata | object | appVersion、algorithmVersions、collectedAt、importedAt、originalCapturedAt 可空、采集 QC；不混入本机文件路径 |
| revision/contentRevision/reviewStatus/createdAt/updatedAt/deletedAt | 服务端字段 | 客户端不得任意授予审核结果或写 owner 范围 |

`syncStatus`、UploadTask 的 attempts/lastError 是客户端传输状态，不是服务端资源事实，不作为上传的可信业务字段。上传成功必须得到远端 Capture 的确认版本和文件状态，不能只因 PUT 成功就标 SYNCED。

### CalibrationInfo

保留 `markerId,markerWidthMm,pixelsPerMm,colorReference[],qualityScore,swatchRgb[],colorCorrection`；Lab 为 `{l,a,b}`，RGB 为 `{r,g,b}`，颜色校正为 `{redScale,redOffset,greenScale,greenOffset,blueScale,blueOffset}`。新增可空 `method=AUTO_MARKER/MANUAL/UNKNOWN`、markerCorners、scalePoints、calibrationVersion，用于后续追溯。

markerWidthMm/pixelsPerMm 必须有限且 >0；qualityScore 范围0–1；RGB通道0–255；Lab/校正参数必须有限。不得只上传校正后的数值而丢失原标定快照。手动尺度与自动颜色校正不能等同，manual 不代表存在色块校正。

### AnalysisResult、LesionAnnotation 与 QC

AnalysisResult 保留 `areaMm2,lesionColor,colorDifference,algorithmVersion,isValid,failureReason`。有效定量结果要求面积有限且 >0、有有效尺度与合法标注；色差可空，存在时有限且 ≥0。referenceOnly=true 不允许有效定量结果。若 isValid=false，前端趋势不得绘入其残余数值。

修改尺度、标注或影响测量的输入后，若请求未同时提交与新输入匹配的重算结果，服务端必须将原 analysis 标记无效或清空，不保留旧结果 isValid=true。只保存/审核客户端结果不代表服务器已经运行算法。

LesionAnnotation 保留 `points[{x,y}],imageWidth,imageHeight,segmentationVersion,edited`。尺寸需与归档文件一致；坐标为副本像素，位于 `[0,width] × [0,height]`；用于有效面积的多边形至少3个不同顶点、非交叉且非退化。归档后不在同 fileId 下替换图片，否则标注坐标失效。

校验和对象存储保留上传字节；文件尺寸按与标注一致的编码像素读取，不能隐式 EXIF 自动旋转后覆盖原对象。需要方向规范化或缩略图时生成独立派生 fileId，并记录坐标变换。旧记录 referenceOnly=null 时不能自动认定通过采集门控或自动审核；isValid 只表示已保存算法结果自身有效，不代表身份核验、临床结论或采集 QC 通过。

QC 建议用结构化 `{liveGateExecuted,passed,issues[],sharpness,meanExposure,clippedPixelRatio,markerDetected,targetSizeRatio,perspectiveRatio}`，未知用 null。GALLERY 的 liveGateExecuted=false；历史旧记录缺乏数据时为 null。不要根据当前设备/图片可读就自动填写 QC 通过。

### 草稿与旧数据导入

本地 `CaptureDraftEntity.editorState` 是 App 私有编码，不建议直接当服务端业务字段。P1 云端草稿使用 `editorState:{schemaVersion,points,scalePoints,referencePoint,markerMm,segmentationVersion,edited,confirmed,step,mode}`，并把 imagePath 转为 imageFileId。步骤0–2，点数上限2000，整份编辑 JSON设大小限制。P0 可不上传草稿；明确告知跨设备只恢复正式已同步记录。

未绑定用户的旧数据：先在 App 明确选择要归入当前账号/机构的患者并确认，再调用受控导入计划；服务端返回 importId 与允许的 clientPatientIds。后续批次提交必须带 importId 并检查范围，不能默认把整库挂到第一个登录账号。名称/日期/照片 hash 相同不自动合并病例。服务端可保留 clientId=serverId，也需处理既有 ID 冲突。

## 7. 上传闭环（P0 单文件，P1 分片）

1. 确认当前真实身份、活动范围、旧数据导入确认；先创建/同步 Patient，再 Lesion。
2. 本机计算文件 SHA-256、字节长度、实际 MIME、归档图宽高；初始化上传得到 uploadId/fileId/strategy/短期 URL/requiredHeaders/expiresAt。
3. 将本机文件字节直接 PUT 到签名 URL；这个请求不附应用 Bearer token，签名 URL 不写入日志或作为永久图片地址保存。URL 过期可重新申请；同 scope 内的可授权去重不能泄露其他患者/机构是否有相同文件。
4. 调 complete。服务端验证对象实际存在、实际长度、SHA-256、图片类型、尺寸/解码上限；多段 ETag 不能代替 SHA-256。同步校验完成返回 200 File(READY)；异步校验返回202，轮询上传状态直到 READY 或 FAILED。
5. 创建 Capture 元数据，引用 READY 且 kind=IMAGE 的 fileId。服务端检查患者/病灶归属、文件范围及所有算法数据；原子写入 Capture、revision 和同步事件。导出 ZIP/JSON 不能作为采集图像引用。
6. App 保存远端确认 revision 后才能标记 SYNCED；请求已提交但响应丢失时，用原幂等键/原 ID 查询确认，不重复创建。

分片：初始化 strategy=MULTIPART，服务端指定 partSizeBytes/maxParts；sign-parts 获取允许段号的 URL；客户端记录完成段及校验信息；断点恢复 GET upload 列出已接受段；complete 提交按段号排序的 partNumber/etag/sha256。不得让客户端任意提交其他 bucket/objectKey 或绕过范围复用 uploadId。DELETE upload 中止，遗留对象由后台回收。

单文件续签：`Upload.expiresAt` 是上传会话有效期，`urlExpiresAt` 是本次上传 URL 有效期，两者分开。SINGLE URL 过期但会话未过期时，调用 `POST /file-uploads/{uploadId}/sign-single`，用本次续签的新幂等键返回新 URL；重试同一次续签继续使用原键。它不新建 fileId/uploadId，也不修改期望 hash/长度/MIME。初始化幂等键只重放原初始化结果，不负责续签。会话已过期返回410，必须以新初始化键创建会话；旧未引用对象由服务端回收。VERIFYING/READY 状态不再允许重新上传覆盖，返回409。

init URL默认仅支持指定 MIME、对象和长度范围；文件100 MiB上限可作为当前 App 的服务端初始配置，但应通过 capabilities 下发并检查真实大小。不得只依赖请求头或扩展名。

File.kind 区分 IMAGE、EXPORT_ARCHIVE、ALGORITHM_ARTIFACT。图片 width/height 必须为正整数；导出 ZIP/JSON 的 width/height 为 null。客户端上传接口只接收图片，其余类型只能由有权限的服务端任务生成；导出文件下载仅限申请人及授权范围，并受过期策略约束。App 不得把导出归档文件传入图片解码流程。

### 提交采集示例

```json
{
  "id": "11111111-1111-4111-8111-111111111111",
  "lesionId": "22222222-2222-4222-8222-222222222222",
  "imageFileId": "33333333-3333-4333-8333-333333333333",
  "capturedAt": 1793664000000,
  "deviceModel": "Android phone",
  "source": "CAMERA",
  "assetKind": "CAPTURE_IMAGE",
  "referenceOnly": false,
  "qualityNote": null,
  "calibration": {
    "markerId": "manual-marker", "markerWidthMm": 10, "pixelsPerMm": 20,
    "qualityScore": 0.9, "colorReference": [], "swatchRgb": [], "colorCorrection": null,
    "method": "MANUAL"
  },
  "analysis": {
    "areaMm2": 25, "lesionColor": {"l": 70,"a": 2,"b": 8},
    "colorDifference": 5, "algorithmVersion": "v1-basic", "isValid": true,"failureReason": null
  },
  "annotation": {
    "points": [{"x":100,"y":100},{"x":200,"y":100},{"x":200,"y":200},{"x":100,"y":200}],
    "imageWidth": 1200,"imageHeight": 1600,"segmentationVersion": "manual","edited": true
  },
  "metadata": {"appVersion":"1.6.0","liveQc":{"liveGateExecuted":true,"passed":true,"issues":[]}}
}
```

以上仅为字段示例，服务端仍需检查真实文件尺寸、尺度/面积一致性与当前访问范围。仅照片提交：calibration/annotation 可空，analysis 为 null 或明确无效，不补零、不伪造正常皮肤参考。

## 8. 拉取与同步

### 首次/重装拉取

POST `/sync/snapshots` 创建授权范围内一致性快照，返回 snapshotId/expiresAt。GET snapshot 按 pageToken 获取稳定的数据集，每页包含 `items:[{entityType,entityId,revision,payload}]`；最后一页返回 nextCursor。快照期间新增/更新资源不混入本轮页结果，通过该 nextCursor 后续增量获取。快照过期返回410后重新开始，不拼接两次快照。

服务端快照读取位置与后续变更游标必须关联同一提交水位。**数据库自增 ID 分配顺序不一定等于事务提交顺序**，不能简单用 MAX(changeId) 作为游标然后跳过稍后提交的小 ID。采用可证明提交有序的日志/水位，或包含事务位置的成熟变更方案。

### 增量拉取

GET `/sync/changes?cursor=opaque&limit=100` 返回 `{items,nextCursor,hasMore}`。每项包括 `entityType/entityId/operation/revision/payload`，operation 为 UPSERT/DELETE/ACCESS_REVOKED；后两者 payload=null。游标绑定用户、机构和授权范围/版本，禁止拿其他用户游标查询。

App 在一个本地事务中应用一页资源变更/墓碑并保存新游标；中途崩溃重放同页按 revision 幂等应用。未同步本地修改不因拉取被静默覆盖，而是记录冲突待处理。文件按需通过授权下载接口缓存；不能把对象 URL 作为本地 imagePath，必须写入私有文件后再供算法读取。

快照按父资源优先输出（Patient→Lesion→Capture/Draft→关联/审核），同一事务的增量事件带稳定内部顺序。客户端对缺失依赖应暂存或授权补取，不能显示孤立采集。删除/撤权父级时，先在本机隐藏并级联处理其子资源，再续拉后续墓碑；不得因跨页而继续展示已撤权内容。

撤权应返回不含敏感 payload 的 ACCESS_REVOKED 墓碑，或令旧游标失效强制完整重建并清理不再授权的本地远端缓存。普通删除标 deletedAt；墓碑需保留至少覆盖承诺的离线窗口。超过保留期返回410重建，不假装没有新数据。重新登录/切换机构必须切换本地数据分区、待上传队列和游标。

**授权扩大也需要补齐旧数据**：新增/重新授予 grant、增加患者关联或机构成员权限时，递增该身份的授权范围 generation，使旧增量游标和在途快照返回410 SYNC_RESET_REQUIRED；客户端重新建立完整快照，把早于旧游标的历史病例一起拉取。不能仅发送新增 grant 事件后继续旧游标。授权缩小采用同一失效规则；本机先隔离旧远端缓存，保留未同步编辑为冲突/待处理区，不继续对外展示已失去权限的病例。

### 上传冲突

普通 PATCH 及 `/sync/push` 共用服务端验证和事务逻辑。P1 push 每批最多100条，按 dependencies/顺序处理，每条 mutationId 独立幂等，每条资源操作原子，**整批不承诺原子**。返回逐项 APPLIED/CONFLICT/REJECTED/DEPENDENCY_FAILED；200不代表全部成功。文件字节不走 push。

同一 revision 上的两台设备更新：先提交者成功，后者412；返回当前 revision 和可授权的服务端资源，App展示差异再合并或放弃本地修改。照片字节、分析与审核不采用静默“最后时间覆盖”。更新采集内容增加 revision，审核状态回 NOT_SUBMITTED 并保留旧审核记录。

## 9. 医院管理与审核流程

管理员受控创建/开通机构与成员；患者账户绑定应由核验、受控邀请或可信医院流程完成。患者授权机构/医生查看病例，服务端同时验证成员资格和病例 grant。撤销成员或 grant 后令牌/游标/下载签名的有效策略一并处理；短期签名发出后通常不能即时回收，因此签名有效期要短且不得作永久授权依据。

审核状态：NOT_SUBMITTED → PENDING → APPROVED/REJECTED。提交审核需要有效 captureContentRevision；待审内容修改后撤销该待审状态；审批必须同时检查 If-Match 与 captureContentRevision。自己采集不自动获得审核权限；是否允许审核自己记录由机构策略明确设定。审核只追加不可变记录，不篡改手机原始算法结果。拒绝需说明理由，重新提交绑定新版本。

**资源版本与内容版本分离**：Capture.revision 用于 ETag/同步，任何公开状态变化都递增；contentRevision 仅在图像/标注/标定/算法结果或采集元数据内容改变时递增。审核与算法输入使用 captureContentRevision 固定内容，而不是把资源 ETag 当内容版本。

例如 Capture 为 revision=5/contentRevision=3：提交审核用 If-Match="5"、captureContentRevision=3，同事务新增 Review(revision=1、内容3) 并把 Capture 改为 revision=6/PENDING（内容仍3）。审批用 Review 的 If-Match="1"，校验内容仍3、Review仍PENDING；同事务 Review升revision=2、Capture升revision=7/APPROVED，分别写同步事件。随后 CapturePatch 用 If-Match="7" 修改内容后变为 revision=8/contentRevision=4/NOT_SUBMITTED，待审 Review置VOID并递增其revision；已完成的旧审核作为内容3的历史保留。相同 Capture.revision 绝不对应两个不同 reviewStatus。

账号绑定核验有独立的申请/查询/决定接口，核验员使用可信院内资料完成核验，不允许客户端自己声明 VERIFIED。verificationId 绑定 patientId、userId、relation、有效期和发起范围；创建 patient_accounts 时同事务检查并一次性消费，不得跨病例或账号复用。短信注册 challenge 不能替代此核验。

可选云端任务返回 Job.analysisResult 或 Job.alignmentResult，并固定输入资源版本。配准 transform 是9个有限数的行优先3×3矩阵，把 moving 归档图像坐标映射到 reference 图像坐标；失败时不伪造单位矩阵。成功的透明叠加图通过受控 image fileId 下载。任务完成不静默覆盖采集结果，需要有写权限的人用 CapturePatch/If-Match明确采用新结果。

删除 Patient 为受控软删除，原子写 Patient 与子资源墓碑，或202删除任务且完成前标 DELETING 禁止写子记录。不能只删父表让其他设备保留孤立病灶。对象存储物理删除、账号关闭及审计/数据保留策略由机构正式配置，不在 API 中承诺立即物理抹除所有记录。

## 10. 开发验收清单

- 两个不同账号不能通过替换 patientId/fileId/uploadId/reviewId 访问对方数据；医院成员仍需病例授权。
- 登录 role hint 不授予医院/管理员权限；凭证哈希、刷新摘要不出现在响应或日志。
- 并发刷新、刷新令牌重放、退出后刷新、禁用成员后旧 token 的结果明确；密码找回不枚举账号。
- PUT成功但 complete失败、校验不一致、URL过期、断点重传、响应丢失均能恢复，不生成重复 Capture。
- Capture 引用非 READY 文件或不同范围文件被拒绝；非有限结果、错误尺寸和交叉轮廓被拒绝。
- 拉取分页期间并发提交不漏数据；事务乱序提交不丢事件；游标过期/撤权有明确处理。
- 本地未同步修改遇远端删除/更新不会静默丢失；多个设备修改返回冲突，审核不会通过旧版本。
- 旧本机数据确认归属后才能上传；旧相册副本不被包装成原始照片或通过实时 QC。
- 先实现 P0 闭环再做 P1/P2；接口工具导入通过后仍需真实服务端集成与权限/并发测试。
