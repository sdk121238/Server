# 使用鼠标启动后端

## 日常运行：IDEA + 已准备的运行配置

本机已有 `.idea/runConfigurations/Backend_Local.xml`，显示名称为 **Backend Local - JAR**。它使用现有 Java 21 和已打包的程序，数据库连接及签名密钥也已从本机配置填入。此文件包含本机凭据，已被 Git 忽略并限制文件访问权限。

1. 按 `Win + E` 打开文件资源管理器，地址栏输入 `D:\ProgramData\android_projects\Server\scripts`。
2. 右键 `dev-db.ps1`，选择“使用 PowerShell 运行”。Windows 11如果未直接显示此菜单，先点“显示更多选项”。等待启动窗口结束。若出现执行策略确认，确认目标是本项目的脚本后按提示允许本次运行。
3. 打开 IntelliJ IDEA，点击 **File → Open**（文件→打开），选择 `D:\ProgramData\android_projects\Server`。已打开这个项目则跳过。
4. 在 IDEA 顶部运行配置下拉框中选择 **Backend Local - JAR**。也可点 **Run → Run…**（运行→运行…），在列表中选择它。刚添加的配置若未出现，关闭并重新打开项目。
5. 点击配置旁的绿色三角形 **▶ Run**。底部 **Run** 窗口会显示启动日志。
6. 出现 `Started ServerApplication` 和 Tomcat 8080 的启动信息后，浏览器打开 `http://127.0.0.1:8080/api/v1/system/health`，返回 `"status":"UP"` 即成功。
7. 以后停止此进程时，点击 IDEA **Run** 窗口里的红色方块 **■ Stop**。

调试接口时，浏览器打开 **http://127.0.0.1:8080/swagger-ui/index.html**。展开接口后点击 **Try it out → Execute**；登录和授权步骤见 [Swagger 页面说明](swagger-ui.md)。

每次重启电脑后先做第1—2步启动项目数据库，再在IDEA运行。数据库使用3307端口，是本项目独立实例；Windows服务里的原3306实例不能替代它。日常运行无需打开MySQL Workbench。

此配置运行的是已打包JAR。修改Java代码后，需要先重新打包：在IDEA右侧点 **Maven → followup-server → Lifecycle → package**，双击 `package`，看到 **BUILD SUCCESS** 后再运行 **Backend Local - JAR**。若右侧没有Maven窗口，可用 **View → Tool Windows → Maven** 打开。首次使用Maven构建前，在 **File → Project Structure → Project** 中把SDK选为21；若没有21，选择 **Add SDK → JDK**，指定已有路径 `D:\搜狗高速下载\IntelliJ IDEA 2025.1.3\jbr`。

## 不打开IDEA：文件资源管理器启动

在 `scripts` 文件夹中，先右键 `dev-db.ps1` →“使用 PowerShell 运行”，再右键 `server.ps1` →“使用 PowerShell 运行”。程序在后台运行，浏览器访问相同健康检查地址。日志文件是 `.codex-build/server.stdout.log`，可用记事本打开。

两种方式选一种运行即可。如果后台脚本版本已占用8080，再在IDEA点击Run会报端口占用。用IDEA运行时，先停止脚本启动的实例；本次已为切换到IDEA停止后台验证实例。

## 使用Workbench查看数据库（可选）

1. 打开 **MySQL Workbench**。
2. 在首页 **MySQL Connections** 旁点 **＋**。
3. Connection Name填 `Followup Local`；Connection Method选 **Standard (TCP/IP)**。
4. Hostname填 `127.0.0.1`，Port填 `3307`，Username填 `derm_app`，Default Schema填 `derm_followup`。
5. 用IDEA或记事本打开 `scripts/local.ps1`，找到 `DB_PASSWORD`，复制其单引号内部的密码。它是数据库密码，与API账号的 `BOOTSTRAP_PASSWORD` 不同。
6. 回到Workbench，点 **Store in Vault…** 填密码，再点 **Test Connection**。连接成功后点 **OK**。
7. 双击首页的新连接，在左侧 **Schemas → derm_followup → Tables** 展开表；右键某张表，选择 **Select Rows - Limit 1000** 查看记录。

Workbench用于查看和管理数据库；IDEA用于运行Java后端和看日志。当前没有供患者/医院操作的网页管理界面。

IDEA的JAR运行配置字段可参考：[JetBrains官方说明](https://www.jetbrains.com/help/idea/run-debug-configuration-jar.html)。
