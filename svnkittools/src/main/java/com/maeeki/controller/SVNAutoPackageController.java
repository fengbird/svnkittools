package com.maeeki.controller;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import cn.hutool.core.io.FileUtil;
import cn.hutool.setting.dialect.Props;
import com.maeeki.constant.SystemConstant;
import lombok.extern.slf4j.Slf4j;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

@Slf4j
public class SVNAutoPackageController {
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

    public void excutePackage() throws SVNException{
        //初始化库。 必须先执行此操作。具体操作封装在setupLibrary方法中。
        setupLibrary();
        String finalDestUrl = outputPath + outputDirname;
        SVNRepository repository = getSvnRepository();
        //核心代码,根据svn库及版本号获取当前库当前版本存在变更的文件列表
        List<Map<String, Object>> repoInfos = getRepoInfoByRevision(repository, version);
        List<String> allUrlList = new ArrayList<>();
        repoInfos.forEach(repoInfo -> {
            //拼接出的文件父路径
            List<String> urlList = (List<String>) repoInfo.get("urlList");
            allUrlList.addAll(urlList);
        });

        //处理代码的核心逻辑
        allUrlList.forEach(svnFileUrl -> handleCopy(finalDestUrl, svnFileUrl));
        summaryFiles(finalDestUrl, repoInfos);
        log.info("文件全部复制完毕!增量包输出位置:"+finalDestUrl);
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
        }else if(!revisions.contains("~")){
            long revision = Long.parseLong(revisions);
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

}
