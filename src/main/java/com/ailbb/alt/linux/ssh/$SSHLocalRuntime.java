package com.ailbb.alt.linux.ssh;

import com.ailbb.ajj.entity.$Result;
import com.ailbb.ajj.entity.$Status;
import com.ailbb.alt.$;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by Wz on 7/10/2019.
 */
public class $SSHLocalRuntime extends $SSHExtend implements $SSHInterface {
    Runtime rt; // 运行时连接

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
    public $Result executeCmd(String cmd) throws Exception {
        $Result rs = $.result();

        try {
            $.info("cmd:" + cmd);

            Process proc = getRuntime().exec(new String[]{"/bin/sh","-c", $.string.trim(cmd)}); // 执行命令
            InputStream stdout = proc.getInputStream(); // 输入流
            InputStream stderr = proc.getErrorStream(); // 输出流

            rs = readInputStream(stdout, stderr);

            rs.setStatus(statusCmd(proc.waitFor(), 0));
        } catch (Exception e) {
            $.error("本地请求发生异常......");
            throw $.exception(e);
        } finally {
            disconnect();
        }

        return rs;
    }


    public Runtime getRuntime() throws Exception {
        connect(); // 进行连接
        return rt;
    }

}
