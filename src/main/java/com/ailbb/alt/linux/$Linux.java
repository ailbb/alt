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

import java.io.IOException;

import static com.ailbb.ajj.$.*;

/**
 * Created by Wz on 6/20/2018.
 */
public class $Linux {
    public static final int $PORT = 22;
    Connection connection = null;
    private $ConnConfiguration connConfiguration;

    /**
     * 初始化对象
     * @param connConfiguration
     * @return
     */
    public $Linux init($ConnConfiguration connConfiguration)  {
        this.setConnConfiguration(connConfiguration)
            .setConnection(new Connection(connConfiguration.getIp(), connConfiguration.getPort()))
            .login();
        return this;
    }

    public $Result login()  {
        $Result rs = $.result();
        try {
            connection.connect();
            if(!connection.authenticateWithPassword(connConfiguration.getUsername(), connConfiguration.getPassword()))
                throw new $LinuxException.$LoginErrorException("鉴权异常！");
            rs.addMessage(info(connConfiguration.getIp() + " - 连接成功>>>>>>>>>>>>>>>>>>>>>>>>"));
        } catch (IOException e) {
            rs.addError($.exception(e));
        } catch ($LinuxException.$LoginErrorException e) {
            rs.addError($.exception(e));
        }

        return rs;
    }

    public $Result closeConnection() {
        $Result rs = $.result();

        if(connection != null)
            connection.close();

        return rs.addMessage(info(connConfiguration.getIp() + " - 连接已关闭>>>>>>>>>>>>>>>>>>>>>>>>"));
    }

    public $Result cmd(String c)  {
        return exec(c);
    }

    public $Result exec(String c)  {
        $Result ssh = $.result();
        Integer status = -1;

        try {
            Session session = null;
                session = getConnection().openSession();
            session.execCommand(c);

            ssh = $.resultIf(read(new StreamGobbler(session.getStdout())), read(new StreamGobbler(session.getStderr())));

            ssh.setCode(session.getExitStatus());
            session.waitForCondition(ChannelCondition.EXIT_STATUS, connConfiguration.getTimeOut());
            session.close();

        } catch (IOException e) {
            ssh.addError(e);
        } finally {
            closeConnection();
        }

        ssh.addMessage(info(ssh));

        return ssh.setRemark(c);
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
