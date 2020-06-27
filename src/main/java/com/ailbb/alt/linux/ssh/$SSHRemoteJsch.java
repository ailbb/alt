package com.ailbb.alt.linux.ssh;

import com.ailbb.ajj.entity.$ConnConfiguration;
import com.ailbb.ajj.entity.$Result;
import com.ailbb.ajj.entity.$Status;
import com.ailbb.alt.$;
import com.ailbb.alt.exception.$LinuxException;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by Wz on 7/10/2019.
 */
public class $SSHRemoteJsch extends $SSHExtend implements $SSHInterface {
    $ConnConfiguration connConfiguration;
    Session session ; // 连接会话
    InputStream stdout = null; // 结果读取流
    InputStream stderr = null; // 错误日志读取流
    BufferedReader reader = null; // 缓存

    public $SSHRemoteJsch($ConnConfiguration connConfiguration){
        this.connConfiguration = connConfiguration;
    }

    @Override
    public boolean isConnected() {
        return $.isEmptyOrNull(session) ? false : session.isConnected();
    }

    @Override
    public $SSHInterface connect() throws Exception {
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
        } catch (Exception e) {
            throw e;
        }

        return this;
    }

    @Override
    public $SSHInterface disconnect() {
        $.file.closeStearm(stdout);
        $.file.closeStearm(stderr);
        $.file.closeStearm(reader);
        if(!$.isEmptyOrNull(session)) session.disconnect();
        return this;
    }

    @Override
    public $Result executeCmd(String cmd) throws Exception {
        $Result rs = $.result();

        try {
            $.info("[Jsch Execute cmd]: " + cmd);

            ChannelExec exec = (ChannelExec)getSession().openChannel("exec");
            exec.setCommand(cmd);
            exec.setInputStream(null);
            exec.setErrStream(System.err);
            exec.connect();

            stdout = exec.getInputStream();
            stderr = exec.getErrStream();

            new $SSHThread(stdout, rs, "data").start();
            new $SSHThread(stderr, rs, "message").start();

            rs.setStatus(statusCmd(exec.getExitStatus(), 0));
            exec.disconnect();
        } catch (Exception e) {
            $.error("远程请求发生异常......");
            throw $.exception(e);
        } finally {
            disconnect();
        }

        return rs;
    }

    public Session getSession() throws Exception {
        connect(); // 进行连接
        return session;
    }

}
