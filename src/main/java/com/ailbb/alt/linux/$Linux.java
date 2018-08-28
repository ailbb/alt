package com.ailbb.alt.linux;

import ch.ethz.ssh2.ChannelCondition;
import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;
import com.ailbb.ajj.$;
import com.ailbb.ajj.entity.$ConnConfiguration;
import com.ailbb.ajj.entity.$Result;
import com.ailbb.ajj.entity.$Status;
import com.ailbb.alt.exception.$LinuxException;

import static com.ailbb.ajj.$.*;

/**
 * Created by Wz on 6/20/2018.
 */
public class $Linux {
    Connection connection = null;
    private $ConnConfiguration connConfiguration;
    public final int $PORT = 22;

    /**
     * 初始化对象
     * @param connConfiguration
     * @return
     */
    public $Linux init($ConnConfiguration connConfiguration){
        this.setConnConfiguration(connConfiguration)
            .setConnection(new Connection(connConfiguration.getIp(), connConfiguration.getPort()))
            .login();
        return this;
    }

    public $Result login(){
        try {
            connection.connect();
            if(!connection.authenticateWithPassword(connConfiguration.getUsername(), connConfiguration.getPassword()))
                throw new $LinuxException.$LoginErrorException("鉴权异常！");
        } catch (Exception e) {
            error("连接服务器失败：" + this.toString() + e.getMessage());
            exception(e);
        }

        return $.result();
    }

    public $Result closeConnection() {
        if(connection != null)
            connection.close();

        info(connConfiguration.getIp() + " - 连接已关闭>>>>>>>>>>>>>>>>>>>>>>>>");

        return $.result();
    }

    public $Result cmd(String c) {
        return exec(c);
    }

    public $Result exec(String c) {
        $Result ssh = new $Result().setRemark(c);
        Integer status = -1;
        String data = null;
        String err = null;

        try {
            Session session = null;
                session = getConnection().openSession();
            session.execCommand(c);

            data = read(new StreamGobbler(session.getStdout()));
            err = read(new StreamGobbler(session.getStderr()));
            status = session.getExitStatus();

            session.waitForCondition(ChannelCondition.EXIT_STATUS, connConfiguration.getTimeOut());
            session.close();
        } catch (Exception e) {
            ssh.setSuccess(false);
            error(err = ("执行命令失败：" + this.toString()));
            e.printStackTrace();
        } finally {
            closeConnection();
        }

        ssh.setCode(status).setData(data).addError(err);

        info(ssh);

        return ssh;
    }

    public Connection getConnection(){
        return connection;
    }

    public $Linux setConnection(Connection connection) {
        this.connection = connection;
        return this;
    }

    public $ConnConfiguration getConnConfiguration() {
        return connConfiguration;
    }

    public $Linux setConnConfiguration($ConnConfiguration connConfiguration) {
        this.connConfiguration = connConfiguration;
        return this;
    }

    @Override
    public String toString() {
        return connConfiguration.toString();
    }
}
