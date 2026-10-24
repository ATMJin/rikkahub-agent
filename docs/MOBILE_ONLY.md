# 我的手機 Agent：精簡版

2026-10-24。此分支是 ExTV/rikkahub-agent 的 AGPL-3.0 衍生版本。

## 建置邊界

預設只包含四個模組：`:app`（目錄 `mobile-app/`）、`:ai`、`:local-llm`、`:common`。
舊 `app/` 及其餘上游模組不再加入 Gradle 專案、不編譯、不進 APK。原始碼保留供歷史
追溯；選用的 Codex 登入／Provider 程式及帳號單元測試移至 `mobile-app/`，登入協定未改寫。

移除的功能／依賴：llama.cpp、Linux 工作區／終端機、Web 管理介面、Telegram、SSH、
MCP、技能／工作流程、排程、檔案管理、影片生成、完整語音模組、QuickJS、浮動視窗及 AICore。
不會載入舊 Application／服務／工具註冊表，不能用隱藏設定把上述功能重新開啟。
舊原始碼和已刪除的檔案可由 Git 歷史恢復。

Windows 保留現有 `cmd.exe` RUNASADMIN 設定。建置直接呼叫 Java Wrapper，不使用 `.bat`、
CMake、Ninja、bun 或 pnpm。LiteRT 的原生程式碼來自現成 Android 執行庫，不在此機編譯。

```powershell
./scripts/build-mobile.ps1
```

輸出：`mobile-app/build/outputs/apk/debug/app-debug.apk`。
套件：`tw.littleyi.mobileagent`，Android 12+、arm64，與舊 LittleYi／RikkaHub 分開安裝，不覆寫原 App。

## 保留的十個工具

直接執行（仍需解鎖）：`list_apps`、`launch_app`、`open_settings`、`read_settings`、
`read_notifications`、`read_network_state`。

逐次確認：`set_brightness`、`set_volume`、`set_wifi_enabled`、`set_airplane_mode`。
僅調整媒體音量；設定頁可開啟 Wi-Fi、飛航、藍牙、顯示、音效、通知權限與一般設定。
沒有傳訊、刪檔、購買、任意 Activity／URI、畫面座標點擊或任意 Shell 工具。

通知只有明確按「查詢目前通知並摘要」，或勾選本回合授權，才會提供給模型。
未授權時，不僅隱藏宣告，執行層也拒絕該工具。最多讀取 30 則目前通知，不讀歷史資料庫，
不背景轉送；本回合通知本文可交給已選線上模型處理。聊天與通知結果只在記憶體中，未寫入歷史檔。

確認綁定該次工具及參數，60 秒到期、離開確認畫面或鎖定會拒絕。沒有 YOLO／常駐授權。
工具參數嚴格驗證；每回合最多 8 輪推論、每輪最多 8 個工具；不自動重試系統操作。
App 視窗使用 FLAG_SECURE，確認按鈕拒絕被其他視窗遮蔽的點擊。這不是對已遭入侵系統或所有
第三方無障礙服務的完整防護，仍需實機驗證。

## 網路修復是離線核心功能

- E2B 可呼叫 Wi-Fi／飛航工具，不需要線上 API、電腦、root、Shizuku 或 Termux。
- 另有四個固定本機按鈕，模型未匯入或網路狀態不明時也能啟用／停用 Wi-Fi、飛航模式。
  固定按鈕經過相同參數驗證與逐次確認，不繞過安全機制。
- 切換透過受限無障礙服務，只讀 `com.android.settings` 的設定畫面，只辨識已知中文／英文
  標籤的可見 Switch；不輸入文字、不執行模型提供的任意點擊。至多點一次，並檢查實際開關狀態。
  多個相似開關、OEM 畫面不符或狀態未確認時停止，不再盲點，也不代按額外系統確認框。
- **SSID 選擇、輸入 Wi-Fi 密碼／新增網路目前仍需在系統頁手動完成。** 無障礙頁面辨識
  尚未在 Zenfone 12 實測；不能把編譯成功當成這兩個開關已實測成功。
- 每次推論前依當下系統網路證據重新選模型：驗證可連網時只用選定線上模型；確認斷網時只用 E2B。
  API 401／429／5xx／逾時不視為斷網、不切換 Provider。恢復網路後下一次推論回到線上模型。
