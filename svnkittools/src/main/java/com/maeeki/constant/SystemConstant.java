package com.maeeki.constant;

public class SystemConstant {
    //config.properties
    public final static String CONFIG_PATH = "C:/svnAutoPackage/config.properties";
    //svn配置
    public final static String SVN_URL = "svn.url";
    public final static String SVN_NAME = "svn.name";
    public final static String SVN_PASSWORD = "svn.password";
    public final static String SVN_VERSION = "svn.version";
    //服务器加载web应用的地址
    public final static String WEBAPP = "server.webapp";
    //文件输出路径
    public final static String OUTPUT_PATH = "output.path";
    //输出到的文件夹名称
    public final static String OUTPUT_DIRNAME = "output.dirname";
    //class文件在服务器下固定的存储路径
    public final static String CLASS_URL = "/WEB-INF/classes";
    //普通web工程固定的文件路径
    public final static String SRC = "src";
    public final static String WEBCONTENT = "WebContent";
    public final static String WEBROOT = "WebRoot";
    public final static String[] WEB_LIST = {SRC,WEBCONTENT,WEBROOT};
    //maven工程固定的文件路径
    public final static String MAVEN_JAVA = "src/main/java";
    public final static String MAVEN_RESOURCES = "src/main/resources";
    public final static String MAVEN_WEBAPP = "src/main/webapp";
    public final static String MAVEN_TEST_JAVA = "src/test/java";
    public final static String MAVEN_TEST_RESOURCES = "/src/test/resources";
    public final static String[] MAVEN_LIST = {MAVEN_JAVA,MAVEN_RESOURCES,MAVEN_WEBAPP,MAVEN_TEST_JAVA,MAVEN_TEST_RESOURCES};
    //后缀
    public final static String CLASS_SUFFIX = ".class";
    public final static String JAVA_SUFFIX = ".java";
    //分隔符
    public final static String PATH_SEPARATOR = "/";
}
