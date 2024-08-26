
<div align="center">
<img src="images/logo.png" style="width: 20%" />
<h4><a href="https://github.com/gh0stkey/CaA">信息洞察，智探千方！</a></h4>
<h5>第一作者： <a href="https://github.com/gh0stkey">EvilChen</a>（中孚信息元亨实验室）, 第二作者： <a href="https://github.com/0chencc">0chencc</a>（米斯特安全团队）</h5>
</div>

## 关于CaA

**CaA**是一款网络安全（漏洞挖掘）领域下的辅助型项目，主要用于分析、拆解HTTP协议报文，提取HTTP协议报文中的参数、路径、文件、参数值等信息，并统计出现的频次，帮助用户构建出具有实战应用价值的Fuzzing字典。除此之外CaA可以生成各类HTTP请求提供给BurpSuite Intruder用于Fuzzing工作。

**CaA**的设计思想来源于Web Fuzzing技术，皆在帮助用户发现隐藏的漏洞面，通过对信息的收集分析整理，让用户真正意义上的实现**数据挖掘**。

**思路来源**:

1. [我的Web应用安全模糊测试之路](https://gh0st.cn/archives/2018-07-25/1)
2. [WebFuzzing方法和漏洞案例总结](https://gh0st.cn/archives/2019-11-11/1)

**所获荣誉**:

1. [入选2024年KCon兵器谱](https://mp.weixin.qq.com/s/H7QLItrMw-aaqL2-CAvBTg)

**注意事项**:

1. CaA采用`Montoya API`进行开发，需要满足BurpSuite版本（>=2023.12.1）才能使用。

## 使用方法

插件装载: `Extender - Extensions - Add - Select File - Next`

初次装载`CaA`会自动创建配置文件`Config.json`和数据库文件`CaA.db`：

1. Linux/Mac用户的配置文件目录：`~/.config/CaA/`
2. Windows用户的配置文件目录：`%USERPROFILE%/.config/CaA/`

除此之外，您也可以选择将配置文件存放在`CaA Jar包`的同级目录下的`/.config/CaA/`中，**以便于离线携带**。

你可以很方便的在CollectInfo中右键选择RAW、JSON、XML类型的参数值进行复制，用于对请求的测试。

<img src="images/right-click-function.png" style="width: 80%" />

### 功能说明

收集的信息类型：
1. GET、POST正常形式参数和值；
2. Cookie名和值；
3. POST（JSON、Multipart、XML）参数和值；
4. 逐层路径、文件和完整路径。

生成的Payload信息：
1. GET请求；
2. POST请求；
3. POST With JSON请求；
4. POST With XML请求；
5. POST With Multipart请求；
6. 目录逐层遍历请求。

### 界面信息

| 界面名称                  | 界面展示                                              |
| ------------------------ | ---------------------------------------------------- |
| Databoard（数据集合）     | <img src="images/databoard.png" style="width: 80%" />     |
| Config（配置管理）    | <img src="images/config.png" style="width: 80%" />    |
| Generator（字典生成） | <img src="images/generator.png" style="width: 80%" /> |
| CollectInfo（数据展示） | <img src="images/collectinfo.png" style="width: 80%" /> |

## 最后

如果你觉得CaA好用，可以打赏一下作者，给作者持续更新下去的动力！

<div align=center>
<img src="images/reward.jpeg" style="width: 30%" />
</div>