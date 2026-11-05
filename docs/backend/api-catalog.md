# 后端接口逐项清单

共 75 个业务 HTTP 操作；不计对象存储签名 URL 上的实际 PUT/GET。P0/P1/P2 含义与业务约束见 [总说明](README.md)。所有路径前缀 `/api/v1`。

输入/输出类型名称对应 [OpenAPI schemas](backend-v1.openapi.json)。`—` 表示无请求正文或204无响应正文；`[Type]` 表示分页集合（items、nextPageToken），PartUrls 仅含 items，不分页。所有受限字段、父子权限和版本条件还需结合总说明执行。

## 认证

| 优先级 | 方法与路径 | 用途 | 请求 → 响应 | 授权/约束 |
|---|---|---|---|---|
| P0 | `POST /auth/login` | 账号密码登录 | Login → Tokens | 公开，限流；角色只作提示 |
| P0 | `POST /auth/refresh` | 轮换刷新令牌 | Refresh → Tokens | 有效刷新令牌，检测重放 |
| P0 | `POST /auth/logout` | 退出并撤销会话 | Refresh → — | Bearer + 刷新令牌 |
| P0 | `GET /auth/me` | 当前公开账号信息 | — → Profile | 本人 |
| P1 | `POST /auth/challenges` | 申请注册或恢复验证码 | ChallengeRequest → Challenge | 公开，限流、防枚举 |
| P1 | `POST /auth/register` | 注册患者账号 | Register → Profile | 公开，仅患者；验证码一次性消费 |
| P1 | `POST /auth/password-resets` | 验证并重置密码 | ResetPassword → — | 公开，恢复凭证，撤销旧会话 |

## 账号

| 优先级 | 方法与路径 | 用途 | 请求 → 响应 | 授权/约束 |
|---|---|---|---|---|
| P0 | `PATCH /users/me` | 修改显示名 | ProfilePatch → Profile | 本人，不允许自改角色/机构 |
| P0 | `POST /users/me/password` | 修改密码 | PasswordChange → — | 本人，验证当前密码并撤销其他会话 |
| P1 | `GET /users/me/sessions` | 查询本人会话 | — → [SessionInfo] | 本人 |
| P1 | `DELETE /users/me/sessions/{sessionId}` | 撤销指定会话 | — → — | 本人 |
| P1 | `POST /users/me/active-organization` | 切换活动机构 | OrganizationSwitch → Tokens | 本人且机构成员有效；重置同步上下文 |
| P1 | `POST /users/me/closure` | 申请关闭账号 | CloseAccount → Job | 本人，重新验证；不直接抹除医疗记录 |

## 机构

| 优先级 | 方法与路径 | 用途 | 请求 → 响应 | 授权/约束 |
|---|---|---|---|---|
| P1 | `POST /organizations` | 后台受控开通机构 | OrganizationCreate → Organization | 平台受控管理权限，非公开自助医院注册 |
| P1 | `GET /organizations` | 查询可管理或所属机构 | — → [Organization] | 已授权范围 |
| P1 | `GET /organizations/{organizationId}` | 机构详情 | — → Organization | 成员或机构管理权限 |
| P1 | `PATCH /organizations/{organizationId}` | 修改或停用机构 | OrganizationPatch → Organization | 机构/平台管理权限 |
| P1 | `GET /organizations/{organizationId}/members` | 机构成员列表 | — → [Membership] | 机构管理权限 |
| P1 | `POST /organizations/{organizationId}/members` | 添加或邀请工作人员 | MembershipCreate → Membership | 机构管理权限，用户必须可信开通 |
| P1 | `PATCH /organizations/{organizationId}/members/{memberId}` | 修改成员身份或停用 | MembershipPatch → Membership | 机构管理权限，防止自助越权 |

## 患者

| 优先级 | 方法与路径 | 用途 | 请求 → 响应 | 授权/约束 |
|---|---|---|---|---|
| P0 | `GET /patients` | 授权患者分页列表 | — → [Patient] | 本人关联或病例授权；可按姓名过滤 |
| P0 | `POST /patients` | 新建患者或确认归属后导入 | PatientCreate → Patient | patient:create；导入须检查 importId |
| P0 | `GET /patients/{patientId}` | 患者详情 | — → Patient | patient:read + 资源范围 |
| P0 | `PATCH /patients/{patientId}` | 修改患者基本信息 | PatientPatch → Patient | patient:write |
| P0 | `DELETE /patients/{patientId}` | 受控删除患者及子资源 | — → — | patient:delete；子资源墓碑同事务，较大删除可202 |
| P1 | `GET /patients/{patientId}/accounts` | 查询核验后的患者账号关联 | — → [PatientAccount] | 患者本人/授权病例管理者 |
| P1 | `POST /patients/{patientId}/accounts` | 核验后关联本人或监护账号 | PatientAccountCreate → PatientAccount | 患者绑定管理权限 + 有效 verificationId |
| P1 | `POST /patients/{patientId}/account-link-verifications` | 受控申请病例账号绑定核验 | LinkVerificationCreate → LinkVerification | patient:verify；核验材料引用可信院内记录，非客户端自证 |
| P1 | `GET /account-link-verifications/{verificationId}` | 查询绑定核验状态 | — → LinkVerification | 申请人或核验员，限同病例范围 |
| P1 | `POST /account-link-verifications/{verificationId}/decision` | 受控确认或拒绝绑定核验 | LinkVerificationDecision → LinkVerification | patient:verify；独立核验员，按机构自审限制 |
| P1 | `DELETE /patients/{patientId}/accounts/{linkId}` | 撤销账号关联 | — → — | 本人解绑或受控管理权限 |

