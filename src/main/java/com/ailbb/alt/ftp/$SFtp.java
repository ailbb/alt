package com.ailbb.alt.ftp;

import com.ailbb.ajj.entity.$Result;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.*;
import java.net.InetAddress;
import java.util.Properties;

import static com.ailbb.ajj.$.*;

/**
 * Created by Wz on 6/20/2018.
 */
public class $SFtp {
    private InetAddress host;
    private String ip = "127.0.0.1";
    private int port = 22;
    private String username = "root";
    private String password = "123456";

    private long connectTimeOut = 1000 * 60 * 60; // 默认1小时超时时间

    private static final int SFTP_DEFAULT_TIMEOUT=3600;//连接服务器的超时时间

    private Session session = null;
    private ChannelSftp channel = null;

    private boolean isLogin = false;

    public $SFtp(String ip, String username, String password) {
        this.ip = ip;
        this.username = username;
        this.password = password;
    }

    public $SFtp(InetAddress host, String username, String password) {
        this.host = host;
        this.username = username;
        this.password = password;
    }

    public boolean login() {
        if(isLogin) return isLogin;

        // 默认的端口22
        JSch jsch = new JSch();
        // 按照用户名,主机ip,端口获取一个Session对象
        try {
            info("登陆FTP服务器:" + username + "@" + ip);
            session = jsch.getSession(username, ip, port);
            System.out.println("Session created.");

            session.setPassword(password); // 设置密码

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("UseDNS", "no");

            session.setConfig(config); // 为Session对象设置properties
            session.setTimeout(SFTP_DEFAULT_TIMEOUT); // 设置timeout时候
            session.connect();

            channel = (ChannelSftp) session.openChannel("sftp"); // 打开SFTP通道
            channel.connect(); // 建树SFTP通道的连接

            isLogin = true;
        } catch (JSchException e) {
            isLogin = false;
            error("登陆服务器失败：" + username + "@" + ip, e);
        } finally {
            if(isLogin) async(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(connectTimeOut);
                        logout();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        return isLogin;
    }

    /**
     * 退出/关闭服务器连接
     * @return
     */
    public $Result logout(){
        $Result ftpResult = new $Result();

        try {
            if (channel != null) channel.disconnect();
        } catch (Exception e) {
            warn(ftpResult.setSuccess(false).addError("关闭channel异常：" + ip).getError(), e);
        }

        try {
            if (session != null) session.disconnect();
        } catch (Exception e) {
            warn(ftpResult.setSuccess(false).addError("关闭session异常：" + ip).getError(), e);
        }

        info(ftpResult.addMessage("成功断开服务器连接：" + ip).getMessage());

        return ftpResult;
    }

    /**
     * @param localFile /home/upload/a.file
     * @param remotePath /home/upload/
     * @return boolean
     */
    public boolean uploadFile(File localFile, String remotePath) {
        if(!login()) return false;

        boolean success = true;
        String fileName = localFile.getName();

        InputStream input = null;
        try {
            input = new FileInputStream(localFile);
            // 改变当前路径到指定路径
            info(concat("上传文件：", fileName, " >>>>>>> ", remotePath));
            channel.cd(remotePath);
            channel.put(input, fileName);
        } catch (Exception e) {
            success = false;
            error("ftp上传文件失败", e);
        } finally {
            try {
                if(input != null) input.close();
            } catch (IOException e) {
                error("ftp上传文件关闭IO流失败", e);
            }
        }

        return success;
    }

    /**
     *
     * @param remotePath 下载文件路径:/home/download/a.file
     * @param savePath 保存文件地址: /home/download/a.file
     * @return
     */
    public boolean downloadFile(String savePath, String remotePath){
        if(!login()) return false;

        boolean success = true;
        OutputStream output = null;
        File file = null;

        try {
            file = getFile(savePath);
            if(!file.exists()) file.createNewFile();

            output = new FileOutputStream(file);

            String path = remotePath.substring(0, remotePath.lastIndexOf("/"));
            String fileName = remotePath.substring(remotePath.lastIndexOf("/") + 1);

            channel.cd(path);
            channel.get(fileName, output);

            success = true;
        } catch (Exception e) {
            success = false;
            exception(e);
        } finally {
            try {
                if (output != null) output.close();
                if (file != null) file.delete();
            } catch (IOException e) {
                error("ftp下载文件关闭IO流失败", e);
            }
        }

        return success;
    }

    public InetAddress getHost() {
        return host;
    }

    public $SFtp setHost(InetAddress host) {
        host = host;
        return this;
    }

    public String getIp() {
        return ip;
    }

    public $SFtp setIp(String ip) {
        ip = ip;
        return this;
    }

    public int getPort() {
        return port;
    }

    public $SFtp setPort(int port) {
        port = port;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public $SFtp setUsername(String username) {
        username = username;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public $SFtp setPassword(String password) {
        password = password;
        return this;
    }

    public long getConnectTimeOut() {
        return connectTimeOut;
    }

    public $SFtp setConnectTimeOut(long connectTimeOut) {
        connectTimeOut = connectTimeOut;
        return this;
    }

    @Override
    public String toString() {
        return "$SFtp{" +
                "host=" + host +
                ", ip='" + ip + '\'' +
                ", port=" + port +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", connectTimeOut=" + connectTimeOut +
                '}';
    }
}
