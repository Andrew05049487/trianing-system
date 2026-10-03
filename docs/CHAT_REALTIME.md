# 真人即時聊天：部署與驗收

## 範圍與基準

- Backend `main`：`07f5e3b082c953fae5703880d4abf8b7fbac4f3f`。
- Flutter `master`：`6bafcdaecf574d7bf86b4752911222fd05f1aac1`。
- 施工開始時兩個工作樹乾淨；本次修改尚未 commit、push 或部署。
- 沿用既有 REST、MySQL 聊天資料表、HMAC 身分驗證及好友／治療師綁定。沒有 SQL Migration。
- Docker、Tailscale userspace、SOCKS5、Connector/J、`ddl-auto=validate`、Render `PORT` 均保持原設定。

## 通訊契約

同一 HTTP 服務提供原生 STOMP WebSocket `/ws/chat`，不使用 SockJS。
Flutter 從 `ApiConfig.baseUrl` 推導 URL：HTTPS → WSS、HTTP → WS，保留 host/port/base path，移除 query/fragment。
CONNECT native headers 使用 `X-User-Id`、`X-Custom-Exercise-Token`；Token 不在 URL、WebSocket HTTP header 或事件內容。

伺服器重新讀取使用者並沿用 `CustomExerciseIdentityService` 驗證 Token，建立 principal。
SUBSCRIBE 再驗證身分，只接受 `/user/queue/chat-events`；不能訂閱指定他人的 user destination、解析後的 queue 或公開 topic。
所有 STOMP SEND 都拒絕；訊息仍必須經原 REST API 寫入。

事件格式：

```json
{"type":"MESSAGE_CREATED","conversationId":"123","messageId":"456"}
```

- `CONVERSATION_CREATED`：首次建立合法聊天室。
- `MESSAGE_CREATED`：訊息與最後訊息欄位提交成功；亦表示對話列表需更新。
- `MESSAGE_READ`：實際有對方未讀訊息被標記；沒有變更不發事件，`messageId` 為 null。
- `CONNECTION_READY`：私人訂閱確實建立後，只送給該連線；ID 欄位為 null。Flutter 此時 REST 補同步，避免 CONNECTED 和 SUBSCRIBE 之間遺漏訊息。
- Flutter 可以解析 `CONVERSATION_UPDATED`，但目前服務沒有另外重複發送這種事件。

沒有聊天文字、email 或醫療資訊 DTO push。事件只使已有訂閱的相關訊息 channel、當前使用者對話列表／未讀數重新讀取；不重新抓所有 contacts 或無關聊天室。
既有取得最近 200 則、時間排序與 HTTP 狀態碼保持不變。

## 交易、安全及生命週期

REST 驗證 → MySQL 保存訊息與 conversation → transaction commit → `AFTER_COMMIT` listener → 新 readonly transaction 重驗雙方關係 → 私人事件。
rollback 不通知。Broker 發送失敗只記錄一般診斷文字，不將已提交的 REST 操作回報成失敗。
治療師必須仍與病患有效綁定；病友必須仍是好友。解除關係後，舊聊天室的讀取、傳送及已讀操作均拒絕（403），並停止這個聊天室的事件推送。

Flutter 每個 RestChatBackend 只有一條 socket／私人訂閱。既有 ChatHomeScreen 和 RemoteChatScreen 共用 backend。
無 stream 訂閱者、App 非 resumed、dispose、登出或 Session 切換時關閉 socket，取消重連／握手 timer，丟棄舊 session 的延遲結果。
背景不提供推播通知。回前景或連線恢復時 REST 補回資料；WebSocket 不通時 REST 傳送、歷史、手動刷新仍可使用。
`_RefreshChannel` 在請求進行中收到事件會設 dirty，完成後合併成下一次刷新，不再直接丟棄事件。

既有 HMAC 是 stateless：前端登出會立即停止該前端連線，但本次沒有新增伺服器端每一登入 session 的 Token 撤銷機制。
帳號存在／角色檢查在 CONNECT、SUBSCRIBE 重新執行；事件推送和 REST 重新確認聊天室關係。
請勿啟用 STOMP frame、全體 messaging TRACE 或 HTTP request body/header 的詳細 production 日誌。

## 資源、Render 與限制

