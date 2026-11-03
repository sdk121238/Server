# 皮肤病随访后端（P0）

Java 21 + Spring Boot 3.5.16 + MySQL 8.0，基于 [接口合同](docs/backend/README.md) 实现。当前交付个人工作区的 P0 闭环；医院成员/病例授权、审核、短信注册、云端草稿、批量同步与服务器算法属于尚未启用的 P1/P2，不宣称已经完成。

## 本机环境与启动

复用电脑已有的 IDEA JDK 21.0.7、IDEA Maven 3.9.9 和 MySQL 8.0.42。所有脚本只修改当前进程环境，未修改系统 JAVA_HOME、原 3306 MySQL 服务或原 root 密码。

IDEA可直接打开 `pom.xml`；将项目SDK及Maven运行JRE指向已有的 `D:\搜狗高速下载\IntelliJ IDEA 2025.1.3\jbr`，不要沿用此目录原先选择的Java 27。命令行脚本已自动选择Java 21。

在项目根目录的 PowerShell 执行：

```powershell
.\scripts\dev-db.ps1 start
.\scripts\mvn.ps1 package
.\scripts\server.ps1 start
```

服务地址 `http://127.0.0.1:8080/api/v1`，健康检查 `GET /system/health`。本机开发实例只监听 `127.0.0.1:3307`，库名 `derm_followup`，数据库用户 `derm_app`。数据库数据在 `.codex-build/mysql-dev/data`，图片在 `data/objects`，不要删除这些目录来清理构建缓存。

首次运行数据库脚本会生成随机数据库密码、签名密钥和 API 初始账号密码，保存在被 Git 忽略的本机文件，且文件 ACL 只允许当前 Windows 用户和 SYSTEM 访问。**不会将密码打印到终端或写入 README。** API 初始账号是 `local-owner`，其密码可在本机编辑器中查看 `scripts/local.ps1` 的 `BOOTSTRAP_PASSWORD`。使用后可通过修改密码接口更换；bootstrap 仅在账号不存在时创建，不重置已有账号。

```powershell
.\scripts\server.ps1 status
.\scripts\server.ps1 stop
.\scripts\dev-db.ps1 stop
```

停止脚本只针对本项目进程。数据库运行期间不能删除数据目录。独立开发实例不是新的 MySQL 安装，未注册新的系统服务。

## 配置

`scripts/mvn.ps1` 和 `scripts/server.ps1` 自动读取忽略的 `scripts/local.ps1`。迁移至其他电脑时，可设置 `PROJECT_JAVA_HOME`、`PROJECT_MAVEN_HOME` 指向已有工具。

生产/其他 MySQL 使用环境变量：

| 变量 | 用途 |
|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 专用 MySQL 库连接；建议 utf8mb4_bin 排序规则 |
| `APP_SIGNING_KEY` | 至少32字符的随机密钥；多实例保持一致并妥善备份 |
| `APP_PUBLIC_URL` | 签名上传/下载的外部基础地址，生产需 HTTPS |
| `STORAGE_ROOT` | 私有持久化图片目录，禁止配置为公开静态目录 |
| `BOOTSTRAP_ACCOUNT` / `BOOTSTRAP_PASSWORD` | 受控开通个人账号；密码至少12字符、UTF-8最多72字节 |
| `SERVER_PORT` | 默认8080 |
| `SERVER_ADDRESS` | 默认127.0.0.1；手机局域网联调时可显式设置为本机局域网地址 |

Flyway 在启动时创建/升级表。应用必须能访问已经建立的专用库，不会自动拿 root 操作其他数据库。首次创建账号后可移除 bootstrap 环境变量。生产部署应分离迁移与日常业务数据库权限，启用 HTTPS、数据库 TLS、备份和持久化文件卷；本机3307连接关闭TLS仅适用于回环地址开发。

## 已实现的行为

- 登录、刷新轮换/重放撤销、退出、资料 ETag、修改资料/密码，BCrypt 密码和令牌摘要存储，持久化登录限流。
- 患者/病灶/采集 CRUD；资源归属来自服务端；UUID 统一规范形式；删除父资源同事务写入全部子资源墓碑。
- JSON 字段白名单与 OpenAPI 请求验证；递归 Merge Patch；If-Match 428/412；创建/提交等请求的持久化事务幂等，确定性失败同样记录。失败重试若需要改变请求内容或条件，应使用新幂等键。
- JPEG/PNG 单文件签名上传、续签、中止、实际字节 SHA-256/类型/尺寸/解码校验、不可变 READY 文件和短期授权下载。最大100MiB、4000万像素；WebP及分片上传未启用，通过 capabilities 明示。
- 文件和采集必须属于同一患者；标注坐标与归档图像一致；有效面积与尺度/多边形一致（允许2%或0.01 mm²舍入误差）；修改尺度或轮廓未提交完整重算结果时清空旧分析。
- 旧数据导入范围确认、存储式一致快照、增量事件游标和普通分页游标；游标绑定用户和授权generation；快照24小时、普通分页24小时、增量游标90天。当前不清理同步墓碑和幂等记录，防止离线重试被提前遗忘。
- 所有P0操作均有路由；P0工作同步执行，因此当前没有异步任务，`GET /jobs/{jobId}` 对不存在的任务返回404。P1/P2入口返回501，capabilities 不宣称启用扩展。

图片通过应用签名 `/objects/{id}?ticket=...` 存取。PUT/GET签名有效期分别最多5分钟/2分钟，不需附Bearer令牌，访问时仍检查账号及患者可用状态。患者数据和文件对象不会放在公开静态目录中。无法验证的上传可以续传/重试，确认失败不转为READY。

每个个人工作区的所有写事务先锁定其 users 行，直到业务、版本历史、幂等记录及同步事件共同提交；因此同一范围的事件位置与提交顺序一致，不使用自增ID猜测提交顺序。不同工作区可以并行。P1机构授权加入时须扩展范围模型，并在授权扩大或缩小时递增generation，当前不会开通医院角色来绕过这一限制。

## 验证

```powershell
# 内存数据库集成测试及业务校验测试（不依赖MySQL启动）
.\scripts\mvn.ps1 test

# 在本项目3307实例上运行相同HTTP/事务集成测试
.\scripts\test-mysql.ps1
```

MySQL测试脚本只重建独立开发实例中的 **derm_followup_test** 测试库，保留 `derm_followup` 和原3306实例。测试覆盖账号隔离、幂等并发、刷新重放、快照/墓碑、上传下载闭环、Merge Patch、导入范围和几何边界。可执行 JAR 在 `target/followup-server-0.1.0.jar`。

详见 [本次实现与验证记录](docs/implementation-status.md)。

目前采用每范围串行写入和服务内存筛选普通列表，适合开发/初期联调；大病例库需要将列表过滤下推SQL，并采用分批生成快照、批量级联删除和后台文件清理。多实例部署必须挂载共享私有文件卷或增加对象存储适配器。当前没有短信供应商和临床身份核验接入，也不运行服务器图像算法。
