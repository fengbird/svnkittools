package com.maeeki.controller;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.setting.dialect.Props;
import com.maeeki.constant.SystemConstant;
import com.sun.deploy.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc2.ng.SvnDiffGenerator;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc2.SvnDiff;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

@Slf4j
public class SVNAutoPackageController {
    private static long defaultRevision;
    private SVNRepository repository;
    private String svnUrl;
    private String svnName;
    private String svnPassword;
    private String webapp;
    private String outputPath;
    private String outputDirname;
    private String version;

    public SVNAutoPackageController() {
        Props props = new Props(SystemConstant.CONFIG_PATH);
        svnUrl = props.getProperty(SystemConstant.SVN_URL);
        svnName = props.getProperty(SystemConstant.SVN_NAME);
        svnPassword = props.getProperty(SystemConstant.SVN_PASSWORD);
        webapp = props.getProperty(SystemConstant.WEBAPP);
        outputPath = props.getProperty(SystemConstant.OUTPUT_PATH);
        outputDirname = props.getProperty(SystemConstant.OUTPUT_DIRNAME);
        version = "".equals(props.getProperty(SystemConstant.SVN_VERSION)) ? null
                : props.getProperty(SystemConstant.SVN_VERSION);
    }

    public void excutePackage() throws Exception{
        //初始化库。 必须先执行此操作。具体操作封装在setupLibrary方法中。
        setupLibrary();
        String finalDestUrl = outputPath + outputDirname;
        repository = getSvnRepository();
        //核心代码,根据svn库及版本号获取当前库当前版本存在变更的文件列表
        List<Map<String, Object>> repoInfos = getRepoInfoByRevision(repository, version);
        List<String> allUrlList = new ArrayList<>();
        repoInfos.forEach(repoInfo -> {
            //拼接出的文件父路径
            List<String> urlList = (List<String>) repoInfo.get("urlList");
            allUrlList.addAll(urlList);
        });

        //处理代码的核心逻辑
        allUrlList.forEach(svnFileUrl -> {
            //用于尝试复制项目之间的依赖jar包,处理状况: 依赖模块中的内容改变,pom.xml中的内容无任何变化
            if (svnFileUrl.contains(SystemConstant.SRC) && !svnFileUrl.contains(outputDirname)) {
                String tempStr = svnFileUrl.substring(0, svnFileUrl.indexOf(SystemConstant.SRC) - 1);
                String modelNameStr = tempStr.substring(tempStr.lastIndexOf('/') + 1);
                copyJarFile(finalDestUrl, modelNameStr);
            // 用于尝试复制由于pom.xml变更影响到的jar包
            } else if (svnFileUrl.contains(SystemConstant.POM_XML)){
                try {
                    //多版本version的jar包复制处理
                    if (version.contains("~")) {
                        String[] split = version.split("~");
                        long startVersion = Long.parseLong(split[0]);
                        long endVersion = Long.parseLong(split[1]);
                        for (long i = startVersion;i <= endVersion;i++) {
                            Set<String> diffDependency = getDiffDependency(svnUrl + '/' + SystemConstant.POM_XML, i);
                            if (diffDependency.isEmpty()) {
                                continue;
                            }
                            diffDependency.forEach(jarName -> copyJarFile(finalDestUrl,jarName));
                        }
                    //单版本version的jar包复制处理
                    } else {
                        getDiffDependency(svnUrl + '/' + SystemConstant.POM_XML,defaultRevision)
                                .forEach(jarName -> copyJarFile(finalDestUrl,jarName));
                    }
                } catch (SVNException e) {
                    log.error(e.getMessage(),e);
                }
            } else {
                handleCopy(finalDestUrl, svnFileUrl);
            }
        });
        summaryFiles(finalDestUrl, repoInfos);
        log.info("文件全部复制完毕!增量包输出位置:"+finalDestUrl);
    }

