# Render Docker + Tailscale userspace MySQL

## 範圍與基準

- Backend `main`，施工前 HEAD：`959e94494d460da2ddaef37537d90985fc9775ad`，工作樹乾淨。
- 保留 Maven 3.9.9 / Java 21 multi-stage 建置與現有 MySQL Connector/J。
- 不修改 Entity、Controller、業務邏輯、Flutter、Schema 或 `ddl-auto=validate`。
- 僅 Docker 執行時啟用 Tailscale；一般本機 Maven / Java 啟動不受影響。

## 啟動流程

1. 檢查必要 runtime 設定，且 JDBC 目的地須與 TCP 探測目的地一致。
2. 官方 `tailscale/tailscale:v1.102.5` 提供 CLI / daemon；Runtime 使用原 Java 21 JRE。
3. `tailscaled --tun=userspace-networking --state=mem:`，SOCKS5 僅監聽 `127.0.0.1:1055`。
4. 從 `TS_AUTHKEY` 建立 mode 600 暫存檔，CLI 使用 `--auth-key=file:...`；隨即取消環境變數，成功後刪檔。
5. 等待本機控制介面（最多 10 次，每次有 timeout），`tailscale up --timeout=45s` 外層再限制 50 秒與強制終止期限。
6. 確認 `BackendState=Running`，經 SOCKS5 探測實驗室 MySQL TCP 3306（最多 3 次，每次 6 秒外層 timeout）。
7. 確認可達後才啟動 Java。失敗只記錄明確階段／檢查方向，不印金鑰、DB 密碼、JDBC URL 或原始 provider 輸出。
8. Spring 或 daemon 結束時停止另一程序。SIGTERM/INT 轉送；清理最多等待 10 秒後強制停止。

原始 Tailscale stdout/stderr 被捨棄，避免 provider 錯誤附帶敏感資訊。需細查裝置核准、登入失敗或 ACL 時使用 Tailscale 管理控制台，不要啟用 shell `set -x` 或在 Render 印出環境變數。
TCP 探測證明路由、ACL 和 MySQL listener 可達，不代表 DB 密碼正確或 Schema validate 已通過；後者仍由 Spring 正常啟動驗證。
`--state=mem:` 不保存 node key；Render 重啟需要重新驗證。此容器不開啟 SSH、exit node 或 subnet route。

## Render runtime 設定（由使用者手動配置）

必填：

- `TS_AUTHKEY`：只放 Render Secret，不放 Docker build args、Git 或文件。適合無持久磁碟的 reusable、ephemeral、pre-authorized key；如 tailnet 使用裝置核准，需確認已核准。限制授權 tag 與到期時間。
- `DB_URL`：以下是非敏感範例（密碼與帳號不要加進 URL）：

```text
jdbc:mysql://100.94.202.58:3306/rehab_r2_validation?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8&socksProxyHost=127.0.0.1&socksProxyPort=1055&connectTimeout=10000&socketTimeout=30000
```

- `DB_USERNAME`、`DB_PASSWORD`：實驗室 MySQL 的專用應用帳號／Secret，僅該 Schema 必要 CRUD，不授予 DDL／系統庫權限。

可選：`TS_HOSTNAME`（預設 `rehabassist-render`）、`TS_DB_HOST`（預設 `100.94.202.58`）、`TS_DB_PORT`（預設 `3306`）。修改目的地時必須同步調整 `DB_URL`。
保留其他現有必要 application secrets；`RESEARCH_COLLECTION_ENABLED` 維持 `false`。`PORT` 不需改寫，仍由 Render 注入並由原 `server.port=${PORT:8080}` 使用。

Entrypoint 同時明確傳入 Hikari `data-source-properties.socksProxyHost` / `socksProxyPort`，因此 URL 不含這兩個參數也會使用 proxy。若 URL 有同名參數，必須使用上述一致值，不要指定其他 proxy 或自訂 socketFactory。
只替 Connector/J 設定 SOCKS，**不**設定 JVM 全域 `-DsocksProxyHost`，Google / Resend HTTPS 不被強制改道。

## 實驗室與 Tailnet 前置條件

- 實驗室 Tailscale node 上線，MySQL listener 接受該裝置 Tailscale interface 上的 3306。
- Tailnet ACL/grants 僅准許 Render 的 app tag 到 `100.94.202.58:3306`，不是整個網段所有服務。
- 作業系統防火牆允許上述流量，不需把 MySQL 公開到 Internet，也不需開路由器公開 3306。
- MySQL 的 `user@host` 必須接受實際 Render tailnet node 的來源；本機 `rehab_app@localhost` 不適用遠端連線。ephemeral node IP 可能更動，須由管理者選擇受限帳號 host 策略並搭配精確 tailnet ACL，不使用 root 遠端登入。
- MySQL SSL 設定由既有伺服器憑證／政策決定。不要為通過登入而關閉驗證或任意加 `allowPublicKeyRetrieval=true`；如使用 `sslMode=VERIFY_IDENTITY`，須配置與目的地匹配的有效憑證。
- 已有 R3 的 29 表與 V003 欄位必須存在。**不要重跑 V001/V002 或切回 Hibernate update。**本次沒有新增 SQL。

