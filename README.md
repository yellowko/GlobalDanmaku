GlobalDanmaku
==================

# 功能介绍
可以实现覆盖于所有应用之上的全局弹幕。2020年过年花了十几天写的，修了修开源出来。

# 使用方法
~~还没修好，从b站app分享打开本app，可自动捕获av/BV号或者直播间号~~
也可手动输入直播间号或者视频av/BV号

打开开关后会自动判断输入类型

直播间会抓取实时弹幕，
视频会打开内建播放器和弹幕
观看视频需要先添加到视频列表，仅支持b站av/BV开头视频
由于未重构接口仅当输入栏有av/BV开头的视频才能开启视频观看

# 演示
直播间和视频的弹幕效果如下：

<img src=./pic/streamDanmaku.jpg width=400/>
<img src=./pic/videoDanmaku.jpg width=400/>

# 参考

基于[DanmakuFlameMaster](https://github.com/bilibili/DanmakuFlameMaster)的弹幕渲染引擎，参考了[TestForFloatingWindow](https://github.com/dongzhong/TestForFloatingWindow)的悬浮窗设计。

在我本地还发现了[DanmuDemo-](https://github.com/wangpeiyuan)和[WebSocketClient](https://github.com/yangxch)这两个项目，代码里是有参考的，可惜这两个项目已经被原作者删除了，只能留一个他们GitHub主页的地址了。

API接口参考文章
https://blog.csdn.net/xfgryujk/article/details/80306776
https://www.bilibili.com/read/cv13410251/
https://www.bilibili.com/read/cv14101053/