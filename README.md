# twid

## 这是什么？

这是一个用于下载Twitter上的图片和视频的小工具，你可以用它下载指定用户所有推文中的图片和视频。

## 如何使用

这个工具用Java语言编写，请先安装Java运行环境（Java Runtime Environment），不会的自己Baidu / Google一下，很简单，推荐Java 17或更高版本。

在使用之前，你需要准备以下几样东西：

1. 访问Twitter的Cookie，使用浏览器开发者工具（F12）获取。 
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

请到 [这里](https://github.com/lw900925/twid/releases) 下载已构建的最新版本运行。

1. 解压twid.zip文件到任意目录。
2. 编辑`/config/application.yml`文件，填入`access-token`。
3. 编辑`/config/list.txt`文件，填入要下载的推主用户名（注意是用户名，不是昵称，就是screen_name，浏览器地址栏后面那部分，比如https://twitter.com/lw900925 ，lw900925就是用户名），如果要下载多个推主，请每一行填一个。
4. Windows双击运行`start.bat`，Linux / macOS在终端下执行`start.sh`文件即可启动下载，下载的媒体文件将会放在downloads目录下。
5. 下载过程中如需停止，请按`Ctrl + C`键。
6. 工具本身支持多线程下载，下载速度取决于你的CPU性能，带宽速率以及梯子的稳定性。

## 关于抓取

1. 工具默认支持锁推用户抓取，前提是，你的账号要关注这个锁推的用户。
2. 支持增量抓取，即第一次抓取全部用户数据，之后抓取从上次抓取最后一条推文开始。如需要全量抓取，请在`cache/timeline_id.json`文件下找到指定用户并删除。


## 最后

如对工具有问题请通过邮件联系我，祝生活愉快。^_^

