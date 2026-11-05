# 患者与医生账号、资料字段及联调说明

适用：API v1.1.0，2026-11-05。

## 权限

| 功能 | 普通患者 PATIENT | 医院医生 HOSPITAL_STAFF |
|---|---|---|
| 患者、病灶、采集记录、图片读取 | 自己账号拥有的数据 | 全部患者数据 |
| 新增、修改、删除 | 自己账号拥有的数据 | 自己账号拥有的数据；不能修改别人的数据 |
| GET /patients?scope=all | 自己账号患者 | 全部患者 |
| GET /patients?scope=mine | 自己账号患者 | attendingDoctorId 为自己的患者 |
| 查看医生列表 | 允许 | 允许 |
| 个人快照与增量同步 | 自己拥有的数据 | 自己拥有的数据；全库查看使用在线接口 |

“自己账号”以 owner_id 为准；主治关联不转移所有权，不限制医生全库只读的能力。医生账号也不能查看别人的密码、会话或上传会话。登录中的 requestedRole 不能授予权限，真实角色由数据库决定。患者未关联医生时医生依然可以在全库列表中查看。

## 创建账号（可双击操作）

1. 按原教程启动项目 MySQL。
2. 在项目根目录双击 **Create-Account.cmd**。
3. 输入新账号、显示名，选择 **1 患者** 或 **2 医生**。
4. 输入两次初始密码，至少12个字符、UTF-8不超过72字节。输入时不显示密码。
5. 看到 `Account created successfully` 后，使用新账号在 Swagger 登录。

此入口由能够访问后端电脑及本地数据库配置的维护人员使用。同名账号（含大小写或规范化后相同）会报错，不覆盖密码、不提升角色；原有账号升级后仍是患者。本次不会自动创建实际医生账号。密码只短暂传入子进程环境，不出现在启动命令参数中，也不会写入凭据文件。

Swagger 地址：http://127.0.0.1:8080/swagger-ui/index.html 。先用新账号登录，复制 accessToken 到 Authorize，再执行 GET /auth/me 核对 role。

## 患者字段

| 字段 | 类型 | 写入规则 |
|---|---|---|
| phoneNumber | string / null | 可选+号和7～15位数字；空串或空白规范化为null |
| gender | string / null | MALE 男、FEMALE 女、OTHER 其他、UNKNOWN 未知 |
| birthYearMonth | string / null | YYYY-MM，不能晚于当前年月 |
| birthDate | string / null | 兼容旧版，完整日期YYYY-MM-DD，不能在未来 |
| age | integer / null | 只读，自动计算，不能在POST/PATCH提交 |
| attendingDoctorId | UUID / null | 可选，必须来自有效医生列表；null取消关联 |
| attendingDoctor | object / null | 只读摘要：id、displayName、unavailable |

有完整生日时年龄按周岁计算；仅有年月时，以出生月是否已到计算整岁。例如2000-11出生，在2026-10为25，在2026-11为26。时区为Asia/Shanghai。缺少出生信息时age为null；年龄不存为会过期的固定数据库数值。

birthDate会推导birthYearMonth；两者一起填写时必须同月。仅修改出生年月且与旧完整生日不同月时，旧birthDate清空；只清空birthDate会保留已有年月。要清空全部出生信息，同时提交两个null。保留完整生日时不能单独清空年月。

主治医师来自 GET /doctors，不接受普通患者ID、停用医生或不存在的ID。旧关联医生被停用后，患者仍可读取旧关联，摘要unavailable=true；可清空或更换。改医生显示名不会改变患者revision。

新患者示例：

```json
{
  "id": "11111111-1111-4111-8111-111111111111",
  "name": "虚构测试患者",
  "phoneNumber": "+8613800000000",
  "gender": "UNKNOWN",
  "birthYearMonth": "1990-06",
  "attendingDoctorId": null
}
```

主治选择流程：GET /doctors → 保存选择项id → POST /patients或PATCH /patients/{patientId}传attendingDoctorId。PATCH仍需先读取资源并传最新If-Match；不要把age、attendingDoctor等展示字段提交回来。

## 前端接口对齐

- GET /doctors?q=&limit=50&pageToken=：需Bearer，返回items[{id,displayName}]与nextPageToken。只展示有效医生，不返回登录账号。
- GET /patients?scope=all：默认值，医生全库列表。
- GET /patients?scope=mine：医生主治名下列表。
- 保留q、scope和其他原筛选条件翻页；不能把all的pageToken用于mine，否则410。患者始终限定本人，传scope=all不会扩大权限。
- 医生读取病灶、采集、文件详情与申请图片下载URL沿用原接口。跨账号修改/删除返回404。图片签名下载时也重新检查账号角色、状态和患者是否已删除。
- 医生跨账号只能读取READY文件元信息；未确认或已中止的上传文件返回404。文件所有者仍可读取本人PENDING元信息以完成上传流程。
- role、菜单和只读按钮可用于界面展示，真正权限由后端验证；不能仅靠隐藏按钮限制访问。
- 个人离线同步不下载医生可见的全库数据。快照/增量中的患者存储字段包含出生信息和attendingDoctorId；age与医生显示名由读取时生成或客户端从基础字段解析。

## 验收场景

| 场景 | 预期 |
|---|---|
| 患者A读取患者B的详情、病灶、采集、文件 | 404 |
| 医生读取A/B的数据和已确认图片 | 200 |
| 医生修改或删除A/B的记录 | 404 |
| 医生all/mine切换 | 全部/主治关联名下，分页条件绑定 |
| 指定患者账号ID作为医生 | 422 |
| 主治医师留空、取消或更换 | 正常，所有权不改变 |
| 未知电话格式、枚举、未来出生日期或提交age | 422 |
| 医生降为患者后继续读取别人的数据或使用旧图片URL | 拒绝 |
| 旧患者有完整生日 | 迁移回填出生年月，原版本和时间保留 |

## 数据与升级

users.role保存两种角色。patients新增patient_name（数据库姓名搜索）、phone_number、gender、birth_year_month、attending_doctor_id列；主治医师使用外键和索引。payload同步保存对应资料。V1保持原样，V2增列、V3回填；不重建业务库。备份数据库时同时保留data/objects中的图片。
