package com.ailbb.alt.linux.ssh;

import com.ailbb.ajj.entity.$ConnConfiguration;
import com.ailbb.ajj.entity.$Result;
import com.ailbb.ajj.entity.$Status;
import com.ailbb.alt.$;
import com.ailbb.alt.exception.$LinuxException;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/*
 * Created by Wz on 7/10/2019.
 */
public class $SSHRemoteJsch extends $SSHExtend {
    $ConnConfiguration connConfiguration;
    Session session ; // 连接会话
    InputStream stdout = null; // 结果读取流
    InputStream stderr = null; // 错误日志读取流
    BufferedReader reader = null; // 缓存

    $Result result = $.result();

    public $SSHRemoteJsch($ConnConfiguration connConfiguration){
        this.connConfiguration = connConfiguration;
    }

    @Override
    public synchronized boolean isConnected() {
        $.debug("["+connConfiguration.getIp()+"]，获取到连接信息......");
        return $.isEmptyOrNull(session) ? false : session.isConnected();
    }

    @Override
    public synchronized $SSHInterface connect() throws Exception {
        try {
            if($.isEmptyOrNull(connConfiguration)) throw new $LinuxException.$ConnectErrorException("没有连接信息!");

            if(isConnected()) return this;

            JSch jSch = new JSch(); // 创建JSch
            // 获取session
            session = jSch.getSession(connConfiguration.getUsername(), connConfiguration.getIp(), connConfiguration.getPort());
            session.setPassword(connConfiguration.getPassword());
            session.setConfig("StrictHostKeyChecking", "no");

            // 启动连接
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
            $.debug("["+connConfiguration.getIp()+"]，连接异常......"+e);
            throw e;
        }

        return this;
    }

    @Override
    public synchronized $SSHInterface disconnect() {
        $.file.closeStream(stdout);
        $.file.closeStream(stderr);
        $.file.closeStream(reader);
        if(!$.isEmptyOrNull(session)) session.disconnect();
        return this;
    }

    @Override
    public synchronized $Result execCmd(String runCmdStr) throws Exception {
        try {
            result = $.result();

            $.info("["+connConfiguration.getIp()+"][Jsch Execute cmd]: " + runCmdStr);

            ChannelExec channel = (ChannelExec)getSession().openChannel("exec");
            channel.setCommand(runCmdStr);
            channel.setInputStream(null);
            channel.setErrStream(System.err);

            channel.connect();

            stdout = channel.getInputStream();
            stderr = channel.getErrStream();

            new $SSHThread(stdout, result, "data").run();
            new $SSHThread(stderr, result, "message").run();

            while (!channel.isClosed()) {
                Thread.sleep(100);
            }

            result.setStatus(statusCmd(result.isSuccess() ? channel.getExitStatus() : result.getCode(), 0));
            channel.disconnect();
            $.debug("["+connConfiguration.getIp()+"]，执行结束......");
        } catch (Exception e) {
            result.setStatus(statusCmd(-1, 0, "远程请求发生异常......"));
            throw $.exception(e);
        } finally {
            disconnect();
        }

        return result;
    }

    @Override
    public synchronized $Result batchExecuteCmd(String... cmd) throws Exception {
        String noSudoCmd = "sh -c ";
        String runCmdStr = noSudoCmd+ "\""+$.join(cmd," && ")+"\"";
        return this.execCmd(runCmdStr);
    }

    @Override
    public synchronized $Result batchExecuteCmdSudo(String... cmd) throws Exception {
        String sudoCmd = "echo \""+connConfiguration.getPassword()+"\" | sudo -S sh -c ";
        String runCmdStr = sudoCmd + "\""+$.join(cmd," && ")+"\"";
        return this.execCmd(runCmdStr);
    }

    @Override
    public $Result getResult() {
        return result;
    }

    public synchronized Session getSession() throws Exception {
        connect(); // 进行连接
        return session;
    }

}
