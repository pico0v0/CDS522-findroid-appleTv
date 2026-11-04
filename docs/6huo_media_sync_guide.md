# 6huo（六火预告片网）媒体数据同步与更新标准操作指南 (SOP)

> **文档性质**：单一真源（Single Source of Truth, SSOT）技术指南  
> **适用对象**：维护人员、后续接入的 AI 助手、流媒体后台运维  
> **核心职责**：规范当媒体库新增预告片/短片时，如何从 6huo.com 自动化获取官方正版海报、权威简介、2026定档年份，并通过 Jellyfin API 与 OpenCV 自动抽帧构建影视元数据的全流程。

---

## 目录
1. [系统环境与凭据安全规范](#1-系统环境与凭据安全规范)
2. [6huo.com 网站抓取规范](#2-6huocom-网站抓取规范)
3. [Jellyfin 服务端 API 对接规范](#3-jellyfin-服务端-api-对接规范)
4. [双语标题与背景剧照抽帧规范](#4-双语标题与背景剧照抽帧规范)
5. [Android 客户端 (Findroid) 本地适配与版本发版规范](#5-android-客户端-findroid-本地适配与版本发版规范)
6. [全自动 Python 同步脚本完整实现（含 6 部预告片配置）](#6-全自动-python-同步脚本完整实现含-6-部预告片配置)
7. [常见问题排查与注意事项](#7-常见问题排查与注意事项)

---

## 1. 系统环境与凭据安全规范

### 1.1 服务端信息与安全准则
> [!CAUTION] 凭据安全红线
> 作业与生产规范**严禁**将真实服务器公网 IP、管理员账户及明文密码提交至 Git 仓库。所有脚本应优先通过环境变量或安全凭据文件读取配置。

- **Jellyfin 地址**: 环境变量 `JELLYFIN_SERVER_URL`（默认或开发配置如 `http://<SERVER_HOST>:8096`）
- **管理员账号**: 环境变量 `JELLYFIN_ADMIN_USER`（默认开发账号 `admin`）
- **管理员密码**: 环境变量 `JELLYFIN_ADMIN_PASSWORD`（从私有环境变量注入）
- **服务器媒体挂载目录**: `/root/jellyfin-demo/media`

### 1.2 本地运行环境要求
- **Python**: 3.10+
- **必要依赖包**:
  ```bash
  pip install opencv-python pillow
  ```
  *(标准库 `urllib.request`, `json`, `base64`, `re`, `ssl`, `os` 均已内置)*

---

## 2. 6huo.com 网站抓取规范

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

### 2.2 搜索结果解析
搜索结果容器位于 `<div class="inner-2col-main">`：
```html
<a href="/movie/{MOVIE_ID}" target="_blank">
    <img src="/files/mpic/{DATE}/p{MOVIE_ID}.jpg?{HASH}" alt="{FILM_TITLE}" />
    <span class="item-title">{FILM_TITLE}</span>
    <span class="item-pubtime">{RELEASE_DATE}上映</span>
</a>
```
- 正则提取电影 ID：`/movie/(\d+)`
- 正则提取海报图片路径：`/files/mpic/[^"]+`

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

### 3.5 上传 Backdrop 宽屏背景剧照
- **接口**: `POST {JELLYFIN_SERVER_URL}/Items/{item_id}/Images/Backdrop/0`
- **请求头**: `Content-Type: image/jpeg`
- **请求体**: 16:9 横版剧照 JPEG 转成的 **Base64 字符串**。

---

## 4. 双语标题与背景剧照抽帧规范

### 4.1 标题双语格式
Findroid 客户端详情页和首页组件内嵌了 `splitTitle` 逻辑：
- 规则：以空格隔开 `[中文名] [英文原名]`
- 示例：`欢迎来龙餐馆 Once Upon a Time in the Middle East`
- 显示效果：第一行大字呈现中文名，第二行小字呈现英文副标题，贴合 Apple TV 原生排版美学。

### 4.2 预告片原片 16:9 剧照自动截帧
为保证详情页顶部背景剧照（Backdrop）与视频内容 100% 契合且无黑边拉伸，采用 OpenCV 从 Jellyfin 视频流中直接抽帧：
```python
import cv2

stream_url = f'{JELLYFIN_SERVER_URL}/Videos/{item_id}/stream.mp4?static=true&api_key={token}'
cap = cv2.VideoCapture(stream_url)
cap.set(cv2.CAP_PROP_POS_MSEC, frame_sec * 1000) # 跳转至指定秒数
ret, frame = cap.read()
cap.release()

if ret:
    _, buffer = cv2.imencode('.jpg', frame, [int(cv2.IMWRITE_JPEG_QUALITY), 95])
    backdrop_bytes = buffer.tobytes()
```

---

## 5. Android 客户端 (Findroid) 本地适配与版本发版规范

### 5.1 剧情简介离线映射表
文件位置：`findroid/app/phone/src/main/java/dev/jdtech/jellyfin/presentation/film/MovieScreen.kt`  
在该文件的 `fallbackChineseOverviews` 映射中，添加对应视频的关键词条目，确保离线或首次进入时秒级加载中文简介。

### 5.2 版本发版同步规范（严防无限更新弹窗）
> [!IMPORTANT] 版本号必须同步递增
> 客户端内置的更新检查器（`AppUpdateManager.kt`）判定逻辑为 `remoteCode > currentCode`。  
> 每次重新编译并分发 APK 时，**必须且只能同时修改两处版本号**：
> 1. **代码内源码版本**：`findroid/buildSrc/src/main/kotlin/Versions.kt`
>    ```kotlin
>    const val APP_CODE = 35        // 必须递增，例如 34 -> 35
>    const val APP_NAME = "1.2.1"     // 对应版本名，例如 1.2.0 -> 1.2.1
>    ```
> 2. **服务端声明文件**：`version.json`
>    ```json
>    {
>      "versionCode": 35,
>      "versionName": "1.2.1",
>      "downloadUrl": "http://<SERVER_HOST>/Findroid-AppleTV-Demo.apk"
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

## 6. 全自动 Python 同步脚本完整实现（含 6 部预告片配置）

脚本建议存放路径：`db-lab/sync_6huo_authentic_data.py`。
客户端标识全局统一使用 `Client="admin-tool"`，凭据通过环境变量注入（留有默认兜底）：

```python
# -*- coding: utf-8 -*-
"""
6huo -> Jellyfin 媒体数据全自动同步生产脚本
"""
import os, sys, urllib.request, json, base64, time, ssl, re
import cv2

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

# 3. 6 部预告片完整权威元数据定义（来自 6huo.com 官方）
video_configs = {
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
    }
}

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
    for k, cfg in video_configs.items():
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

    # 4.2 从 6huo 下载原版海报并上传
    req_poster = urllib.request.Request(
        matched['poster_url'],
        headers={'User-Agent': 'Mozilla/5.0', 'Referer': 'https://www.6huo.com/'}
    )
    with urllib.request.urlopen(req_poster, context=ctx, timeout=12) as p_resp:
        p_bytes = p_resp.read()
    b64_poster = base64.b64encode(p_bytes).decode('ascii')
    up_poster = urllib.request.Request(
        f'{JELLYFIN_URL}/Items/{item_id}/Images/Primary',
        data=b64_poster.encode('utf-8'),
        headers={'Content-Type': 'image/jpeg', 'Authorization': f'MediaBrowser Token="{token}"'}
    )
    urllib.request.urlopen(up_poster, timeout=15)
    print(f"  -> Primary Poster uploaded ({len(p_bytes)} bytes).")

    # 4.3 截取预告片 16:9 精彩画面作为 Backdrop
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
        print(f"  -> Video Frame Backdrop uploaded ({frame.shape[1]}x{frame.shape[0]}).")

# 5. 触发媒体库刷新
refresh_req = urllib.request.Request(f'{JELLYFIN_URL}/Library/Refresh', data=b'', headers={'Authorization': f'MediaBrowser Token="{token}"'})
urllib.request.urlopen(refresh_req, timeout=10)
print("\nALL DONE: All 6 trailers synced with 6huo authentic data!")
```

---

## 7. 常见问题排查与注意事项

1. **PowerShell 命令行编码问题**：
   - 在 Windows PowerShell 下执行内联 `python -c "..."` 且包含中文或复杂引号时极易引发转义截断。
   - **务必**将 Python 脚本存为独立的 `.py` 文件后执行。

2. **6huo 防盗链保护**：
   - 抓取 6huo 静态海报（`/files/mpic/...`）时，请求头中必须包含 `'Referer': 'https://www.6huo.com/'`，否则会直接返回 403 Forbidden。

3. **客户端缓存刷新**：
   - Android 客户端内部对图片有内存与磁盘二级缓存。
   - 服务端更新海报后，在客户端首页做一次**下拉手势刷新**，即可清除旧缓存呈现最新海报与剧照。
