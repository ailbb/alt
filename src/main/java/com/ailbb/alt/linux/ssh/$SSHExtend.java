package com.ailbb.alt.linux.ssh;

import com.ailbb.ajj.entity.$Result;
import com.ailbb.ajj.entity.$Status;
import com.ailbb.alt.$;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

/*
 * Created by Wz on 7/10/2019.
 */
public abstract class $SSHExtend implements $SSHInterface{
    public $Status status;
    /*
     * 执行命令成功
     * @param stat
     * @return
     */
    synchronized $Status statusCmd(int stat) {
        return statusCmd(stat, 0);
    }

    /*
     * 执行命令成功
     * @param stat
     * @param successStat
     * @return
     * @throws IOException
     */
    synchronized $Status statusCmd(int stat, int... successStat) {
        boolean success = false;
        for (int num : successStat)  if (num == stat) success = true;

        if(success)
            this.status = new $Status(true, stat, "[执行命令成功]：" + stat, "成功");
        else
            this.status = new $Status(false, stat, $.info("[执行命令结果]：" + stat), "失败");

        return this.status;
    }

    /*
     * 执行命令成功
     * @param stat
     * @param successStat
     * @return
     * @throws IOException
     */
    synchronized $Status statusCmd(int stat, int successStat, String msssage) {
        if(stat == successStat)
            this.status = new $Status(true, stat, $.info(msssage + stat), "成功");

        this.status = new $Status(false, stat, $.info(msssage + stat), "失败");

        return this.status;
    }

    @Override
    public synchronized $Status getStatus() {
        return this.status;
    }
}