    private void copyJarFile(String finalDestUrl, String modelNameStr) {
        String libStr = webapp + SystemConstant.LIB_URL;
        File[] libFiles = FileUtil.ls(libStr);
        for (File libFile : libFiles) {
            if (libFile.getName().contains(modelNameStr)) {
                File file = new File(finalDestUrl+SystemConstant.LIB_URL,libFile.getName() );
                FileUtil.copy(libFile,file,true);
                break;
            }
        }
    }

    private void summaryFiles(String finalDestUrl, List<Map<String, Object>> repoInfos) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String time = sdf.format(new Date());
        File wrapFileDir = FileUtil.mkdir(outputPath + outputDirname + "_" + time);
        File finalDestDir = new File(finalDestUrl);
        FileUtil.copy(finalDestDir,wrapFileDir,true);
        File readMe = FileUtil.touch(wrapFileDir, "readMe.txt");
        repoInfos.forEach(repoInfo -> {
            StringBuilder sb = new StringBuilder();
            String str = "当前版本提交者:"+repoInfo.get("author")+"\r\n"
                    + "提交时间:"+repoInfo.get("date")+"\r\n"
                    + "提交信息:"+repoInfo.get("message")+"\r\n"
                    + "当前版本号:"+repoInfo.get("revision")+"\r\n"
                    + "更新文件列表如下:"+"\r\n";
            sb.append(str);
            List<String> urlList = (List<String>) repoInfo.get("urlList");
            urlList.forEach(url -> sb.append(url+"\r\n"));
            sb.append("\r\n------------------------------------\r\n");
            byte[] bytes = sb.toString().getBytes();
            FileUtil.writeBytes(bytes,readMe,0,bytes.length - 1,true);
        });
        FileUtil.del(finalDestDir);
        try {
            Runtime.getRuntime().exec("cmd /c start " + wrapFileDir);
        } catch (IOException e) {
            log.warn(e.getMessage(),e);
        }
    }

    /**
     * 处理文件复制的具体逻辑
     * @param finalDestUrl 复制出的文件最终保存的路径
     * @param svnFileUrl 从svn中读取到的完整文件路径
     */
    private void handleCopy(String finalDestUrl, String svnFileUrl) {
        if(svnFileUrl.contains(SystemConstant.SRC)){
            if(svnFileUrl.contains(SystemConstant.MAVEN_JAVA)){
                //从svn中读取的路径列表中截取java文件的类路径名
                String srcUrl = svnFileUrl.substring(svnFileUrl.indexOf(SystemConstant.MAVEN_JAVA)
                        + SystemConstant.MAVEN_JAVA.length() + 1);
                if (!srcUrl.contains(".")) {
                    return;
                }
                handleJavaCopy(finalDestUrl, srcUrl);
            } else if(svnFileUrl.contains(SystemConstant.MAVEN_WEBAPP)){
                handleWebResources(SystemConstant.MAVEN_WEBAPP,finalDestUrl, svnFileUrl);
            } else if (svnFileUrl.contains(SystemConstant.MAVEN_RESOURCES)) {
                //从svn中读取的路径列表中截取java文件的类路径名
                String srcUrl = svnFileUrl.substring(svnFileUrl.indexOf(SystemConstant.MAVEN_RESOURCES)
                        + SystemConstant.MAVEN_RESOURCES.length() + 1);
                if (!srcUrl.contains(".")) {
                    return;
                }
                File src = new File(webapp + SystemConstant.CLASS_URL, srcUrl);
                File dest = new File(finalDestUrl + SystemConstant.CLASS_URL, srcUrl);
                FileUtil.copy(src,dest,true);
                log.info("复制"+webapp + SystemConstant.CLASS_URL+SystemConstant.PATH_SEPARATOR+srcUrl+"文件完毕");
            } else {
                //从svn中读取的路径列表中截取java文件的类路径名
                String srcUrl = svnFileUrl.substring(svnFileUrl.indexOf(SystemConstant.SRC)
                        +SystemConstant.SRC.length()+1);
                if (!srcUrl.contains(".")){
                    return;
                }
                handleJavaCopy(finalDestUrl, srcUrl);
            }
        }
        if(svnFileUrl.contains(SystemConstant.WEBCONTENT)){
            handleWebResources(SystemConstant.WEBCONTENT,finalDestUrl, svnFileUrl);
        }
    }

    /**
     * 处理web资源的复制
     * @param type web资源所处的文件夹类型
     * @param finalDestUrl 目标URL
     * @param svnFileUrl svn文件url
     */
    private void handleWebResources(String type,String finalDestUrl, String svnFileUrl) {
        String webUrl = svnFileUrl.substring(svnFileUrl.indexOf(type)
                + type.length()+1);
        if(!webUrl.contains(".")){
            return;
        }
        File src = new File(webapp, webUrl);
        File dest = new File(finalDestUrl, webUrl);
        FileUtil.copy(src,dest,true);
        log.info("复制"+webapp+SystemConstant.PATH_SEPARATOR+webUrl+"文件完毕");
    }

    /**
     * 处理Java文件的复制
     * @param finalDestUrl 目标URL
     * @param srcUrl 源URL
     */
    private void handleJavaCopy(String finalDestUrl, String srcUrl) {
        //从svn中读取的路径列表中对java文件进行处理
        if(srcUrl.endsWith(SystemConstant.JAVA_SUFFIX)){
            srcUrl = srcUrl.replace(SystemConstant.JAVA_SUFFIX,SystemConstant.CLASS_SUFFIX);
            String fileParentPath = srcUrl.substring(0,srcUrl.lastIndexOf(SystemConstant.PATH_SEPARATOR));
            String className = srcUrl.substring(srcUrl.lastIndexOf(SystemConstant.PATH_SEPARATOR) + 1,srcUrl.indexOf(SystemConstant.CLASS_SUFFIX));
            File[] files = FileUtil.ls(webapp + SystemConstant.CLASS_URL + SystemConstant.PATH_SEPARATOR +fileParentPath);
            for (File file : files) {
                //获取并复制匿名内部类对象
                if (file.getName().contains(className+"$")){
                    File dest = new File(finalDestUrl +
                            SystemConstant.CLASS_URL,
                            srcUrl.replace(className,file.getName().replace(SystemConstant.CLASS_SUFFIX,"")));
                    FileUtil.copy(file,dest,true);
                    log.info("复制"+file.getName()+"文件完毕");
                }
            }

        }
        File src = new File(webapp + SystemConstant.CLASS_URL, srcUrl);
        File dest = new File(finalDestUrl + SystemConstant.CLASS_URL, srcUrl);
        FileUtil.copy(src,dest,true);
        log.info("复制"+webapp + SystemConstant.CLASS_URL+SystemConstant.PATH_SEPARATOR+srcUrl+"文件完毕");
    }

    /*
     * 初始化库
     */
    private void setupLibrary() {
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
        FSRepositoryFactory.setup();
    }

    /**
     * 根据给定的url以及name,password获取到对应的svn仓库对象
     */
    private SVNRepository getSvnRepository() throws SVNException {
        //定义svn版本库的URL。
        SVNURL repositoryURL = null;
        //定义版本库。
        SVNRepository repository = null;
        //获取SVN的URL。
        repositoryURL=SVNURL.parseURIEncoded(svnUrl);
        //根据URL实例化SVN版本库。
        repository = SVNRepositoryFactory.create(repositoryURL);

        //对版本库设置认证信息。
        ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(svnName, svnPassword);
        repository.setAuthenticationManager(authManager);
        return repository;
    }

    /**
     * 根据仓库对象及版本号,获取对应该版本的提交信息
     */
    private static List<Map<String,Object>> getRepoInfoByRevision(SVNRepository repository,String revisions) throws SVNException {
        List<SVNLogEntry> entries = new ArrayList<>();
        if (revisions == null){
            Long revision = repository.getLatestRevision();
            while (entries.isEmpty()){
                repository.log(new String[]{""},//为过滤的文件路径前缀，为空表示不进行过滤
                        entries,
                        revision,//-1代表最新的版本号，初始版本号为0
                        revision,
                        true,
                        true);
                revision--;
            }
            defaultRevision = revision;
        }else if(!revisions.contains("~")){
            long revision = Long.parseLong(revisions);
            defaultRevision = revision;
            repository.log(new String[]{""}, entries, revision, revision, true, true);
        }else if(revisions.contains("~")){
            String[] split = revisions.split("~");
            long startRevision = Long.parseLong(split[0]);
            long endRevision = Long.parseLong(split[1]);
            repository.log(new String[]{""}, entries, startRevision, endRevision, true, true);
        }

        List<Map<String, Object>> list = new ArrayList<>();
        entries.forEach(consumer -> {
            Map<String, Object> map = new HashMap<>();
            map.put("author",consumer.getAuthor());
            map.put("date", consumer.getDate());
            map.put("message",consumer.getMessage());
            map.put("revision",consumer.getRevision());
            List<String> urlList = new ArrayList<>();
            Map<String, SVNLogEntryPath> changedPaths = consumer.getChangedPaths();
            changedPaths.forEach((key,value) -> urlList.add(key));
            map.put("urlList",urlList);
            list.add(map);
        });
        return list;
    }

    /**
     * 获取pom.xml中受影响的jar包
     * @param svnUrl
     * @param version
     * @return
     * @throws SVNException
     */
    private Set<String> getDiffDependency(String svnUrl,long version) throws SVNException {
        //判断当前version是否有更新的内容
        List<SVNLogEntry> entries = new ArrayList<>();
        repository.log(new String[]{""},
                entries,
                version,
                version,
                true,
                true);
        if (entries.isEmpty()) {
            return Collections.emptySet();
        }
        SVNURL svnurl = SVNURL.parseURIEncoded(svnUrl);
        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        try {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final SvnDiffGenerator diffGenerator = new SvnDiffGenerator();
            diffGenerator.setBasePath(new File(""));
            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setSources(SvnTarget.fromURL(svnurl, SVNRevision.create(version - 1)), SvnTarget.fromURL(svnurl, SVNRevision.create(version)));
            diff.setDiffGenerator(diffGenerator);
            diff.setOutput(byteArrayOutputStream);
            diff.run();
            final String actualDiffOutput = new String(byteArrayOutputStream.toByteArray());
            String[] diffStr = actualDiffOutput.split("\n");
            //获取以 + 号开头的字符串,这些都是新增内容
            HashSet<String> acquireStr = new HashSet<>();
            for (int i =0;i< diffStr.length;i++) {
                String s = diffStr[i];
                if (s.startsWith("+") && !StrUtil.isBlank(s.replace("+",""))) {
                    //分情况获取jar包名称
                    //若 + 号所在行包含artifactId,则直接获取jar包名称
                    if (s.contains("artifactId")) {
                        deleteArtifactId(acquireStr, s);
                    }
                    //若对某个jar包的版本version进行修改,则尝试获取其名称
                    if (s.contains("version") && i - 2 >= 0 && diffStr[i - 2].contains("artifactId")) {
                        deleteArtifactId(acquireStr,diffStr[i - 2]);
                    }
                    //TODO 其他情况的判断与处理
                }
            }
            byteArrayOutputStream.close();
            return acquireStr;
        } catch (SVNException e) {
            log.error(e.getMessage(),e);
        } catch (IOException e) {
            log.error(e.getMessage(),e);
        } finally {
            svnOperationFactory.dispose();
        }
        return Collections.emptySet();
    }

    /**
     * 删除artifactId标签,获取jar包名称
     * @param acquireStr 存储jar包名称的集合
     * @param s 待处理的字符串
     */
    private void deleteArtifactId(HashSet<String> acquireStr, String s) {
        String jarName = s.replace("<artifactId>", "")
                            .replace("</artifactId>", "")
                            .trim();
        acquireStr.add(jarName);

    }

}
