package com.ailbb.alt.ftp;

import com.ailbb.ajj.$;
import com.ailbb.ajj.entity.$ConnConfiguration;
import com.ailbb.ajj.entity.$Result;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPReply;

import java.io.*;
import java.net.InetAddress;
import java.util.TimeZone;

import static com.ailbb.ajj.$.*;

/**
 * Created by Wz on 6/20/2018.
 */
public class $Ftp {
    private $ConnConfiguration connConfiguration;
    private final int $PORT = 21;
    private InetAddress host;
    private FTPClient ftpClient = null;
    private boolean isLogin = false;

    public $Result login() {
        if(isLogin)
            return $.result().setSuccess(isLogin);

        try {
            ftpClient = new FTPClient();
            FTPClientConfig ftpClientConfig = new FTPClientConfig();
            ftpClientConfig.setServerTimeZoneId(TimeZone.getDefault().getID());
            ftpClient.setControlEncoding("UTF-8");
            ftpClient.configure(ftpClientConfig);
            if(null == host) {
                host = InetAddress.getByName(connConfiguration.getIp());
            } else {
                connConfiguration.setIp(host.getHostAddress());
            }
            ftpClient.connect(host, connConfiguration.getPort());

            //ftp连接回答返回码
            int reply = ftpClient.getReplyCode();

            if (!FTPReply.isPositiveCompletion(reply)) {
                ftpClient.disconnect();
                error("连接ftp服务器失败, code:\t" + reply);
                return $.result().setSuccess(isLogin);
            }

            isLogin = ftpClient.login(connConfiguration.getUsername(), connConfiguration.getPassword());

            if (isLogin) {
                //设置传输协议
                ftpClient.enterLocalPassiveMode();
                ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
                info("登陆FTP服务器:" + connConfiguration.getUsername() + "@" + connConfiguration.getIp());
                isLogin = true;
                ftpClient.setBufferSize(1024 *2);
                ftpClient.setDataTimeout(30 *1000);
            }
        } catch (Exception e) {
            error("登陆服务器失败：" + connConfiguration.getUsername() + "@" + connConfiguration.getIp(), e);
        } finally {
            if(isLogin) async(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(connConfiguration.getTimeOut());
                        logout();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        return $.result().setSuccess(isLogin);
    }

    /**
     * 退出/关闭服务器连接
     */
    public $Result logout(){
        if (null != ftpClient && ftpClient.isConnected()) {
            try {
                if (ftpClient.logout())
                    info("成功退出服务器：" + connConfiguration.getIp());
            } catch (IOException e) {
                error("退出服务器异常：" + connConfiguration.getIp(), e);
            } finally {
                try {
                    ftpClient.disconnect();    //关闭ftp服务器连接
                } catch (IOException e) {
                    error("关闭服务器异常：" + connConfiguration.getIp(), e);
                }
            }
        }

        return $.result();
    }

    /**
     *
     * @param sourcePath
     * @param isReplace
     * @param destPaths
     * @return
     */
    public $Result uploadFile(String sourcePath, boolean isReplace, String... destPaths) {
        if (!isExists(sourcePath)) return $.result().setSuccess(false);

        boolean isSuccess = true;
        // format path
        sourcePath = getPath(sourcePath);
        File sfile = getFile(sourcePath);

        for (String destPath : destPaths) {
            destPath = getPath(destPath).trim();
            if (!isFile(sourcePath)) {
                try {
                    ftpClient.changeWorkingDirectory(destPath); //改变工作路径
                } catch (IOException e) {
                    try {
                        ftpClient.makeDirectory(destPath);
                    } catch (IOException e1) {
                        exception(e1);
                        isSuccess = false;
                        continue;
                    }
                }

                for (String s : sfile.list()) {
                    if (!uploadFile(concat(sourcePath, "/", s), isReplace, concat(destPath, "/", s)).isSuccess()) isSuccess = false;
                }
            } else {
                InputStream is = null;

                try {
                    is = ftpClient.retrieveFileStream(new String(destPath.getBytes("GBK"), FTP.DEFAULT_CONTROL_ENCODING));

                    if (is != null && ftpClient.getReplyCode() != FTPReply.FILE_UNAVAILABLE) { // 如果文件存在
                        if (!isReplace) {
                            warn(String.format("%s is exists!", destPath));
                            isSuccess = true;
                        } else {
                            ftpClient.deleteFile(destPath);
                            isSuccess = uploadFile(sfile, destPath).isSuccess();
                        }
                    }
                } catch (IOException e) {
                    isSuccess = false;
                    exception(e);
                } finally {
                    if (null != is) try {
                        is.close();
                    } catch (IOException e) {
                        error(e);
                    }
                }
            }
        }

        return $.result().setSuccess(isSuccess);
    }

    public $Result uploadFile(File localFile, String... remoteUpLoadPath){
        BufferedInputStream inputStream = null;
        boolean success = true;
        String fileName = localFile.getName();

        for(String r: remoteUpLoadPath) {
            try {
                info(concat("上传文件：", fileName, " >>>>>>> ", r));
                ftpClient.changeWorkingDirectory(r.trim()); //改变工作路径
                inputStream = new BufferedInputStream(new FileInputStream(localFile));
                if (ftpClient.storeFile(fileName, inputStream)) {
                    info("上传成功：" + fileName);
                } else {
                    success = false;
                }
            } catch (Exception e) {
                success = false;
                error("ftp上传文件失败", e);
            } finally {
                if (null != inputStream){
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        error("ftp上传文件关闭IO流失败", e);
                    }
                }
            }
        }

        return $.result().setSuccess(success);
    }

    public InetAddress getHost() {
        return host;
    }

    public $Ftp setHost(InetAddress host) {
        host = host;
        return this;
    }

}
