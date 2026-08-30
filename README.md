# 双语字典 (Bilingual Dictionary) - Android

一款专为个人日常与学习设计的**轻量级双语离线/在线混合字典**安卓应用。支持**英语、马来语 (Bahasa Melayu) 与中文 (Chinese)** 的即时秒级互译。

---

## ✨ 核心特性

1. **⚡ 智能双语自动识别 (英 / 马 $\rightarrow$ 中)**：
   - 自动检测拉丁字母输入，同时在离线英语词库与马来语词库中检索。
   - 对英马同形词（如 `air`：英语为*空气*，马来语为*水*）分块高亮展示不同语种释义。
2. **🔄 中文双向互译 (中 $\rightarrow$ 英 / 马)**：
   - 输入中文时，支持一键切换「中 $\rightarrow$ 英」或「中 $\rightarrow$ 马」精准查询。
3. **🇲🇾 内置马来语形态词缀还原引擎 (Malay Morphological Stemmer)**：
   - 自动识别并剥离常见词缀（`me-`, `mem-`, `men-`, `meng-`, `meny-`, `ber-`, `ter-`, `pe-`, `di-`, `ke-`, `-kan`, `-an`, `-i` 等）。
   - 针对鼻音变音（如 `menyapu -> sapu`, `memilih -> pilih`, `menulis -> tulis`）智能还原词根，并高亮提示词缀构成。
4. **🌐 离线优先 + 智能网络兜底 (Auto-Cache)**：
   - 内置预压缩 SQLite 数据库（收录 76,000+ 常用词条，首启解压，秒级离线查词）。
   - 离线查不到时自动/一键发起在线翻译，并将网络结果自动缓存至本地数据库，越用越丰富。
5. **🪶 极小体积与极致性能**：
   - 原生 Kotlin 极简架构，网络请求直接使用系统 `HttpURLConnection`，无冗余第三方依赖。
   - 开启 R8 代码混淆与资源缩减，单架构打包，纯代码底包仅 ~2MB。
6. **📱 便捷辅助功能**：
   - **TTS 原生语音发音**（支持英语、马来语发音）。
   - **生词本收藏**（支持星标与单项管理）。
   - **查词足迹与历史记录**。
   - **系统级划词查词 (Process Text)**：在浏览器或其他 App 中长按选中文本，直接在弹出菜单中选择「双语字典」即可快速查词。

---

## 🛠️ 项目结构

```
dictionary/
├── app/
│   ├── src/main/
│   │   ├── assets/
│   │   │   └── dictionary.db.gz       # 预压缩双语 SQLite 词库
│   │   ├── java/com/bilingual/dictionary/
│   │   │   ├── core/
│   │   │   │   └── MalayStemmer.kt    # 马来语词缀形态分析引擎
│   │   │   ├── data/
│   │   │   │   ├── db/                # SQLite 数据库管理与 DAO
│   │   │   │   ├── model/             # 数据实体
│   │   │   │   ├── network/           # 轻量网络兜底翻译服务
│   │   │   │   └── repository/        # 统一业务数据仓库
│   │   │   └── ui/                    # 界面逻辑、Adapters 与交互
│   │   └── res/                       # 界面布局、矢量图标、Material 3 主题
│   └── build.gradle.kts               # R8 优化与构建配置
├── tools/
│   ├── build_dictionary.py            # 词库生成与 Gzip 压缩脚本
│   └── curated_lexicon.py             # 精选马来语/英语双语词汇表
└── README.md
```

---

## 🚀 编译与运行

### 方式一：使用 Android Studio
1. 打开 **Android Studio**，选择 `File -> Open`，打开本项目根目录 `d:\code\dictionary`。
2. 等待 Gradle 同步完成，连接手机或启动模拟器，点击 **Run (Shift + F10)** 即可安装体验。

### 方式二：命令行打包 APK
```bash
# 生成 Debug 测试包
.\gradlew.bat assembleDebug

# 生成 Release 优化混淆包 (已配置签名)
.\gradlew.bat assembleRelease
```
产物输出位置：`app/build/outputs/apk/`

### 词库更新与二次构建
若需要扩展或重新生成词库：
```bash
python tools/build_dictionary.py
```
该脚本会自动构建紧凑带索引的 `dictionary.db` 并将其压缩更新至 `app/src/main/assets/dictionary.db.gz`。
