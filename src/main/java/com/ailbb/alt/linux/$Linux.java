package com.ailbb.alt.linux;

import com.ailbb.ajj.$;
import com.ailbb.ajj.entity.$ConnConfiguration;
import com.ailbb.ajj.entity.$Result;
import com.ailbb.alt.linux.ssh.$SSHInterface;
import com.ailbb.alt.linux.ssh.$SSHLocalRuntime;
import com.ailbb.alt.linux.ssh.$SSHRemoteEh2;
import com.ailbb.alt.linux.ssh.$SSHRemoteJsch;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Wz on 6/20/2018.
 */
public class $Linux {
    public static final int $PORT = 22;
    private $ConnConfiguration connConfiguration;
    private List<$SSHInterface> sshs = new ArrayList<>();

    /**
     * 初始化对象
     * @return
     */
    public $Linux init()  {
        return init(null);
    }

    /**
     * 初始化对象
     * @param connConfiguration
     * @return
     */
    public $Linux init($ConnConfiguration connConfiguration)  {
        this.setConnConfiguration(connConfiguration);

        sshs.clear();

        if($.isEmptyOrNull(connConfiguration) || connConfiguration.getIp().equals($.http.getIp()))
            sshs.add(new $SSHLocalRuntime());
        else {
            sshs.add(new $SSHRemoteEh2(connConfiguration));
            sshs.add(new $SSHRemoteJsch(connConfiguration));
        }

        return this;
    }

    public $Result cmd(String c)  {
        return exec(c);
    }

    public $Result exec(String c)  {
        return executeCmd(c);
    }

    public $Result executeCmd(String c)  {
        $Result rs = $.result();

//        for($Linux li : list) {
//            com.ailbb.alt.$.thread.async(new Runnable() {
//                @Override
//                public void run() {
//                    $Result rs = li.executeCmd("mkdir /home/zhangw");
//                    com.ailbb.alt.$.sout(li.getConnConfiguration().getIp() + "---" + rs.getData());
//                    com.ailbb.alt.$.warn(li.getConnConfiguration().getIp() + "---" + rs.getError());
//                }
//            });
//        }

        for($SSHInterface ssh : sshs) {
            try {
                rs = ssh.executeCmd(c);
                break;
            } catch (Exception e) {
                $.warn("请求失败，如有其他请求方式，将继续执行...", e); // 单个执行失败不影响其他引擎执行
            }
        }

        return rs;
    }

    public $Result batchExecuteCmd(String c)  {
        return executeCmd(c);
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
