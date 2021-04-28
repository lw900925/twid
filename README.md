# twitterd

## 这是什么？

这是一个用于下载Twitter上的图片和视频的小工具，你可以用它下载指定某一个用户所有推文中的图片和视频。

## 如何使用

在使用之前，你需要准备以下几样东西：

1.访问Twitter API的Access Token，可以通过申请开发者账号获得，请确保申请的Token可以访问user_timeline接口的权限。
2.梯子工具（访问Twitter需要科学上网）。

### 从源码构建

执行如下命令构建

```bash
gradlew clean build -x test
```

构建完成后，会在build/libs目录下生成app.jar文件

### 运行

执行如下命令运行

```
java -jar app.jar --app.access-token='Twitter API Access Token' --app.screen-name='用户名'
```

请将上述命令中的参数值替换为你自己的参数。

默认下载目录与app.jar目录相同，使用用户昵称作为下载目录。

下载中如需停止，请安Ctrl + C。


## 配置项

可以通过启动命令修改配置项（参数），如`java -jar app.jar --配置名称='值'`，关于app的配置项如下：

```
# twitter API接口URL
app.base-url=https://api.twitter.com/1.1

# 代理信息（访问Twitter接口需要科学上网，请自行准备梯子，并开启代理）
app.proxy.host=127.0.0.1
app.proxy.port=7890

# 访问Twitter API的Access Token（请自己准备，注意添加前缀Bearer）
app.access-token=Bearer ACCESS_TOKEN

# Twitter用户名（注意是用户名，就是URL后面部分，如：https://twitter.com/lw900925，lw900925就是用户名）
app.screen-name=lw90925

# 每次获取推文数量（Twitter支持最大值为200，此处默认200，可以不用修改）
app.count=200

# 下载文件保存路径（默认下载到和app.jar运行目录同目录下，如需指定下载目录，运行时请修改此项）
app.media-download-path: ${user.dir}
```

## 最后

如对工具有问题请通过邮件联系我，住大家生活愉快。^_^

