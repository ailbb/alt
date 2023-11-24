package com.ailbb.alt.linux.ssh;

import com.ailbb.ajj.entity.$ConnConfiguration;
import com.ailbb.ajj.entity.$Result;
import com.ailbb.alt.$;
import com.ailbb.alt.exception.$LinuxException;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.session.ClientSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/*
 * Created by Wz on 7/10/2019.
 */
public class $SSHRemoteSSHD extends $SSHExtend {
    $ConnConfiguration connConfiguration;
    ClientSession session ; // 连接会话
    InputStream stdout = null; // 结果读取流
    InputStream stderr = null; // 错误日志读取流
    BufferedReader reader = null; // 缓存

    $Result result = $.result();

    public $SSHRemoteSSHD($ConnConfiguration connConfiguration){
        this.connConfiguration = connConfiguration;
    }

    @Override
    public synchronized boolean isConnected() {
        $.debug("["+connConfiguration.getIp()+"]，获取到连接信息......");
        return $.isEmptyOrNull(session) ? false : session.isOpen();
    }

    @Override
    public synchronized $SSHInterface connect() throws Exception {
        try {
            if($.isEmptyOrNull(connConfiguration)) throw new $LinuxException.$ConnectErrorException("没有连接信息!");

            if(isConnected()) return this;

            SshClient client = SshClient.setUpDefaultClient();

            client.start();

            session = client.connect(connConfiguration.getUsername(), connConfiguration.getIp(), connConfiguration.getPort())
                    .verify(connConfiguration.getTimeOut(), TimeUnit.MILLISECONDS)
                    .getSession();

            session.addPasswordIdentity(connConfiguration.getPassword());
            session.auth().verify(connConfiguration.getTimeOut(), TimeUnit.MILLISECONDS);
        } catch (IOException e) {
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
        $.file.closeStream(session);
        return this;
    }

    @Override
    public  $Result execCmd(String runCmdStr) throws Exception{
        try {
            result = $.result();

            $.info("["+connConfiguration.getIp()+"][SSHD Execute cmd]: " + runCmdStr);

            ChannelExec channel = getSession().createExecChannel(runCmdStr);
            channel.setOut(null);
            channel.setErr(null);

            channel.open();

            stdout = channel.getInvertedOut();
            stderr = channel.getInvertedErr();

            new $SSHThread(stdout, result, "data").run();
            new $SSHThread(stderr, result, "message").run();

            while (!channel.isClosed()) {
                Thread.sleep(100);
            }

            result.setStatus(statusCmd(result.isSuccess() ? channel.getExitStatus() : result.getCode(), 0,9));
            channel.close();
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

    public synchronized ClientSession getSession() throws Exception {
        connect(); // 进行连接
        return session;
    }

}
