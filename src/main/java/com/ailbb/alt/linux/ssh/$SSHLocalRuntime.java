package com.ailbb.alt.linux.ssh;

import com.ailbb.ajj.entity.$Result;
import com.ailbb.ajj.entity.$Status;
import com.ailbb.alt.$;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/*
 * Created by Wz on 7/10/2019.
 */
public class $SSHLocalRuntime extends $SSHExtend implements $SSHInterface {
    Runtime rt; // 运行时连接

    $Result result = $.result();

    @Override
    public boolean isConnected() {
        return !$.isEmptyOrNull(rt) ;
    }

    @Override
    public $SSHInterface connect() throws Exception {
        if(isConnected()) return this;

        rt = Runtime.getRuntime(); // 获取运行时

        return this;
    }

    @Override
    public $SSHInterface disconnect() {
        if(!$.isEmptyOrNull(rt)) {
            rt.exit(0);
            rt = null;
        }
        return this;
    }

    @Override
    public $Result execCmd(String runCmdStr) throws Exception {
        result = $.result();

        try {
            $.info("[Local Execute cmd]:" + runCmdStr);

            Process proc = getRuntime().exec(new String[]{"/bin/sh","-c", runCmdStr}); // 执行命令
            InputStream stdout = proc.getInputStream(); // 输入流
            InputStream stderr = proc.getErrorStream(); // 错误流

            new $SSHThread(stdout, result, "data").run();
            new $SSHThread(stderr, result, "message").run();

            result.setStatus(statusCmd(proc.waitFor(), 0));
        } catch (Exception e) {
            $.error("本地请求发生异常......");
            throw $.exception(e);
        }

        return result;
    }

    @Override
    public $Result batchExecuteCmd(String... cmd) throws Exception {
        result = $.result();

        try {
            String runCmdStr = $.join(cmd," && ");
            $.info("[Local Execute cmd]:" + runCmdStr);

            Process proc = getRuntime().exec(new String[]{"/bin/sh","-c", runCmdStr}); // 执行命令
            InputStream stdout = proc.getInputStream(); // 输入流
            InputStream stderr = proc.getErrorStream(); // 错误流

            new $SSHThread(stdout, result, "data").run();
            new $SSHThread(stderr, result, "message").run();

            result.setStatus(statusCmd(proc.waitFor(), 0));
        } catch (Exception e) {
            $.error("本地请求发生异常......");
            throw $.exception(e);
        }

        return result;
    }

    @Override
    public $Result batchExecuteCmdSudo(String... cmd) throws Exception {
        result = $.result();

        try {
            String runCmdStr = $.join(cmd," && ");
            $.info("[Local Execute cmd]:" + runCmdStr);

            Process proc = getRuntime().exec(new String[]{"sudo sh -c ", runCmdStr}); // 执行命令
            InputStream stdout = proc.getInputStream(); // 输入流
            InputStream stderr = proc.getErrorStream(); // 错误流

            new $SSHThread(stdout, result, "data").run();
            new $SSHThread(stderr, result, "message").run();

            result.setStatus(statusCmd(proc.waitFor(), 0));
        } catch (Exception e) {
            $.error("本地请求发生异常......");
            throw $.exception(e);
        }

        return result;
    }

    @Override
    public $Result getResult() {
        return result;
    }

    public Runtime getRuntime() throws Exception {
        connect(); // 进行连接
        return rt;
    }

}
