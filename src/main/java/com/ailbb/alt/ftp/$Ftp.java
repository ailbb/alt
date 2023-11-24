package com.ailbb.alt.ftp;

import com.ailbb.ajj.$;
import com.ailbb.ajj.entity.$ConnConfiguration;
import com.ailbb.ajj.entity.$Progress;
import com.ailbb.ajj.entity.$Result;
import com.ailbb.alt.ftp.impl.$FtpInterface;
import com.ailbb.alt.ftp.impl.$SFtpJsch;
import com.ailbb.alt.ftp.impl.$XFtp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class $Ftp {
    public static final int $PORT = 22;
    private $ConnConfiguration connConfiguration;
    private List<$FtpInterface> runners = new ArrayList<>();

    $Progress progress;

    /*
     * 初始化对象
     * @return
     */
    public $Ftp init()  {
        return init(null);
    }

    /*
     * 初始化对象
     * @param connConfiguration
     * @return
     */
    public $Ftp init($ConnConfiguration connConfiguration)  {
        this.setConnConfiguration(connConfiguration);

        runners.clear();
        progress = new $Progress();

        if($.isLocalIp(connConfiguration.getIp())) return this;

        if(!$.isEmptyOrNull(connConfiguration.getIps())) {
            for (String ip : connConfiguration.getIps()) {
                if(!$.isEmptyOrNull(ip)) {
                    runners.add(new $SFtpJsch($.clone(connConfiguration).setIp(ip)));
//                    runners.add(new $XFtp($.clone(connConfiguration).setIp(ip)));
                }
            }
        } else {
            runners.add(new $XFtp(connConfiguration));
        }

        return this;
    }

    /*
     * 初始化对象
     * @param connConfiguration
     * @return
     */
    public $Ftp login($ConnConfiguration connConfiguration)  {
        this.setConnConfiguration(connConfiguration);

        runners.clear();
        progress = new $Progress();

        if($.isLocalIp(connConfiguration.getIp())) return this;

        if(!$.isEmptyOrNull(connConfiguration.getIps())) {
            for (String ip : connConfiguration.getIps()) {
                if(!$.isEmptyOrNull(ip)) runners.add(new $XFtp($.clone(connConfiguration).setIp(ip)).login());
            }
        } else {
            runners.add(new $XFtp(connConfiguration).login());
        }

        return this;
    }

    public synchronized $Result uploadFile(InputStream is, Long length, String file1, String file2)  {
        $Result rs = $.result();
        $.info("开始在 ("+ runners.size()+") 台设备，线程数（"+$.thread.getRunThreadPoolSize()+"）执行传输文件："+file2 );

        // 开始执行命令
        for($FtpInterface ftp : runners) {
            // 同时执行所有远程接口
            try {
                ftp.connect();
                ftp.uploadFile(is, length, file1, file2);
            } catch (Exception e) {
                $.warn("请求失败，如有其他请求方式，将继续执行...", e); // 单个执行失败不影响其他引擎执行
            } finally {
                $.closeStream(is);
                ftp.disconnect();
            }
        }

        return rs;
    }

    public synchronized $Result sftp(File sourceFile, String descPath)  {
        $Result rs = $.result();
        $.info("开始在 ("+ runners.size()+") 台设备，线程数（"+$.thread.getRunThreadPoolSize()+"）批量执行传输文件："+sourceFile.getPath() );

        progress = new $Progress(true);

        // 开始执行命令
        for($FtpInterface ftp : runners) {
            // 同时执行所有远程接口
            try {
                // 创建临时描述文件
                ftp.connect();
                progress.addChild(ftp.getProgress());
                $.file.scanPath(sourceFile.getAbsolutePath(), (f)->{
                    try {
                        ftp.uploadFile($.file.getInputStream(f), f.length(), descPath, f.getName());
                    } catch (IOException e) {
                        rs.addError(e);
                    } catch (Exception e) {
                        rs.addError(e);
                    }
                });

                // 删除临时文件
            } catch (Exception e) {
                $.warn("请求失败，如有其他请求方式，将继续执行...", e); // 单个执行失败不影响其他引擎执行
            } finally {
                ftp.disconnect();
            }
        }

        return rs;
    }

    public $Progress getProgress(){
        return progress;
    }

    public $ConnConfiguration getConnConfiguration() {
        return connConfiguration;
    }

    public void setConnConfiguration($ConnConfiguration connConfiguration) {
        this.connConfiguration = connConfiguration;
    }
}