- Captive portal／網路正在驗證等 `UNVERIFIED` 狀態會停止模型推論，不擅自判斷離線；此時固定
  網路修復按鈕仍可用。網路切換中的既有請求不遷移、不重播。

## 手機設定與驗證順序

1. 安裝開發 APK；開 App 設定線上 Provider。可使用上游 Codex 登入，或填入 OpenRouter／
   OpenAI 相容 HTTPS API URL、Key 和明確的 Model ID。模型清單由使用者選擇，不自動挑另一個付費 Provider。
2. API Key 經 Android Keystore 加密，登入憑證沿用上游加密儲存；關閉系統備份。沒有在本次建置中讀取
   使用者 Token 或執行付費推論。登出按鈕清除本地帳號，不聲稱已遠端撤銷。
3. 由手機檔案選擇器匯入 `gemma-4-E2B-it.litertlm`，權重不隨 APK 打包、不從 PokeClaw 私有目錄擅自複製。
   保留檔案暫存、完整複製後才登記。檔案大小檢查不等於來源／內容完整性驗證；實際載入與 GPU 健康需實測。
4. 視需求授予通知存取、「修改系統設定」（亮度）、以及「我的手機 Agent：網路開關」無障礙權限。
   不要求 SMS、聯絡人、相機、麥克風、定位、外部儲存總存取或全部 App 清單權限。
5. 先用固定按鈕驗證開關：拒絕應無變化、接受只切一次，無法辨識不亂點。驗證後再測離線 E2B 語意指令。
6. 測試飛航開啟 → E2B、關閉飛航並恢復網路 → 原線上模型；也測 Wi-Fi 接路由器但 WAN 斷線時
   的 UNVERIFIED 提示和本機恢復按鈕。此階段不會自動開關使用者手機網路。
7. 測試鎖定後不讀通知、不推論；通知授權未勾選時模型不能偷讀；401／429 不啟動 E2B。

登入與額度屬於待驗證項目：保留的是上游既有整合，不等於完成官方新的 Sign in with ChatGPT
第三方註冊流程，也不保證特定帳號／模型可用。官方說明：
https://developers.openai.com/siwc/token-sharing-open-source
必須以使用者授權登入後的實際推論成功確認，不能僅以模型清單或登入成功判定額度可用。

## 後續仍保留的需求

聲紋註冊、「Hey，我的手機」喚醒、繁中 ASR、關屏／Doze 喚醒尚未實作，未移除需求。
`SpeechOutput` 只是可選 TTS 接口，目前不載入 TTS 服務。任意 App 內多步操作、傳訊等能力
不在此精簡開發版，之後加入時仍須維持解鎖及高風險逐次確認政策。

## 驗證紀錄

2026-10-24，Windows 本機實際結果：

- `:app:testDebugUnitTest :ai:testDebugUnitTest :local-llm:testDebugUnitTest :common:testDebugUnitTest :app:assembleDebug` 成功。
- App 16、AI 319、LiteRT／本地模型 124、共用程式 5，共 **464 項測試，零失敗／錯誤**。
- 修正一項上游 ModelInstallTest 的 Unix 路徑假設，改檢查目錄與檔名；未放寬產品的路徑驗證。
- APK 75,458,921 bytes，僅 `arm64-v8a`。原生庫只有 LiteRT、AndroidX graphics path、DataStore 計數器。
- APK SHA-256：`388D8BF25FEDC48AC5C03F13C9E386DEBB65E1BCA6F324314BEE2E8A005269BC`。
- APK 權限檢查只有 Internet、網路／Wi-Fi 狀態、媒體音量、WRITE_SETTINGS，以及 AndroidX 內部接收器權限。
  通知／網路無障礙服務各自受 Android 綁定權限保護，仍須使用者在系統設定啟用。
- `:app:lintDebug` 通過：0 errors、80 warnings（含依賴版本及 UI 字串國際化提醒，並非零警告）；DEX 檢查沒有工作區、llama.cpp、QuickJS、
  舊 Web 或舊工具註冊表的套件參照。
- 不包含模型權重，沒有安裝到手機、切換實際網路、登入使用者帳號或執行線上推論。

未安裝實測的功能不可標為已通過；上述結果只證明建置、單元測試與 APK 靜態檢查。
