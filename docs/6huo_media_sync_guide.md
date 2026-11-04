# 6huo（六火预告片网）媒体数据同步与更新标准操作指南 (SOP)

> **文档性质**：单一真源（Single Source of Truth, SSOT）技术指南  
> **适用对象**：维护人员、后续接入的 AI 助手、流媒体后台运维  
> **核心职责**：规范当媒体库新增预告片/短片时，如何从 6huo.com 及官方渠道自动化获取正版海报、权威简介、定档年份，并通过 Jellyfin API 与 OpenCV 自动抽帧构建影视元数据的全流程。

---

## 目录
1. [系统环境与凭据安全规范](#1-系统环境与凭据安全规范)
2. [6huo.com 网站抓取规范与踩坑记录](#2-6huocom-网站抓取规范与踩坑记录)
3. [Jellyfin 服务端 API 对接规范](#3-jellyfin-服务端-api-对接规范)
4. [双语标题与背景剧照抽帧规范](#4-双语标题与背景剧照抽帧规范)
5. [Android 客户端 (Findroid) 本地适配与发版规范](#5-android-客户端-findroid-本地适配与发版规范)
6. [全自动 Python 同步脚本完整实现（含 10 部影视配置）](#6-全自动-python-同步脚本完整实现含-10-部影视配置)
7. [核心排错备忘录（三大实战避坑指南）](#7-核心排错备忘录三大实战避坑指南)

---

## 1. 系统环境与凭据安全规范

### 1.1 服务端信息与安全准则
> [!CAUTION] 凭据安全红线
> 作业与生产规范**严禁**将真实服务器公网 IP、管理员账户及明文密码提交至 Git 仓库。所有脚本应优先通过环境变量读取配置，严禁在公开代码或开源客户端中硬编码私有凭据。

- **Jellyfin 地址**: 环境变量 `JELLYFIN_SERVER_URL`（如 `http://<SERVER_HOST>:8096`）
- **管理员账号**: 环境变量 `JELLYFIN_ADMIN_USER`（默认开发账号 `admin`）
- **管理员密码**: 环境变量 `JELLYFIN_ADMIN_PASSWORD`（从私有环境变量注入）
- **服务器媒体挂载目录**: `/root/jellyfin-demo/media`

### 1.2 本地运行环境要求
- **Python**: 3.10+
- **必要依赖包**:
  ```bash
  pip install opencv-python pillow
  ```
  *(标准库 `urllib.request`, `json`, `base64`, `re`, `ssl`, `os`, `sys`, `io` 均已内置)*

---

## 2. 6huo.com 网站抓取规范与踩坑记录

### 2.1 搜索接口格式
6huo 网站采用 GET 表单检索：
```http
GET https://www.6huo.com/?view=search&keyword={URL_ENCODED_KEYWORD}
```
**请求头配置要求**：
```python
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://www.6huo.com/' # 必须携带，防止防盗链拦截
}
```

> [!NOTE] SSL 证书校验取舍说明
> 由于 6huo 网站部分 CDN 节点在不同系统环境的 Python 下偶发握手超时或证书链不匹配，脚本中采用 `ssl.create_default_context(); ctx.check_hostname = False; ctx.verify_mode = ssl.CERT_NONE` 临时放宽校验，以确保批量抓取的可用性与连续性。在安全敏感网络中建议配置完整的本地 CA 证书包。

### 2.2 搜索结果解析（避坑：搜索假命中）
> [!WARNING] 搜索假命中避坑
> 6huo 的搜索结果页面侧边栏包含全局《热搜榜》列表。如果直接在整个 HTML 中用正则匹配 `<li>.../movie/...</li>`，会导致无论搜索任何关键词，前 7 个结果永远是热搜榜项目（如《我们仅有一次的人生》、《生化危机：爆发夜》等）。  
> **正确做法**：必须限定提取主内容区 `<div class="inner-2col-main">` 容器内部的列表项！

### 2.3 电影详情页数据提取 (`https://www.6huo.com/movie/{MOVIE_ID}`)
| 字段名称 | 提取规则 | 示例值 |
| :--- | :--- | :--- |
| **中文片名** | `<h1 class="movie-name">(.*?)<span>` | `欢迎来龙餐馆` |
| **英文/外文名** | `<table class="tlist">` 中的 `原名` 或 `又名` | `Once Upon a Time in the Middle East` |
| **上映年份** | 正则匹配 `上映[时间日期]*[：:]\s*([0-9]{4})` | `2026` |
| **具体上映日期** | `上映[：:]\s*([0-9]{4}-[0-9]{2}-[0-9]{2})` | `2026-08-11` |
| **影片类型** | `<table class="tlist">` 中的 `类型`（滤除国家标签） | `['剧情', '喜剧', '战争']` |
| **官方剧情简介** | `id="content-summary"` 或 `<div class="movie-summary-content">` 正文 | 撤侨守护与中餐厅剧情 |
| **官方高品质海报** | `<div class="movie-title-mpic"><img src="(.*?)"` | `https://www.6huo.com/files/mpic/202401/p46634.jpg?5306` |

---

## 3. Jellyfin 服务端 API 对接规范

### 3.1 认证获取 AccessToken
- **请求地址**: `POST {JELLYFIN_SERVER_URL}/Users/AuthenticateByName`
- **请求头**: 
  - `Content-Type: application/json`
  - `Authorization: MediaBrowser Client="admin-tool", Device="pc", DeviceId="1", Version="1.0"`
- **请求体**: `{"Username": JELLYFIN_ADMIN_USER, "Pw": JELLYFIN_ADMIN_PASSWORD}`
- **返回数据**: `response["AccessToken"]`

> [!IMPORTANT]
> Jellyfin 的 `/Users/AuthenticateByName` 端点**强制校验客户端元数据**。请求头中必须显式携带 `Authorization: MediaBrowser Client="...", Device="...", DeviceId="...", Version="..."` 格式头，否则服务端会直接返回 `HTTP 400 Bad Request`（初次调用常因遗漏此 Header 导致鉴权报 400）。

后续所有 API 请求在 Header 中携带：
```python
headers = {'Authorization': f'MediaBrowser Token="{token}"'}
```

### 3.2 刷新 Jellyfin 媒体库扫描
当向服务器 `/root/jellyfin-demo/media` 上传新 MP4 文件后，必须先触发扫描以生成 Item ID：
```python
req = urllib.request.Request(
    f'{JELLYFIN_SERVER_URL}/Library/Refresh',
    data=b'',
    headers={'Authorization': f'MediaBrowser Token="{token}"'}
)
urllib.request.urlopen(req, timeout=10)
```

### 3.3 更新影视元数据
- **接口**: `POST {JELLYFIN_SERVER_URL}/Items/{item_id}`
- 先 `GET /Items/{item_id}` 获取原始 JSON，覆写以下关键属性后 POST 保存：
```python
detail['Name'] = "中文片名 EnglishTitle"        # 客户端会以空格为基准自动拆分为两行主副标题
detail['OriginalTitle'] = "EnglishTitle"
detail['ProductionYear'] = 2026                 # 必须为整数，严禁设为 0 或 1970
detail['PremiereDate'] = "2026-08-11T00:00:00.0000000Z"
detail['Overview'] = "官方纯中文剧情简介..."
detail['Genres'] = ['类型1', '类型2']
detail['OfficialRating'] = "PG-13"              # G / PG / PG-13 / 18+
```

### 3.4 上传 Primary 封面海报
- **接口**: `POST {JELLYFIN_SERVER_URL}/Items/{item_id}/Images/Primary`
- **请求头**: `Content-Type: image/jpeg`
- **请求体**: 海报文件 bytes 转成的 **Base64 字符串** (utf-8 编码 bytes)。

### 3.5 上传 Backdrop 宽屏背景剧照（避坑：先删后加）
- **删除接口**: `DELETE {JELLYFIN_SERVER_URL}/Items/{item_id}/Images/Backdrop/{index}`（必须从最大索引倒序删到 0）
- **上传接口**: `POST {JELLYFIN_SERVER_URL}/Items/{item_id}/Images/Backdrop/{index}`，`index` 从 0 递增
- **重要规范**：只覆写 `/0` 是**错误**的——历史剧照会残留在 `/1`、`/2`，客户端轮播到旧图甚至其它影片的错图。正确顺序是「先全量 DELETE，再按序号 POST」，保证剧照集合与本次抽帧结果完全一致。

---

## 4. 双语标题与背景剧照抽帧规范

### 4.1 标题双语格式
Findroid 客户端详情页和首页组件内嵌了 `splitTitle` 逻辑：
- 规则：以空格隔开 `[中文名] [英文原名]`
- 示例：`欢迎来龙餐馆 Once Upon a Time in the Middle East`
- 显示效果：第一行大字呈现中文名，第二行小字呈现英文副标题，贴合 Apple TV 原生排版美学。

### 4.2 预告片原片 16:9 剧照抽帧（服务端 ffmpeg + 内嵌字幕过滤）
OpenCV 通过 `stream.mp4` 逐秒 seek 实测**不可用**：每次 seek 都会重开 HTTP 流并重新解码，单部预告片采样 70 帧耗时超过 4 分钟。改用服务端容器内自带的 ffmpeg 直接抽帧：

```bash
# Jellyfin 容器内路径：/usr/lib/jellyfin-ffmpeg/ffmpeg
# -ss 必须放在 -i 之后（输出定位），否则只对齐关键帧，与预期时间点偏差可达 ±2s
$FF -nostdin -hide_banner -loglevel error -y \
    -i "/media/寒戰1994.mp4" -ss 66 -frames:v 1 -q:v 2 /tmp/sl/hanzhan_066.jpg < /dev/null
```

- `-nostdin` 与 `< /dev/null` 二者缺一不可：批量循环中 ffmpeg 会吃掉脚本 stdin 并停在 `Transcoding is paused. Press [u] to resume.`，导致后续帧静默丢失。
- 帧质量硬门槛（在**原始分辨率**上判定，缩到 480px 会抹掉 12–18px 高的内嵌字幕，把带字幕帧误判为干净）：
  | 判定项 | 阈值 | 处理 |
  | :--- | :--- | :--- |
  | 画面亮度均值 | < 46 | 丢弃（详情页顶部呈纯黑） |
  | 画面标准差 | < 24 | 丢弃（纯色/转场帧） |
  | 内容行占比（黑边检测） | < 62% | 丢弃 |
  | 底部 28% 白字连通域 | ≥ 8 个且成 1–2 行 | 丢弃（内嵌字幕） |
  | 中部大字号连通域 | ≥ 4 个 | 丢弃（标题卡/厂标卡） |
  | 顶部发行信息条 | — | 预告片普遍存在，无法规避，可接受 |
- 每部保留 1–3 张彼此差异明显的剧照：感知哈希（8×8 aHash）汉明距离 ≥ 14，时间点间隔 ≥ 12s。
- 极端暗片源（如《生化危机：爆发夜》全部实拍帧均值 < 20）可退化为使用片名标题卡作为唯一 Backdrop。

---

## 5. Android 客户端 (Findroid) 本地适配与发版规范

### 5.1 剧情简介离线映射表
文件位置：`findroid/app/phone/src/main/java/dev/jdtech/jellyfin/presentation/film/MovieScreen.kt`  
在该文件的 `fallbackChineseOverviews` 映射中，添加对应视频的关键词条目，确保离线或首次进入时秒级加载中文简介。

### 5.2 版本发版同步规范（严防无限更新弹窗）
> [!IMPORTANT] 版本号必须同步递增
> 客户端内置的更新检查器（`AppUpdateManager.kt`）判定逻辑为 `remoteCode > currentCode`。  
> 每次重新编译并分发 APK 时，**必须且只能同时修改两处版本号**：
> 1. **代码内源码版本**：`findroid/buildSrc/src/main/kotlin/Versions.kt`
>    ```kotlin
>    const val APP_CODE = 36        // 必须递增，例如 35 -> 36
>    const val APP_NAME = "1.2.2"     // 对应版本名，例如 1.2.1 -> 1.2.2
>    ```
> 2. **服务端声明文件**：`version.json`
>    ```json
>    {
>      "versionCode": 36,
>      "versionName": "1.2.2",
>      "downloadUrl": "/web/Findroid-AppleTV-Demo.zip"
>    }
>    ```
> **若仅修改 `version.json` 而未修改 `Versions.kt`**，客户端下载安装后本地依然是旧的 `APP_CODE`，会再次判定为可更新，导致**陷入无限弹窗提示更新**的死循环！

### 5.3 编译与产物输出
```powershell
cd findroid
.\gradlew :app:phone:assembleLibreDebug
Copy-Item "findroid\app\phone\build\outputs\apk\libre\debug\phone-libre-arm64-v8a-debug.apk" -Destination "..\Findroid-AppleTV-Demo.apk" -Force
```

---

## 6. 全自动 Python 同步脚本完整实现（含 10 部影视配置）

脚本保存路径：`db-lab/sync_6huo_authentic_data.py`。
客户端标识全局统一使用 `Client="admin-tool"`，支持：
1. **环境变量解耦**（公网 IP 与密码脱敏）；
2. **命令行参数筛选**（支持 `python sync_6huo_authentic_data.py 奥本海默` 仅同步单部）；
3. **按域名动态分发 Referer**（支持 6huo 及豆瓣防盗链）；
4. **PIL 自动格式转码**（非 JPEG 图片自动转为 RGB JPEG，防止 Jellyfin 报 418 错误）；
5. **OpenCV 视频原片抽帧上传 Backdrop**。

```python
# -*- coding: utf-8 -*-
"""
6huo -> Jellyfin 媒体数据全自动同步生产脚本
"""
import os, sys, cv2, urllib.request, json, base64, time, ssl, io
from PIL import Image

# 配置 SSL 忽略校验（解决部分 6huo 节点 TLS 握手异常）
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

# 1. 服务端配置（优先读取环境变量）
JELLYFIN_URL = os.environ.get('JELLYFIN_SERVER_URL', 'http://<SERVER_HOST>:8096')
JELLYFIN_USER = os.environ.get('JELLYFIN_ADMIN_USER', 'admin')
JELLYFIN_PWD = os.environ.get('JELLYFIN_ADMIN_PASSWORD', '<YOUR_PASSWORD>')

# 2. 登录 Jellyfin
auth_url = f'{JELLYFIN_URL}/Users/AuthenticateByName'
req = urllib.request.Request(
    auth_url,
    data=json.dumps({'Username': JELLYFIN_USER, 'Pw': JELLYFIN_PWD}).encode('utf-8'),
    headers={
        'Content-Type': 'application/json',
        'Authorization': 'MediaBrowser Client="admin-tool", Device="pc", DeviceId="1", Version="1.0"'
    }
)
resp = json.loads(urllib.request.urlopen(req, timeout=10).read().decode())
token = resp['AccessToken']
print("Jellyfin authenticated successfully.")

# 3. 10 部预告片完整权威元数据定义（来自 6huo.com 及豆瓣官方）
config = {
    '龙餐馆': {
        'name': '欢迎来龙餐馆 Once Upon a Time in the Middle East',
        'originalTitle': 'Once Upon a Time in the Middle East',
        'year': 2026,
        'premiereDate': '2026-08-11T00:00:00.0000000Z',
        'genres': ['剧情', '喜剧', '战争'],
        'officialRating': 'PG-13',
        'poster_url': 'https://www.6huo.com/files/mpic/202401/p46634.jpg?5306',
        'overview': '徐福（沈腾 饰）为养家还债远赴中东中餐馆当厨师，在当地结识了大堂经理马俊生（蒋奇明 饰）。他们以地道中国风味广招八方来客，龙餐馆声名鹊起。然而随着战争爆发，当地形势急转直下，没有人能够独善其身，徐福和同胞们在硝烟弥漫的战火中展开了一场惊心动魄的生死守护之旅。',
        'frame_sec': 25
    },
    '小黄人': {
        'name': '小黄人与大怪兽 Minions & Monsters',
        'originalTitle': 'Minions & Monsters',
        'year': 2026,
        'premiereDate': '2026-07-03T00:00:00.0000000Z',
        'genres': ['动画', '喜剧', '冒险', '科幻'],
        'officialRating': 'G',
        'poster_url': 'https://www.6huo.com/files/mpic/197001/p85495.jpg?0437',
        'overview': '为了拍摄心中理想的怪兽电影，小黄人（皮埃尔·柯芬 Pierre Coffin 配音）这群萌趣活宝四处物色演员，竟然真的集齐了各式远古怪兽，在神秘废墟中上演了一场啼笑皆非、状况百出的疯狂怪兽大乱斗！',
        'frame_sec': 30
    },
    '白衣': {
        'name': '白衣逆行者 The Resolute Healer',
        'originalTitle': 'The Resolute Healer',
        'year': 2026,
        'premiereDate': '2026-11-06T00:00:00.0000000Z',
        'genres': ['剧情', '纪实', '灾难'],
        'officialRating': 'PG',
        'poster_url': 'https://www.6huo.com/files/mpic/202401/p49247.jpg?4141',
        'overview': '未知传染病突发暴发，城市陷入重大危机。病毒肆虐、物资告急，困境不断加剧。在生死考验的紧要关头，医护人员义无反顾逆行奔赴前线，同舟共济奋力守护生命，用凡人之躯铸造坚实屏障，铭刻下惊心动魄的难忘岁月。',
        'frame_sec': 20
    },
    '奥德赛': {
        'name': '奥德赛 The Odyssey',
        'originalTitle': 'The Odyssey',
        'year': 2026,
        'premiereDate': '2026-08-14T00:00:00.0000000Z',
        'genres': ['动作', '历史', '奇幻', '冒险'],
        'officialRating': 'PG-13',
        'poster_url': 'https://www.6huo.com/files/mpic/202507/p83408.jpg?4312',
        'overview': '改编自同名荷马史诗经典，讲述传奇英雄奥德修斯（马特·达蒙 饰）在特洛伊战争结束后的十年漫长归途。面对狂怒的风暴、神秘女巫的魔法与吃人独眼巨人的致命围困，他凭着坚不可摧的毅力与智慧踏过重重险阻返乡。',
        'frame_sec': 35
    },
    '疾速追杀': {
        'name': '疾速追杀：杀神觉醒 John Wick',
        'originalTitle': 'John Wick',
        'year': 2026,
        'premiereDate': '2026-10-30T00:00:00.0000000Z',
        'genres': ['动作', '惊悚', '犯罪'],
        'officialRating': '18+',
        'poster_url': 'https://www.6huo.com/files/mpic/201409/p15789.jpg?4810',
        'overview': '经历丧妻之痛的顶级退隐杀手约翰·威克（基努·里维斯 饰），心爱的福特老爷车被地下黑帮混混盯上，亡妻留下的爱犬更惨遭杀害。彻底被点燃怒火的杀神重出江湖，孤身杀入重重包围的霓虹黑夜，展开枪枪爆头、血洗黑帮的终极复仇！',
        'frame_sec': 28
    },
    '生化危机': {
        'name': '生化危机：爆发夜 Resident Evil: Outbreak Night',
        'originalTitle': 'Resident Evil: Outbreak Night',
        'year': 2026,
        'premiereDate': '2026-11-02T00:00:00.0000000Z',
        'genres': ['科幻', '惊悚', '恐怖', '动作'],
        'officialRating': '18+',
        'poster_url': 'https://www.6huo.com/files/mpic/202609/p86795.jpg?1159',
        'overview': '浣熊市郊外深处的秘密生化实验室因违规操作意外导致致命T病毒泄漏，恐怖感染迅速蔓延，整座都市在数小时内沦为活死人游荡的死亡炼狱。各路身怀秘密的幸存者必须在黎明破晓前冲破重围，寻找最后的突围生路。',
        'frame_sec': 12
    },
    '奥本海默': {
        'name': '奥本海默 Oppenheimer',
        'originalTitle': 'Oppenheimer',
        'year': 2023,
        'premiereDate': '2023-07-21T00:00:00.0000000Z',
        'genres': ['剧情', '传记', '历史'],
        'officialRating': '18+',
        'poster_url': 'https://img3.doubanio.com/view/photo/l_ratio_poster/public/p2897212051.jpg',
        'overview': '当我们为权力金钱焦虑、兴奋与愤怒时，却根本想象不到“他们”在谈论着怎样更重要的事情。随着战争阴云笼罩世界上空，各国紧锣密鼓抓紧军事竞赛。为了抢占先机，美国陆军中将莱斯利·格罗夫斯（马特·达蒙 饰）找到量子力学与核物理学领域的扛鼎人物罗伯特·奥本海默（基里安·墨菲 饰），力荐其担任曼哈顿计划的首席科学家以及洛斯阿拉莫斯国家实验室的总负责人。',
        'frame_sec': 60
    },
    '戴帽子的猫': {
        'name': '戴帽子的猫 The Cat in the Hat',
        'originalTitle': 'The Cat in the Hat',
        'year': 2026,
        'premiereDate': '2026-11-06T00:00:00.0000000Z',
        'genres': ['喜剧', '动画', '奇幻', '冒险'],
        'officialRating': 'G',
        'poster_url': 'https://www.6huo.com/files/mpic/202507/p83210.jpg?3221',
        'overview': '戴帽子的猫（比尔·哈德尔 配音）接下了想像力与灵感研究所有限责任公司迄今最艰巨的任务：为盖比和塞巴斯蒂安这对姐弟加油打气，他们正为搬到新城镇而苦恼。',
        'frame_sec': 45
    },
    '寒戰1994': {
        'name': '寒战1994',
        'originalTitle': '寒戰1994',
        'year': 2026,
        'premiereDate': None,
        'genres': ['剧情', '动作', '犯罪'],
        'officialRating': 'PG-13',
        'poster_url': 'https://www.6huo.com/files/mpic/202506/p82988.jpg?2819',
        'overview': '2017年，李文彬（梁家辉 饰）突然失踪，与此同时，蔡元祺在英国惨遭暗杀。为此，刘杰辉（郭富城 饰）向简奥伟（周润发 饰）寻求协助，开启了一份1994年的神秘档案。',
        'frame_sec': 60
    },
    '勇敢者游戏': {
        'name': '勇敢者游戏3：失控世界 Jumanji: Open World',
        'originalTitle': 'Jumanji: Open World',
        'year': 2026,
        'premiereDate': '2026-12-25T00:00:00.0000000Z',
        'genres': ['喜剧', '动作', '奇幻', '冒险'],
        'officialRating': 'PG',
        'poster_url': 'https://www.6huo.com/files/mpic/202511/p84933.jpg?4611',
        'overview': '游戏程序崩盘破次元入侵，经典角色闯入现实，结果因卡在“试玩模式”疯狂出bug。大型野兽加各路NPC组团进城“撒野团建”，都市秒变真人冒险副本！',
        'frame_sec': 60
    }
}

# 命令行过滤：如执行 `python sync_6huo_authentic_data.py 奥本海默` 则仅同步该片
if len(sys.argv) > 1:
    config = {k: v for k, v in config.items() if k in sys.argv[1:]}

# 4. 获取服务器上已存在的媒体项并匹配
req_items = urllib.request.Request(
    f'{JELLYFIN_URL}/Items?Recursive=true&IncludeItemTypes=Movie,Video&Fields=Path,Overview',
    headers={'Authorization': f'MediaBrowser Token="{token}"'}
)
items = json.loads(urllib.request.urlopen(req_items, timeout=10).read().decode())['Items']

for it in items:
    path = it.get('Path', '')
    name = it.get('Name', '')
    item_id = it.get('Id')
    
    matched = None
    for k, cfg in config.items():
        if k in name or k in path:
            matched = cfg
            break
            
    if not matched:
        continue

    print(f"\n--> Updating [{matched['name']}] (ID: {item_id})...")
    
    # 4.1 更新元数据
    det_req = urllib.request.Request(f'{JELLYFIN_URL}/Items/{item_id}', headers={'Authorization': f'MediaBrowser Token="{token}"'})
    detail = json.loads(urllib.request.urlopen(det_req).read().decode())
    detail['Name'] = matched['name']
    detail['OriginalTitle'] = matched['originalTitle']
    detail['ProductionYear'] = matched['year']
    if matched.get('premiereDate'):
        detail['PremiereDate'] = matched['premiereDate']
    detail['Overview'] = matched['overview']
    detail['Genres'] = matched['genres']
    detail['OfficialRating'] = matched['officialRating']
    
    up_req = urllib.request.Request(
        f'{JELLYFIN_URL}/Items/{item_id}',
        data=json.dumps(detail).encode('utf-8'),
        headers={'Content-Type': 'application/json', 'Authorization': f'MediaBrowser Token="{token}"'},
        method='POST'
    )
    urllib.request.urlopen(up_req, timeout=10)
    print(f"  -> Metadata updated (Year: {matched['year']}).")

    # 4.2 从 6huo / 豆瓣下载原版海报并上传 (按域名分发 Referer，非 JPEG 自动转码)
    try:
        poster_headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
        if '6huo.com' in matched['poster_url']:
            poster_headers['Referer'] = 'https://www.6huo.com/'
        elif 'doubanio.com' in matched['poster_url']:
            poster_headers['Referer'] = 'https://movie.douban.com/'
        req_poster = urllib.request.Request(matched['poster_url'], headers=poster_headers)
        with urllib.request.urlopen(req_poster, context=ctx, timeout=12) as p_resp:
            p_bytes = p_resp.read()

        # 格式检查：若为 WebP/PNG 则转码为高品质 JPEG，避免 Jellyfin 418
        img = Image.open(io.BytesIO(p_bytes))
        if img.format != 'JPEG':
            buf = io.BytesIO()
            img.convert('RGB').save(buf, 'JPEG', quality=92)
            p_bytes = buf.getvalue()
            print(f"  -> Converted {img.format} to JPEG.")

        b64_poster = base64.b64encode(p_bytes).decode('ascii')
        up_poster = urllib.request.Request(
            f'{JELLYFIN_URL}/Items/{item_id}/Images/Primary',
            data=b64_poster.encode('utf-8'),
            headers={'Content-Type': 'image/jpeg', 'Authorization': f'MediaBrowser Token="{token}"'}
        )
        urllib.request.urlopen(up_poster, timeout=15)
        print(f"  -> Primary Poster uploaded ({len(p_bytes)} bytes).")
    except Exception as e:
        print(f"  -> Poster error: {e}")

    # 4.3 覆写 Backdrop：先倒序删除全部旧剧照，再上传本帧（多图集请改用 §4.2 服务端抽帧）
    try:
        old_tags = detail.get('BackdropImageTags') or []
        for idx in range(len(old_tags) - 1, -1, -1):
            del_req = urllib.request.Request(
                f'{JELLYFIN_URL}/Items/{item_id}/Images/Backdrop/{idx}', method='DELETE',
                headers={'Authorization': f'MediaBrowser Token="{token}"'})
            urllib.request.urlopen(del_req, timeout=15).read()

        stream_url = f'{JELLYFIN_URL}/Videos/{item_id}/stream.mp4?static=true&api_key={token}'
        cap = cv2.VideoCapture(stream_url)
        cap.set(cv2.CAP_PROP_POS_MSEC, matched['frame_sec'] * 1000)
        ret, frame = cap.read()
        cap.release()
        if ret:
            _, buffer = cv2.imencode('.jpg', frame, [int(cv2.IMWRITE_JPEG_QUALITY), 95])
            b64_backdrop = base64.b64encode(buffer).decode('ascii')
            up_backdrop = urllib.request.Request(
                f'{JELLYFIN_URL}/Items/{item_id}/Images/Backdrop/0',
                data=b64_backdrop.encode('utf-8'),
                headers={'Content-Type': 'image/jpeg', 'Authorization': f'MediaBrowser Token="{token}"'}
            )
            urllib.request.urlopen(up_backdrop, timeout=15)
            print(f"  -> Backdrop reset: dropped {len(old_tags)} old, uploaded 1 ({frame.shape[1]}x{frame.shape[0]}).")
    except Exception as e:
        print(f"  -> Backdrop error: {e}")

# 5. 触发媒体库刷新
refresh_req = urllib.request.Request(f'{JELLYFIN_URL}/Library/Refresh', data=b'', headers={'Authorization': f'MediaBrowser Token="{token}"'})
urllib.request.urlopen(refresh_req, timeout=10)
print(f"\nALL DONE: {len(config)} item(s) synced successfully!")
```

---

## 7. 核心排错备忘录（四大实战避坑指南）

### 坑一：6huo 搜索假命中与繁简体文件名分歧
- **现象**：按关键词搜索时，前几个卡片经常并非目标影片，而是固定出现侧边栏热搜榜的《我们仅有一次的人生》或《生化危机：爆发夜》。
- **避坑法**：HTML 正则提取时，**必须**限定在 `<div class="inner-2col-main">` 内部。此外，服务器上的文件名可能使用繁体（例如 `寒戰1994.mp4`），脚本匹配键（`match_key`）须与服务器真实 Path 保持一致（使用繁体 `寒戰` 匹配），而呈现给用户的展示名（`Name`）使用 6huo 的官方简体正名（`寒战1994`）。

### 坑二：豆瓣 418 拦截与非 JPEG 自动转码
- **现象**：当部分片源（如《奥本海默》）使用豆瓣 CDN 高清海报（`l_ratio_poster` / `img*.doubanio.com`）时，Jellyfin 提示 `HTTP 418 Unsupported Media Type` 导致海报上传失败。
- **根因与修复**：
  1. 豆瓣 CDN 具备防盗链，请求头中必须包含 `'Referer': 'https://movie.douban.com/'`；
  2. 豆瓣某些大图实际上是 WebP 或 PNG 格式，Jellyfin 的 `/Images/Primary` 接口严格要求必须为合法的 JPEG 字节流。脚本中已集成 PIL `Image.open(io.BytesIO(bytes))` 自动检测，若格式非 JPEG 则实时无损转码为 RGB JPEG 后再做 Base64 编码上传。

### 坑三：Backdrop 多图堆叠与错图残留
- **现象**：详情页顶部轮播出现与本片无关的背景（实测《疾速追杀》挂着雪山风景、《生化危机》挂着欧洲古城，均非本片镜头），或同一张剧照重复出现 2–3 次。
- **根因**：历史版本只覆写 `/Images/Backdrop/0`，早期一次误用不带索引的 POST 又把帧追加到 `/1`、`/2`；这些残留项此后从未被清理，Jellyfin 也不会自动去重。
- **修复**：见 §3.5 的「先删后加」流程；并遵守 §4.2 的原始分辨率字幕/亮度过滤，任何未通过过滤的帧一律不上传。
- **验收**：`GET /Items?IncludeItemTypes=Movie,Video&Fields=BackdropImageTags` 统计张数，再逐张下载肉眼复核（缩略图复核无效，理由见 §4.2）。

### 坑四：`/media` 只读挂载导致 `DELETE /Items/{id}` 报 500
- **现象**：调用 `DELETE /Items/{itemId}` 返回 `HTTP 500 Error processing request`，容器日志给出真因：
  `Read-only file system : '/media/xxx.mp4'` 或 `Access to the path '/media/xxx' is denied`。
- **根因**：生产容器仅 `/config` 为读写挂载，`/media` 以 `ro` 方式挂载（容器内 `cat /proc/mounts` 可见 `/dev/vda2 /media ext4 ro,relatime`）。
  Jellyfin 删除条目时会连带尝试删除真实文件，写操作被只读挂载拒绝，整个事务回滚，条目留在库里。
- **正确流程（先移文件、再删条目）**：
  1. 宿主机把媒体移出扫描目录（建议先移入回收目录，保留一次反悔机会）：
     `mkdir -p /root/jellyfin-demo/trash-<date> && cd /root/jellyfin-demo/media && mv '<file|folder>' ../trash-<date>/`
  2. `POST /Library/Refresh`，等待扫描完成（此时条目的 `ImageTags`/`BackdropImageTags` 已被清空）。
  3. 再调 `DELETE /Items/{itemId}`，返回 `204`，条目与 `/config/metadata/library/<xx>/<id>/` 一并清除。
  4. 验收：`GET /Items?Recursive=true&IncludeItemTypes=Movie,Video` 计数，并确认宿主机 `ls /root/jellyfin-demo/media | wc -l` 与条目数一致。
- **注意**：只读挂载同样意味着任何「由服务端写回 `/media`」的方案（转码产物、字幕下载、API 上传原片）在本环境不可用；新增影片一律走宿主机 `scp`/`mv` 落盘后再刷新。
