package com.ailbb.alt.linux.ssh;

import ch.ethz.ssh2.ChannelCondition;
import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;
import com.ailbb.ajj.entity.$ConnConfiguration;
import com.ailbb.ajj.entity.$Result;
import com.ailbb.ajj.entity.$Status;
import com.ailbb.alt.$;
import com.ailbb.alt.exception.$LinuxException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by Wz on 7/10/2019.
 */
public class $SSHRemoteEh2 extends $SSHExtend implements $SSHInterface {
    $ConnConfiguration connConfiguration;
    Connection connection; // 连接
    InputStream stdout = null; // 结果读取流
    InputStream stderr = null; // 错误日志读取流

    public $SSHRemoteEh2($ConnConfiguration connConfiguration){
        this.connConfiguration = connConfiguration;
    }

    @Override
    public boolean isConnected() {
        return $.isEmptyOrNull(connection) ? false : connection.isAuthenticationComplete();
    }

    @Override
    public $SSHInterface connect() throws Exception {
        try {
            if($.isEmptyOrNull(connConfiguration)) throw new $LinuxException.$ConnectErrorException("没有连接信息!");

            if(isConnected()) return this;

            connection = new Connection(connConfiguration.getIp());
            connection.connect(); // 进行连接
            connection.authenticateWithPassword(connConfiguration.getUsername(), connConfiguration.getPassword()); // 进行验证
        } catch (Exception e) {
            throw e;
        }

        return this;
    }

    @Override
    public $SSHInterface disconnect() {
        $.file.closeStearm(stdout);
        $.file.closeStearm(stderr);
        if(!$.isEmptyOrNull(connection)) connection.close();
        return this;
    }

    @Override
    public $Result executeCmd(String cmd) throws Exception {
        $Result rs = $.result();

        try {
            $.info("[Eh2 Execute cmd]: " + cmd);

            Session session = getConnection().openSession();
            session.execCommand(cmd);

            stdout = new StreamGobbler(session.getStdout());
            stderr = new StreamGobbler(session.getStderr());

            new $SSHThread(stdout, rs, "data").start();
            new $SSHThread(stderr, rs, "message").start();

            session.waitForCondition(ChannelCondition.EXIT_STATUS, connConfiguration.getTimeOut());

            rs.setStatus(statusCmd(session.getExitStatus(), 0));
            session.close();
        } catch (Exception e) {
            $.error("远程请求发生异常......");
            throw $.exception(e);
        } finally {
            disconnect();
        }

        return rs;
    }

    public Connection getConnection() throws Exception {
        connect(); // 尝试进行连接
        return connection;
    }

}
