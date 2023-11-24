package com.ailbb.alt.ftp.impl;

import com.ailbb.ajj.entity.$ConnConfiguration;
import com.ailbb.ajj.entity.$Progress;
import com.ailbb.ajj.entity.$Result;
import com.ailbb.alt.$;
import com.ailbb.alt.exception.$LinuxException;
import com.jcraft.jsch.*;

import java.io.*;

/*
 * Created by Wz on 6/20/2018.
 */
public class $SFtpJsch implements $FtpInterface {
    public static final int $PORT = 21;
    private $ConnConfiguration connConfiguration;
    Session session ; // 连接会话
    ChannelSftp sftpChannel; // 传输通道
    private boolean login = false;

    SftpProgressMonitor sftpProgressMonitor;

    $Progress progress = new $Progress();
    long transferredBytes = 0;
    long totalBytes = 0;

    $Result result = $.result();

    public $SFtpJsch(){}

    public $SFtpJsch($ConnConfiguration connConfiguration) {
        this.setConnConfiguration(connConfiguration);
    }

    public $SFtpJsch(String host, int port, String userName, String pwd) {
        this(new $ConnConfiguration()
                .setIp(host)
                .setPort(port)
                .setUsername(userName)
                .setPassword(pwd)
        );
    }

    @Override
    public $FtpInterface connect() throws Exception { return login(); }

    /*
     * 退出/关闭服务器连接
     */
    @Override
    public $FtpInterface disconnect()  { return this.logout(); }

    @Override
    public boolean isConnected()  { return isLogin(); }

    public $FtpInterface login($ConnConfiguration connConfiguration) throws Exception { // 需要重新实例化出来一个
        $SFtpJsch ftp = new $SFtpJsch(connConfiguration);
        return ftp.login();
    }

    private $FtpInterface login() throws Exception {
        try {
            if($.isEmptyOrNull(connConfiguration)) throw new $LinuxException.$ConnectErrorException("没有连接信息!");

            if(isConnected()) return this;

            JSch jsch = new JSch();

            session = jsch.getSession(connConfiguration.getUsername(), connConfiguration.getIp(), connConfiguration.getPort());
            session.setPassword(connConfiguration.getPassword());
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            $.debug("["+connConfiguration.getIp()+"]，成功连接......");
        } catch (JSchException e) {
            if(connConfiguration.getRetryTimes()==0) {
                $.debug("["+connConfiguration.getIp()+"]，连接异常（已达最大重试次数）......"+e);
                throw e;
            } else if($.str(e.getMessage()).indexOf("connection is closed by foreign host") != -1) {
                Thread.sleep(3000);
                connConfiguration.setRetryTimes(connConfiguration.getRetryTimes()-1);
                return connect();
            } else if($.str(e.getMessage()).indexOf("SocketException") != -1) {
                Thread.sleep(3000);
                connConfiguration.setRetryTimes(connConfiguration.getRetryTimes()-1);
                return connect();
            } else {
                $.debug("["+connConfiguration.getIp()+"]，连接异常......"+e);
                throw e;
            }
        } catch (Exception e) {
            result.addError($.exception(e)).addMessage($.warn("登录服务器失败：" + connConfiguration.getUsername() + "@" + connConfiguration.getIp()+":"+connConfiguration.getPort()));
        } finally {
            if(login) $.async(() -> {
                try {
                    int i=0;
                    while (login && (i++)*1000 < connConfiguration.getTimeOut()) { // 如果没有主动退出，则等待超时
                        Thread.sleep(1000);
                    }

                    logout();
                } catch (Exception  e) {
                    result.addError($.exception(e));
                }
            });
        }


        return this;
    }

    /*
     * 退出/关闭服务器连接
     */
    public $FtpInterface logout()  {
        if(null != sftpChannel && sftpChannel.isConnected()) sftpChannel.disconnect();
        if(null != session && session.isConnected()) session.disconnect();

        return this;
    }

    @Override
    public $Progress getProgress() {
        return progress;
    }

    public long getTransferredBytes(){
        return transferredBytes;
    }

    public long getTotalBytes(){
        return totalBytes;
    }


    /*
     * 上传文件
     * @param inputStream 文件流
     * @param size 文件大小
     * @param destPath 目标路径
     * @param fileName 目标文件名
     * @return
     */
    @Override
    public $Result uploadFile(InputStream inputStream, long size, String destPath, String fileName) throws Exception {
        $FtpKPI kpi = new $FtpKPI();

        long dt = System.currentTimeMillis();
        $.info($.concat("[Jsch Execute sftp]: ", fileName, " >>>>>>> ", connConfiguration.getIp()+":",destPath+"/"+fileName));

        try {
            sftpChannel = (ChannelSftp) getSession().openChannel("sftp");
            sftpChannel.connect();

            progress.setStart(true);

            sftpProgressMonitor = new SftpProgressMonitor() {
                @Override
                public void init(int op, String src, String dest, long max) {
                    totalBytes = max == -1 ? size : max;
                    $.debug("[Jsch sftp Progress] -- Total ( "+ progress.getTotal() + " bytes )");
                    progress.setTotal(totalBytes);
                }

                @Override
                public boolean count(long count) {
                    transferredBytes += count;
                    progress.setCurrent(transferredBytes);
                    $.debug("[Jsch sftp Progress] ("+(progress.getUsedTime()/1000) + "--" + $.doubled.toDouble(progress.getFinish(),2)+"%--"+$.unit.convert(progress.getSpeed())+"/s): " + progress.getCurrent() + " bytes / " + progress.getTotal() + " bytes");
                    return true;
                }

                @Override
                public void end() {
                    $.debug("[Jsch sftp Progress] -- End!");
                    progress.setEnd(true);
                }
            };

            sftpChannel.put(inputStream, destPath+"/"+fileName, sftpProgressMonitor);

            result.setSuccess(true);
            sftpChannel.disconnect();
            $.info("[Jsch Execute sftp]: "+connConfiguration.getIp()+"]，执行结束......");
        } catch (IOException e) {
            result.addError($.exception(e)).addMessage("ftp上传文件失败");
        } catch (Exception e) {
            result.addError(e);
            throw $.exception(e);
        } finally {
            disconnect();
        }

        kpi.setWriteByte(size);
        kpi.setFilecount(1);

        return result.setData(kpi);
    }

    public $ConnConfiguration getConnConfiguration() {
        return connConfiguration;
    }

    public $SFtpJsch setConnConfiguration($ConnConfiguration connConfiguration) {
        this.connConfiguration = connConfiguration;
        return this;
    }

    public boolean isLogin() {
        return login;
    }

    public void setLogin(boolean login) {
        this.login = login;
    }
    public synchronized Session getSession() throws Exception {
        connect(); // 进行连接
        return session;
    }

}