## 512 MB 記憶體預算

Docker 啟動固定 `-Xms32m -Xmx224m -Xss512k`，Metaspace 上限 96 MiB、code cache 32 MiB、direct memory 32 MiB，OOM 時退出。
Heap 不是全部程序記憶體：Tailscale、Java native／thread stacks、HTTP 影片緩衝等仍需要空間。這些上限是保守起點，**不是**512 MB 下所有併發負載都已驗證的保證；請在 Render 監看 RSS 與 OOM，避免展示時大量並行影片傳輸。
勿在 `JAVA_TOOL_OPTIONS` / `JDK_JAVA_OPTIONS` 中覆蓋這些限制或放 Secret；JVM 可能自動列印此類選項。

## 可重複驗證

```sh
sh -n docker/entrypoint.sh
python3 docker/test_entrypoint.py
mvn -Dtest=TailscaleDataSourceConfigurationTest test
mvn test
mvn package -DskipTests
git diff --check
```

Windows Git Bash 亦可執行 shell 語法及 Python fake transport 測試；SIGTERM 測試使用 shell 自身的 `kill`，驗證兩個 fake 子程序會被停止。這是 MSYS 驗證，仍需 Linux/container smoke test。
Fake 測試不使用真實 key，不連資料庫或 tailnet；Maven configuration 測試確認 Spring Boot 3.2 正確保留 Connector/J camelCase properties，不打開 DB 連線。
現有 `MySqlMigrationIntegrationTest` 只在明確本機 DB 環境啟用；未提供環境時列 SKIP，不可稱為本次真實 MySQL / tailnet 驗收。

具備 Docker 的環境由使用者人工執行：

```sh
docker build -t rehabassist-tailscale .
```

執行時以安全未追蹤的 env file 或平台 Secret 注入必要變數；不要把真實 key/密碼放指令列。無需 `--privileged`、`--cap-add` 或 `/dev/net/tun`。對外只需映射 HTTP port，不公開 1055。
先通過 container smoke test 再人工部署 Render：確認三個 startup 階段成功、Spring validate 通過、HTTP 正常、tailnet 上有預期 app node；再驗證登入及 CRUD。測試缺 key / 錯 key / DB 不可達時容器應有限時間失敗，不應啟動 Spring。

## 官方參考

- [Userspace networking](https://tailscale.com/docs/concepts/userspace-networking)
- [官方 image build 的 binary 路徑](https://github.com/tailscale/tailscale/blob/v1.102.5/build_docker.sh)
- [CLI auth-key file 與 timeout](https://github.com/tailscale/tailscale/blob/v1.102.5/cmd/tailscale/cli/up.go)
- [官方容器版本記錄](https://tailscale.com/changelog#2026-09-24)
- [Connector/J networking properties](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-networking.html)

## 本次實際驗證紀錄

2026-10-03，Windows / Git Bash / Java 21：

| 驗證 | 實際結果 |
| --- | --- |
| `sh -n docker/entrypoint.sh`（Git Bash） | PASS |
| `python docker/test_entrypoint.py --shell "C:\Program Files\Git\bin\bash.exe"` | 13/13 PASS；含模擬 SIGTERM、daemon crash、timeout、敏感 provider 輸出遮蔽 |
| `mvn -q -Dtest=TailscaleDataSourceConfigurationTest test` | 2/2 PASS |
| `mvn test` | BUILD SUCCESS；251 項中 231 PASS、20 SKIP、0 failure/error |
| 本次跳過項目 | 既有真實 MySQL integration tests 20 項；未提供其必要本機 DB 環境，不是新失敗 |
| `mvn package -DskipTests` | BUILD SUCCESS；JAR 55,581,787 bytes |
| `git diff --check` | PASS |
| Docker image build / Linux container smoke test | NOT RUN；目前沒有 Docker CLI/daemon |
| 實驗室 Tailscale / MySQL runtime 連線、Render 512 MB 負載 | NOT TESTED；未使用 runtime secrets 或部署 |

測試 log 留在 ignored `target/tailscale-mvn-test.log`、`target/tailscale-mvn-package.log`；Maven reports 在 `target/surefire-reports/`。
第一次 shell 測試因 Windows `C:` 被 PATH 分隔符解析而失敗，已修正測試 harness 的 cygpath 轉換，最終 13 項全通過；不是 production script 問題。
沒有新建 branch、Git add/commit/push、Render 設定變更／部署或資料庫操作。
