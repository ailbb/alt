package com.ailbb.alt.linux;

import ch.ethz.ssh2.ChannelCondition;
import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;

import static com.ailbb.ajj.$.*;

/**
 * Created by Wz on 6/20/2018.
 */
public class $Linux {
    private String single = null;
    Connection connection = null;

    private String ip = "127.0.0.1";
    private String username = "root";
    private String password = "123456";

    private int timeOut = 1000 * 5 * 60;

    public $Linux() {
    }

    public $Linux(String ip, String username, String password) {
        this.ip = ip;
        this.username = username;
        this.password = password;
    }

    public boolean login(){
        try {
            return getConnection().authenticateWithPassword(username, password);
        } catch (Exception e) {
            exception(e);
            return false;
        }
    }

    public Connection getConnection(){
        String _single  = this.toString().replaceAll("\\s+||\\S+", "");

        if(single == null || single != _single) {
            single = _single;
        } else {
            return connection;
        }

        try {
            if(null != connection) closeConnection();
            connection = new Connection(ip);
            connection.connect();
        } catch (Exception e) {
            exception(e);
        }

        return connection;
    }

    public void closeConnection() {
        if(connection != null)
            connection.close();

        info(ip + "连接已关闭>>>>>>>>>>>>>>>>>>>>>>>>");
    }

    public SSHResult cmd(String c) {
        return exec(c);
    }

    public SSHResult exec(String c) {
        SSHResult ssh = new SSHResult(c);

        try {
            Integer status = -1;
            String msg = null;
            String err = null;

            if (login()){
                Session session = null;
                    session = getConnection().openSession();
                session.execCommand(c);

                msg = read(new StreamGobbler(session.getStdout()));
                err = read(new StreamGobbler(session.getStderr()));
                status = session.getExitStatus();

                session.waitForCondition(ChannelCondition.EXIT_STATUS, timeOut);
                session.close();
            } else {
                ssh.setSuccess(false);
                error(err = ("登录失败：" + this.toString()));
            }

            ssh.setStatus(status).setMessage(msg).setError(err);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeConnection();
        }

        info(ssh);

        return ssh;
    }

    public String getIp() {
        return ip;
    }

    public $Linux setIp(String ip) {
        this.ip = ip;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public $Linux setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public $Linux setPassword(String password) {
        this.password = password;
        return this;
    }

    public int getTimeOut() {
        return timeOut;
    }

    public $Linux setTimeOut(int timeOut) {
        this.timeOut = timeOut;
        return this;
    }

    public void getHosts(){

    }

    @Override
    public String toString() {
        return "{" +
                " ip='" + ip + '\'' +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", timeOut=" + timeOut +
                '}';
    }
}
