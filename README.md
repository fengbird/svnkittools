* 本项目为基于SVN的自动增量打包项目,支持对普通WEB工程以及MAVEN工程进行增量打包,支持MAVEN依赖模块的jar包自动复制以及pom.xml中更改项对应jar包的复制
* 功能: 
  1. 指定对应SVN提交的版本号,点击打包按钮
  2. 若需要打包多个指定的SVN版本号,则在版本一栏中写入版本号,中间用逗号隔开
  3. 若需要打包某一个范围内的版本号,则在版本一栏中写入起始和结束版本号, 中间用 ~ 号隔开
 * 运行需求:
  * 本项目为Java桌面客户端,在tag中有对应的可直接运行的jar包, 在运行时需要依赖JDK8环境,双击jar包即可运行
 * 需填写项解释
  * 启动桌面客户端后,需要填写一些选择项,下面对选择项进行一些解释说明
    1. SVN地址 在这里填写需要进行打包的项目的完整路径,若为MAVEN聚合项目,请指定到具体的项目链接
    2. SVN用户名 就写用户名咯
    3. SVN密码 就写密码
    4. 已提交的SVN版本号, 填写需要进行增量打包的版本号,具体填写方式参见**功能** 介绍
    5. 选择部署到服务器后的Web应用的路径  填写本地的,已经编译好的,可在tomcat中运行的 Web应用的完整路径
    6. 选择增量包导出的路径 填写增量包需要导出到本地的路径
    7. 增量包名称  填写增量包名称,此处强烈建议填写当前应用在SVN上对应的名称,不然一些依赖jar包将无法自动打包
    8. 导入上次配置信息  **在初次使用本工具时以上信息需要依次填写, 以后再使用时,只需点击此按钮, 就会将之前填写的信息自动填写完整**
    9. 打包  点击此按钮, 进行打包操作