- STOMP heartbeat 25 秒，僅 socket liveness，不查詢 MySQL；没有持續 REST polling。
- 最多 8 次重連：1、2、4、8、16、30、30、30 秒。20 秒握手／訂閱 readiness timeout；連線穩定 60 秒才重設重連額度，避免短連線風暴。手動刷新或 App resume 可以重新嘗試。
- 心跳 scheduler 1 thread；inbound/outbound pool 各 1–2 threads、queue 256；message 8 KiB、send buffer 32 KiB、send timeout 10 秒。
- 保留既有 512 MB 容器 JVM 預算；**尚未做 Render 記憶體／大量連線負載測試**。
- 使用記憶體 simple broker，適用目前單 instance。跨多個 instance 的使用者路由需要另行設計共享 broker；本次未導入。
- Render 支援 WebSocket Upgrade，服務停止／重啟會中斷連線；Free 的閒置與冷啟動行為不能當成永久連線保證。[官方 WebSocket 文件](https://render.com/docs/websocket)、[Free 限制](https://render.com/docs/free)。
- 同源 browser policy；原生 Flutter 不需要 wildcard Origin。未驗證 Flutter Web 跨來源部署，若需開放，應列出特定可信 origin，不使用 `*`。
- 补同步仍沿用最近 200 則上限；不新增完整歷史分頁。

## 人工部署順序（尚未執行）

1. 人工 review 前後端 diff 及測試報告；確認目標為 main/master，不更動 TV 分支。
2. 確認既有 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`TS_AUTHKEY`、HMAC 所需 `CUSTOM_EXERCISE_IDENTITY_SECRET`（Spring `custom.exercise.identity-secret`，至少 32 bytes）和 `PORT` 設定有效；本次沒有新增必填 env var。保持 `RESEARCH_COLLECTION_ENABLED=false`。
3. 由使用者自行提交、push，先部署 backend；**不執行 SQL Migration、seed 或 Hibernate update**。
4. 確认 Spring 啟動／validate、Tailscale MySQL 可達，原 REST 正常。允許 Upgrade `/ws/chat` 共用目前對外 host 和 PORT，不開放 MySQL 公網。
5. 再由使用者建置／安裝新 Flutter 手機端；API host 須與部署服務一致。
6. 依下方兩手機驗收。不要將本機 mock 或 localhost 測試當成正式驗收。

## 本機驗證（2026-10-03）

| 指令／測試 | 結果 | 證據與界線 |
|---|---|---|
| `mvn -Dtest=ChatServiceTest,ChatStompInterceptorTest,ChatRealtimeTransactionTest,ChatWebSocketIntegrationTest test` | PASS 31/31 | `target/chat-focused.log`；17 service、7 interceptor、4 transaction、3 TCP WebSocket |
| `./docs/database-rebuild/mysql/run-local.ps1 -Action FullTest`（包裝 `mvn test`） | PASS 276/276，0 skip | `target/chat-full-mysql.log`；安全本機憑證，真實 MySQL 8.4.11／validate；原 MySQL integration 21/21 |
| `mvn package -DskipTests` | PASS | `target/chat-package.log` |
| `git diff --check` | PASS | 兩個 repository，最終 console；只有 Windows autocrlf 換行提示，沒有 whitespace error |

Transaction synchronization 使用隔離 H2；localhost TCP WebSocket 使用 Spring broker，身分 repository 是 mock。
完整 suite 包含真實 MySQL REST／聊天讀寫與既有資料隔離測試，但**沒有用兩支手機連線正式 Render**，也沒有把上述分開的測試冒稱為 MySQL + WebSocket + Flutter 一體 E2E。
完整前端結果及既有失敗記錄於 Flutter `docs/CHAT_REALTIME_VALIDATION.md`。
Build/test logs 為忽略的本機產物，不提交個資／密碼。

## 兩手機必要驗收：全部 NOT TESTED

1. A/B 分別用兩個合法帳號登入（治療師 ↔ 綁定病患，再驗病患 ↔ 好友），開啟同一聊天室。
2. A 傳送 `Hello`，B 不刷新即可出現，沒有重複；B 閱讀後 A 自動出現「已讀」。
3. B 留在聊天列表時，A 發送：最後訊息、排序及未讀數更新；第三個無關帳號不可收到事件或讀取聊天室。
4. 前景閒置觀察 SQL／request 數：心跳不造成週期 DB 查詢。無訂閱、背景、登出後 socket／重連停止。
5. 斷網、切 Wi-Fi、App 背景／回前景、服務重啟後補回新訊息；反覆 resume 不重複連線。
6. 登出再切帳號不收到舊帳號資料；取消好友／解除綁定後聊天室 API 403。
7. WebSocket 失效但 REST 可達時，傳送與手動刷新仍可操作；回歸 AI Chat、既有 ZEGO 入口與手機復健。
