package com.ailbb.alt.linux.ssh;

import com.ailbb.ajj.entity.$Result;
import com.ailbb.ajj.entity.$Status;
import com.ailbb.alt.$;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by Wz on 7/10/2019.
 */
public abstract class $SSHExtend {
    /**
     * 获取流的结果
     * @param stdout
     * @param stderr
     * @return
     * @throws IOException
     */
    $Result readInputStream(InputStream stdout, InputStream stderr) throws IOException {
        $Result rs = $.result();
        BufferedReader reader = null;
        String line = null; // 当前行

        if (stdout !=null) {
            reader = new BufferedReader(new InputStreamReader(stdout));
            while ((line = reader.readLine()) != null) {
                $.sout("[Result]：" + line);
                rs.putData(line);
            }
        }

        if (stderr != null) {
            reader = new BufferedReader(new InputStreamReader(stderr));
            while ((line = reader.readLine()) != null) {
                rs.addMessage($.warn("[CMD error]：" + line));
            }
        }

        $.file.closeStearm(reader);

        return rs;
    }

    /**
     * 执行命令成功
     * @param stat
     * @return
     */
    $Status statusCmd(int stat) {
        return statusCmd(stat, 0);
    }

    /**
     * 执行命令成功
     * @param stat
     * @param successStat
     * @return
     * @throws IOException
     */
    $Status statusCmd(int stat, int successStat) {
        if(stat == successStat)
            return new $Status(true, stat, $.info("[执行命令成功]：" + stat), "成功");

        return new $Status(false, stat, $.info("[执行命令失败]：" + stat), "失败");
    }
}