## 授权

| 优先级 | 方法与路径 | 用途 | 请求 → 响应 | 授权/约束 |
|---|---|---|---|---|
| P1 | `GET /patients/{patientId}/grants` | 查询病例授权 | — → [Grant] | 患者本人/授权管理者 |
| P1 | `POST /patients/{patientId}/grants` | 授权机构或医生访问 | GrantCreate → Grant | grant:create，不得授出超出自身范围的权限 |
| P1 | `DELETE /patients/{patientId}/grants/{grantId}` | 撤销病例授权 | — → — | grant:revoke；同步撤权墓碑 |

## 病灶

| 优先级 | 方法与路径 | 用途 | 请求 → 响应 | 授权/约束 |
|---|---|---|---|---|
| P0 | `GET /patients/{patientId}/lesions` | 患者病灶列表 | — → [Lesion] | lesion:read + 患者授权 |
| P0 | `POST /lesions` | 新建病灶 | LesionCreate → Lesion | lesion:create + 患者授权 |
| P0 | `GET /lesions/{lesionId}` | 病灶详情 | — → Lesion | lesion:read |
| P0 | `PATCH /lesions/{lesionId}` | 修改部位和备注 | LesionPatch → Lesion | lesion:write |
| P0 | `DELETE /lesions/{lesionId}` | 删除病灶及采集墓碑 | — → — | lesion:delete；父子变更原子 |

## 采集

| 优先级 | 方法与路径 | 用途 | 请求 → 响应 | 授权/约束 |
|---|---|---|---|---|
| P0 | `GET /lesions/{lesionId}/captures` | 病灶历史记录分页 | — → [Capture] | capture:read；按 capturedAt,id 稳定排序 |
| P0 | `POST /captures` | 提交图片引用与标定分析快照 | CaptureCreate → Capture | capture:create；文件READY且归属匹配 |
| P0 | `GET /captures/{captureId}` | 完整采集详情 | — → Capture | capture:read |
| P0 | `PATCH /captures/{captureId}` | 更新标注或算法结果 | CapturePatch → Capture | capture:write；新revision，原审核失效 |
| P0 | `DELETE /captures/{captureId}` | 删除采集并写墓碑 | — → — | capture:delete；文件延迟清理 |
| P1 | `GET /lesions/{lesionId}/trends` | 查询有效面积及色差趋势 | — → [TrendPoint] | capture:read；无效/仅照片不补0 |

## 文件

| 优先级 | 方法与路径 | 用途 | 请求 → 响应 | 授权/约束 |
|---|---|---|---|---|
| P0 | `POST /file-uploads` | 初始化单文件或分片上传 | UploadCreate → Upload | file:create + 患者授权 |
| P0 | `GET /file-uploads/{uploadId}` | 上传进度及已接受分片 | — → Upload | 上传发起者/同范围授权；过期410 |
| P0 | `POST /file-uploads/{uploadId}/sign-single` | 续签同一单文件上传URL | — → UploadUrl | 原上传发起者，原会话未过期且未进入校验 |
| P1 | `POST /file-uploads/{uploadId}/sign-parts` | 申请指定分片签名URL | SignParts → PartUrls | 上传发起者，段号/次数限额 |
| P0 | `POST /file-uploads/{uploadId}/complete` | 完成上传并校验字节 | UploadComplete → File | 上传发起者；异步校验时202 Upload |
| P0 | `DELETE /file-uploads/{uploadId}` | 中止未完成上传 | — → — | 上传发起者；已提交文件不可中止 |
| P0 | `GET /files/{fileId}` | 文件元信息，不返回本机路径 | — → File | file:read + 所属病例授权 |
| P0 | `POST /files/{fileId}/download-url` | 申请短期授权下载URL | — → DownloadUrl | file:read；每次重新校验病例权限 |
| P1 | `DELETE /files/{fileId}` | 删除未被引用文件 | — → — | file:delete；被采集/草稿引用返回409 |

## 草稿

