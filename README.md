# twitterd

## 这是什么？

这是一个用于下载Twitter上的图片和视频的小工具，你可以用它下载指定用户所有推文中的图片和视频。

## 如何使用

这个工具用Java语言编写，请先安装Java运行环境（Java Runtime Environment），不会的自己Baidu / Google一下，很简单。

在使用之前，你需要准备以下几样东西：

1. 访问Twitter API的Access Token（可以用自己账号的Token，PC浏览器登录Twitter后按F12键，刷新一下页面，左侧栏出现请求，随便点开一个请求，可以看到请求头部Authorization，将里面的Beare Token拷贝出来）。
2. 梯子工具（访问Twitter需要科学上网）。

### 1.从源码构建

执行如下命令构建

Windows

```bash
gradlew.bat clean build -x test
```

Linux / macOS

```bash
gradlew clean build -x test
```

构建完成后，会在build/libs目录下生成app.jar文件，执行下面命令启动。

```bash
java -jar app.jar
```

### 2.下载后运行（推荐）

请到 [这里](https://github.com/lw900925/twitterd/releases) 下载已构建的最新版本运行。

下载twitterd.zip并解压到任意目录，目录文件说明：

```text
/config 配置文件目录
/config/application.yml 一些功能的相关配置，详情参见附录
/config/users.txt 要下载的twitter用户screen_name，如果批量下载多个用户，每行放一个

/data 下载工具运行产生的数据目录
/data/last_user_timeline_id.txt 增量抓取产生的用户timeline id映射

/downloads 媒体文件下载存放目录

app.jar 下载工具主程序文件

start.sh Linux / macOS 请在终端执行该文件

start.bat Windows用户双击运行该文件
```

1. 解压完毕后编辑`/config/application.yml`文件，填入`access-token`。
2. 编辑`/config/users.txt`文件，填入要下载的推主用户名（注意是用户名，不是昵称，就是screen_name，浏览器地址栏后面那部分，比如https://twitter.com/lw900925 ，lw900925就是用户名），如果要下载多个推主，请每一行填一个。
3. Windows双击运行`start.bat`，Linux / macOS在终端下执行`start.sh`文件即可启动下载，下载的媒体文件将会放在downloads目录下。
4. 下载过程中如需停止，请按`Ctrl + C`键。
5. 工具本身支持多线程下载，下载速度取决于你的带宽速率以及梯子的稳定性。

## 附录

`/config/application.yml`文件中的配置项说明如下，请按照需要更改。

```yml
spring:
  application:
    name: twid

logging:
  level:
    io.lw900925: debug

app:
  # twitter API接口URL
  base-url: https://api.twitter.com/1.1

  # 代理信息（访问Twitter接口需要科学上网，请自行准备梯子，并开启代理）
  proxy:
    host: 127.0.0.1
    port: 7890
    
  # 访问Twitter API的Access Token（请自己准备，注意添加前缀Bearer）
  access-token: Bearer XXX

  # 每次获取推文数量（Twitter支持最大值为200，此处默认200，可以不用修改）
  count: 200
  
  # 是否增量抓取（即每次抓取当前用户后记下该用户最新推文ID，下次从该推文开始抓取）
  increment: true
  
  # 要抓取的用户列表，每一行单独放置一个用户名
  users-file-path: ${user.dir}/config/users.txt
  
  # 媒体文件下载目录，默认为downloads目录
  media-download-path: ${user.dir}/downloads
```



## 最后

如对工具有问题请通过邮件联系我，祝大家生活愉快。^_^

