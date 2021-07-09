# twid

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

请到 [这里](https://github.com/lw900925/twid/releases) 下载已构建的最新版本运行。

1. 解压twid.zip文件到任意目录。
2. 编辑`/config/application.yml`文件，填入`access-token`。
3. 编辑`/config/users.txt`文件，填入要下载的推主用户名（注意是用户名，不是昵称，就是screen_name，浏览器地址栏后面那部分，比如https://twitter.com/lw900925 ，lw900925就是用户名），如果要下载多个推主，请每一行填一个。
4. Windows双击运行`start.bat`，Linux / macOS在终端下执行`start.sh`文件即可启动下载，下载的媒体文件将会放在downloads目录下。
5. 下载过程中如需停止，请按`Ctrl + C`键。
6. 工具本身支持多线程下载，下载速度取决于你的带宽速率以及梯子的稳定性。

## 最后

如对工具有问题请通过邮件联系我，祝生活愉快。^_^