| 优先级 | 方法与路径 | 用途 | 请求 → 响应 | 授权/约束 |
|---|---|---|---|---|
| P1 | `GET /lesions/{lesionId}/drafts` | 跨设备草稿列表 | — → [Draft] | draft:read；默认仅本人草稿 |
| P1 | `POST /drafts` | 创建云端草稿 | DraftCreate → Draft | draft:create；文件已确认 |
| P1 | `GET /drafts/{draftId}` | 获取草稿编辑快照 | — → Draft | 草稿所有者/显式授权 |
| P1 | `PATCH /drafts/{draftId}` | 保存结构化编辑快照 | DraftPatch → Draft | 草稿写权限；版本冲突不可静默覆盖 |
| P1 | `DELETE /drafts/{draftId}` | 删除草稿 | — → — | 草稿删除权限 |

## 审核

| 优先级 | 方法与路径 | 用途 | 请求 → 响应 | 授权/约束 |
|---|---|---|---|---|
| P1 | `POST /captures/{captureId}/review-submissions` | 提交指定版本审核 | ReviewSubmit → Review | review:submit + 病例权限 |
| P1 | `GET /captures/{captureId}/reviews` | 审核历史 | — → [Review] | review:read + 病例权限 |
| P1 | `GET /reviews` | 机构待审列表 | — → [Review] | review:read；可筛选status=PENDING |
| P1 | `POST /reviews/{reviewId}/decision` | 批准或拒绝固定版本 | ReviewDecision → Review | review:decide；检查机构/版本/自审策略 |

## 同步

| 优先级 | 方法与路径 | 用途 | 请求 → 响应 | 授权/约束 |
|---|---|---|---|---|
| P0 | `POST /offline-imports` | 确认旧本机数据导入范围 | ImportCreate → ImportPlan | 已验证身份，用户已明确确认归属 |
| P0 | `POST /sync/snapshots` | 建立一致性首次同步快照 | — → Snapshot | 当前账号和活动范围 |
| P0 | `GET /sync/snapshots/{snapshotId}` | 分页读取快照 | — → SnapshotPage | 快照所有者与原授权范围 |
| P0 | `GET /sync/changes` | 按提交水位增量拉取 | — → Changes | cursor绑定身份/机构/授权版本 |
| P1 | `POST /sync/push` | 批量幂等推送元数据变更 | Push → PushResult | 每条独立校验资源权限，不接收图片字节 |

## 任务

| 优先级 | 方法与路径 | 用途 | 请求 → 响应 | 授权/约束 |
|---|---|---|---|---|
| P1 | `POST /exports` | 申请授权病例数据导出 | ExportRequest → Job | export:create + 每个患者授权 |
| P2 | `POST /captures/{captureId}/reanalysis-jobs` | 申请服务器重新分析 | Reanalysis → Job | analysis:run；固定采集版本 |
| P2 | `POST /alignment-jobs` | 申请服务器两图对齐 | Alignment → Job | analysis:run；双方采集均可读，固定版本 |
| P0 | `GET /jobs/{jobId}` | 查询本人可见异步任务 | — → Job | 任务所有者/授权管理者；含大批量删除任务 |

## 运维

| 优先级 | 方法与路径 | 用途 | 请求 → 响应 | 授权/约束 |
|---|---|---|---|---|
| P0 | `GET /system/capabilities` | 协商上传和同步能力 | — → Capabilities | 已登录客户端 |
| P0 | `GET /system/health` | 仅暴露最小健康状态 | — → Health | 公开或网关限制，不含配置及依赖密码 |
| P1 | `GET /audit-events` | 查询受控审计事件 | — → [AuditEvent] | audit:read，受机构/平台权限限制 |

## 写入条件与状态码

PATCH/资源删除通常需 If-Match；准确的例外、审核版本条件、幂等键和成功响应码已写入 OpenAPI 各 operation。POST 不一概表示201，异步任务为202；批量推送200需逐项查看结果。文件 complete 200为READY、202为仍校验。

绑定患者账号需要可信 verificationId，由机构受控核验系统产生；当前合同不把认证短信 challenge 自动等同于患者病例绑定核验。实际启用前必须实现该核验流程或关闭绑定入口。

账号受控开通、平台机构开通可以先通过受限管理工具完成；本文不规定对公网开放的超级管理员注册/任意账号创建入口。

系统能力、分页上限和上传大小均由服务端约束；客户端参数不是可信限制。

## v1.1.0 扩展

| 优先级 | 方法与路径 | 用途 | 输入/响应 | 权限 |
|---|---|---|---|---|
| P0 | `GET /doctors` | 可选主治医师目录 | q/limit/pageToken → DoctorPage | 已登录，仅有效医生ID及显示名 |

GET /patients支持scope=all/mine；患者始终仅本人，医生all读全库、mine按主治关联。医生跨账号只读，个人同步范围不扩大。患者新增字段与年龄规则见 [专项说明](../patient-doctor-guide.md)。
