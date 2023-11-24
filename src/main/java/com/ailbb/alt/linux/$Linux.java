package com.ailbb.alt.linux;

import com.ailbb.ajj.$;
import com.ailbb.ajj.entity.$ConnConfiguration;
import com.ailbb.ajj.entity.$Result;
import com.ailbb.ajj.entity.$Status;
import com.ailbb.alt.linux.ssh.*;

import java.util.ArrayList;
import java.util.List;

/*
 * Created by Wz on 6/20/2018.
 */
public class $Linux {
    public static final int $PORT = 22;
    private $ConnConfiguration connConfiguration;
    private List<$SSHInterface> runners = new ArrayList<>();

    /*
     * 初始化对象
     * @return
     */
    public $Linux init()  {
        return init(null);
    }

    /*
     * 初始化对象
     * @param connConfiguration
     * @return
     */
    public $Linux init($ConnConfiguration connConfiguration)  {
        this.setConnConfiguration(connConfiguration);

        runners.clear();

        if($.isLocalIp(connConfiguration.getIp())) {
            runners.add(new $SSHLocalRuntime());
        } else {
            if(!$.isEmptyOrNull(connConfiguration.getIps())) {
                for (String ip : connConfiguration.getIps()) {
                    if(!$.isEmptyOrNull(ip)) runners.add(new $SSHRemoteSSHD($.clone(connConfiguration).setIp(ip)));
                }
            } else {
                runners.add(new $SSHRemoteSSHD(connConfiguration));
            }
        }

        return this;
    }

    public synchronized $Result cmd(String... c)  {
        return exec(c);
    }

    public synchronized $Result sudo(String... c)  {
        return execSudo(c);
    }

    public synchronized $Result exec(String... c)  {
        return executeCmd(c);
    }

    public synchronized $Result execSudo(String... c)  {
        return executeCmdSudo(c);
    }

    public synchronized $Result executeCmd(String... c)  {
        $Result rs = $.result();
        $.debugOut("开始在 ("+ runners.size()+") 台设备，线程数（"+$.thread.getRunThreadPoolSize()+"）批量执行命令："+$.join(c,";") );

        // 开始执行命令
        for($SSHInterface ssh : runners) {
            // 同时执行所有远程接口
            $.async(()->{
                try {
                    ssh.batchExecuteCmd(c);
                } catch (Exception e) {
                    $.warn("请求失败，如有其他请求方式，将继续执行...", e); // 单个执行失败不影响其他引擎执行
                }
            });
        }

        // 启动监听程序，等待所有执行完成，才结束
        while (true) {
            int size = runners.size();
            int end = 0;
            int success = 0;
            int error = 0;

            for($SSHInterface ssh : runners) {
                if(null != ssh.getStatus() && ssh.getStatus().isEnd()) {
                    if (ssh.getResult().isSuccess()) {
                        success++;
                    }else {
                        error++;
                    }

                    end++;
                }
            }

            $.info("当前进度："+end+"("+success+"/"+error+")/"+size);

            if(end == size) {
                for($SSHInterface ssh : runners) {
                    if(!ssh.getResult().isSuccess()) rs.addMessage(ssh.getStatus().getMessage());
                    rs.addData(ssh.getResult().getData());
                }

                boolean isSuccess = success == size;
                if(isSuccess)
                    rs.setStatus(new $Status(true, 0, "[执行命令成功]", "成功"));
                else
                    rs.setStatus(new $Status(false, 1, "[部分命令执行失败]", "部分失败"));

                break;
            }

            try { Thread.sleep(100); }catch (Exception e){};
        }

        return rs;
    }

    public synchronized $Result executeCmdSudo(String... c)  {
        $Result rs = $.result();
        $.debugOut("开始在 ("+ runners.size()+") 台设备，线程数（"+$.thread.getRunThreadPoolSize()+"）批量执行命令："+$.join(c,";"));

        // 开始执行命令
        for($SSHInterface ssh : runners) {
            // 同时执行所有远程接口
            $.async(()->{
                try {
                    ssh.batchExecuteCmdSudo(c);
                } catch (Exception e) {
                    $.warn("请求失败，如有其他请求方式，将继续执行...", e); // 单个执行失败不影响其他引擎执行
                }
            });
        }

        // 启动监听程序，等待所有执行完成，才结束
        while (true) {
            int size = runners.size();
            int end = 0;
            int success = 0;
            int error = 0;

            for($SSHInterface ssh : runners) {
                if(null != ssh.getStatus() && ssh.getStatus().isEnd()) {
                    if (ssh.getResult().isSuccess()) {
                        success++;
                    }else {
                        error++;
                    }

                    end++;
                }
            }

            $.info("当前进度："+end+"("+success+"/"+error+")/"+size);

            if(end == size) {
                rs.setStatus(
                        (success == size) ? new $Status(true, 0, "[执行命令成功]："+success, "成功")
                            :  new $Status(false, 1, "[部分命令执行失败]："+error, "部分失败")
                );
                break;
            }

            try { Thread.sleep(100); }catch (Exception e){};
        }

        for($SSHInterface ssh : runners) {
            if(!ssh.getResult().isSuccess()) rs.addMessage(ssh.getStatus().getMessage());
            rs.addData(ssh);
        }

        return rs;
    }

    public $ConnConfiguration getConnConfiguration() {
        return connConfiguration;
    }

    public $Linux setConnConfiguration($ConnConfiguration connConfiguration) {
        this.connConfiguration = connConfiguration;
        return this;
    }

    public synchronized boolean existUser(String createUser){
        $Result rs = executeCmdSudo(
                String.format("id -u %s", createUser)
        );

        return rs.getStatus().getCode() == 1 ? false : true;
    }

    public synchronized boolean deleteUser(String createUser){
        if(!existUser(createUser)) return true;

        $Result rs = executeCmdSudo(
            String.format("userdel %s", createUser),
            String.format("rm -rf /home/%s", createUser)
        );

        return  rs.getStatus().getCode() == 1 ? false : true;
    }

    public synchronized boolean addUser(String createUser){
        $Result rs = executeCmdSudo(
            String.format("useradd -m %s", createUser)
        );

        return rs.getStatus().getCode() == 1 ? false : true;
    }

    public synchronized boolean addSudoUser(String createUser){
        $Result rs = executeCmdSudo(
            String.format("useradd -m %s", createUser),
            String.format("usermod -G wheel %s", createUser)
        );

        return rs.getStatus().getCode() == 1 ? false : true;
    }

    public synchronized boolean passwdUser(String createUser, String createUserPasswd){
        $Result rs = executeCmdSudo(
             String.format("echo \"" + createUser + ":" + createUserPasswd + "\" | chpasswd")
        );

        return rs.getStatus().getCode() == 1 ? false : true;
    }

    public synchronized boolean chmod(String path, int chmod){
        $Result rs = executeCmdSudo(
                String.format("chmod " + chmod + " " + path)
        );

        return rs.getStatus().getCode() == 1 ? false : true;
    }

    public synchronized boolean chmodR(String path, int chmod){
        $Result rs = executeCmdSudo(
             String.format("chmod -R " + chmod + " " + path)
        );

        return rs.getStatus().getCode() == 1 ? false : true;
    }

    @Override
    public String toString() {
        return connConfiguration.toString();
    }
}