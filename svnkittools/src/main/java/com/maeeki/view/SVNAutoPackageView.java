package com.maeeki.view;

import cn.hutool.setting.dialect.Props;
import com.maeeki.constant.SystemConstant;
import com.maeeki.controller.SVNAutoPackageController;
import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.tmatesoft.svn.core.SVNException;

import java.io.File;

@Slf4j
public class SVNAutoPackageView extends Application {
    private Props props = new Props(SystemConstant.CONFIG_PATH);
    @Override
    public void start(Stage primaryStage) throws Exception {
        //初始化gridPane
        GridPane gridPane = initGridPane();
        //配置GridPane生成内容
        configGridPane(primaryStage,gridPane);
        //配置primaryStage基础属性
        configPrimaryStage(primaryStage, gridPane);
    }

    private void configGridPane(Stage primaryStage,GridPane gridPane) {
        Text welcome = new Text("Web工程增量打包工具");
        welcome.setFont(Font.font("Tahoma", FontWeight.NORMAL,20));
        gridPane.add(welcome,0,0,2,1);
        Label svnAddressLabel = new Label("SVN地址:");
        gridPane.add(svnAddressLabel,0,1);
        TextField svnAddressTextField = new TextField();
        gridPane.add(svnAddressTextField,1,1);
        Label userNameLabel = new Label("SVN用户名:");
        gridPane.add(userNameLabel,0,2);
        //创建用户名的文本输入框
        TextField userNameTextField = new TextField();
        gridPane.add(userNameTextField,1,2);
        //创建密码的Label对象
        Label passwordLabel = new Label("SVN密码");
        gridPane.add(passwordLabel,0,3);
        //创建密码的输入框
        PasswordField passwordField = new PasswordField();
        gridPane.add(passwordField,1,3);
        //创建提交版本号的Label对象
        Label versionLabel = new Label("已提交的SVN版本号");
        gridPane.add(versionLabel,0,4);
        //创建提交版本号的输入框
        TextField versionField = new TextField();
        gridPane.add(versionField,1,4);
        //创建选择web应用的按钮
        Button selectWebAppPathBtn = new Button("选择部署到服务器后的Web应用的路径");
        gridPane.add(selectWebAppPathBtn,0,5,2,1);
        //创建部署到服务器后的Web应用文件夹选择对象
        DirectoryChooser webAppChooser = new DirectoryChooser();
        Label webAppPathlabel = new Label("当前web应用文件夹路径为:");
        gridPane.add(webAppPathlabel,0,6);
        //创建路径回显文本对象
        Text webAppPathText = new Text();
        gridPane.add(webAppPathText,1,6);
        //创建选择增量包输出路径的按钮
        Button exportPackageBtn = new Button("选择增量包导出的路径");
        gridPane.add(exportPackageBtn,0,7,2,1);
        //创建增量包输出路径的文件夹选择对象
        DirectoryChooser exportPackageChooser = new DirectoryChooser();
        Label exportPackagePathlabel = new Label("增量包导出文件夹路径为:");
        gridPane.add(exportPackagePathlabel,0,8);
        //创建路径回显文本对象
        Text exportPackagePathText = new Text();
        gridPane.add(exportPackagePathText,1,8);
        //增量包名称Label
        Label packageNameLabel = new Label("增量包名称:");
        gridPane.add(packageNameLabel,0,9);
        //增量包名称TextField
        TextField packageNameTextField = new TextField();
        gridPane.add(packageNameTextField,1,9);

        //添加显示上次配置信息的按钮
        Button beforeButton = new Button("导入上次配置信息");
        HBox hBox1 = new HBox();
        hBox1.setAlignment(Pos.BOTTOM_LEFT);
        hBox1.getChildren().add(beforeButton);
        gridPane.add(hBox1,0,10);

        //添加点击按钮
        Button button = new Button("打包");
        HBox hBox = new HBox();
        hBox.setAlignment(Pos.BOTTOM_RIGHT);
        hBox.getChildren().add(button);
        gridPane.add(hBox,1,10);
        //增加用于显示提示信息的文本
        final Text infoText = new Text();
        gridPane.add(infoText,1,11);
        //给点击获取上次配置信息按钮绑定事件
        beforeButton.setOnAction((final ActionEvent e) -> {
            String svnUrl = props.getProperty(SystemConstant.SVN_URL);
            svnAddressTextField.setText(svnUrl);
            String svnName = props.getProperty(SystemConstant.SVN_NAME);
            userNameTextField.setText(svnName);
            String svnPassword = props.getProperty(SystemConstant.SVN_PASSWORD);
            passwordField.setText(svnPassword);
            String webapp = props.getProperty(SystemConstant.WEBAPP);
            webAppPathText.setText(webapp);
            String outputPath = props.getProperty(SystemConstant.OUTPUT_PATH);
            exportPackagePathText.setText(outputPath);
            String outputDirname = props.getProperty(SystemConstant.OUTPUT_DIRNAME);
            packageNameTextField.setText(outputDirname);
            String version = props.getProperty(SystemConstant.SVN_VERSION);
            versionField.setText(version);
        });
        //给打包按钮绑定点击事件
        button.setOnAction((final ActionEvent e) -> {
            String svnAddress = svnAddressTextField.getText();
            String userName = userNameTextField.getText();
            String password = passwordField.getText();
            String webAppPath = webAppPathText.getText();
            String packagePath = exportPackagePathText.getText();
            String packageName = packageNameTextField.getText();
            String version = versionField.getText();
            log.info("svnAddress:"+svnAddress);
            log.info("svnAddress:"+svnAddress);
            log.info("userName:"+userName);
            log.info("password:"+password);
            log.info("webAppPath:"+webAppPath);
            log.info("packagePath:"+packagePath);
            log.info("packageName:"+packageName);
            log.info("version:"+version);
            props.setProperty(SystemConstant.SVN_URL,svnAddress);
            props.setProperty(SystemConstant.SVN_NAME,userName);
            props.setProperty(SystemConstant.SVN_PASSWORD,password);
            props.setProperty(SystemConstant.WEBAPP,webAppPath);
            if (!packagePath.endsWith("/") && !packagePath.endsWith("\\")){
                packagePath = packagePath + "\\";
            }
            props.setProperty(SystemConstant.OUTPUT_PATH,packagePath);
            props.setProperty(SystemConstant.OUTPUT_DIRNAME,packageName);
            props.setProperty(SystemConstant.SVN_VERSION,version);
            props.store(SystemConstant.CONFIG_PATH);

            try {
                new SVNAutoPackageController().excutePackage();
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "增量包打包成功");
                alert.showAndWait();
            } catch (SVNException e1) {
                log.error("SVN异常",e1);
                Alert alert = new Alert(Alert.AlertType.ERROR, e1.getErrorMessage().getFullMessage());
                alert.showAndWait();
            } catch (Exception e2){
                Alert alert = new Alert(Alert.AlertType.ERROR, e2.getMessage());
                alert.showAndWait();
            }
        });
        //给选择文件夹的按钮绑定事件
        selectWebAppPathBtn.setOnAction((final ActionEvent e) -> {
            File file = webAppChooser.showDialog(primaryStage);
            if(file == null) return;
            packageNameTextField.setText(file.getName());
            webAppPathText.setText(file.getAbsolutePath());
        });
        //给选择增量包输出路径的按钮绑定事件
        exportPackageBtn.setOnAction((final ActionEvent e) -> {
            File file = exportPackageChooser.showDialog(primaryStage);
            if (file == null) return;
            exportPackagePathText.setText(file.getAbsolutePath());
        });
    }

    private void configPrimaryStage(Stage primaryStage, GridPane gridPane) {
        primaryStage.setTitle("超方便的SVN打包工具~");
        Scene scene = new Scene(gridPane,800,500);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    //初始化gridPane
    private GridPane initGridPane() {
        GridPane gridPane = new GridPane();
        gridPane.setAlignment(Pos.CENTER);
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new Insets(25,25,25,25));
        return gridPane;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
