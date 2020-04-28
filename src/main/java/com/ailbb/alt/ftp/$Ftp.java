package com.ailbb.alt.ftp;

import com.ailbb.ajj.$;
import com.ailbb.ajj.entity.$ConnConfiguration;
import com.ailbb.ajj.entity.$Result;
import org.apache.commons.net.ftp.*;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.TimeZone;

import static com.ailbb.ajj.$.*;

/**
 * Created by Wz on 6/20/2018.
 */
public class $Ftp {
    public static final int $PORT = 21;
    private $ConnConfiguration connConfiguration;
    private InetAddress host;
    private FTPClient ftpClient = null;
    private boolean isLogin = false;
    private long maxReadLength = 10485760; // 最大读取长度10M

    public $Result login()  {
        $Result rs = $.result();

        if(isLogin)
            return rs.setSuccess(isLogin);

        try {
            ftpClient = new FTPClient();
            FTPClientConfig ftpClientConfig = new FTPClientConfig();
            ftpClientConfig.setServerTimeZoneId(TimeZone.getDefault().getID());
            ftpClient.setControlEncoding("UTF-8");
            ftpClient.configure(ftpClientConfig);
            ftpClient.setConnectTimeout(connConfiguration.getTimeOut());
            if(null == host) {
                host = InetAddress.getByName(connConfiguration.getIp());
            } else {
                connConfiguration.setIp(host.getHostAddress());
            }
            ftpClient.connect(host, $.lastDef($PORT, connConfiguration.getPort()));

            //ftp连接回答返回码
            int reply = ftpClient.getReplyCode();

            if (!FTPReply.isPositiveCompletion(reply)) {
                ftpClient.disconnect();
                return rs.setSuccess(isLogin).setCode(reply).addMessage("连接ftp服务器失败, code:\t" + reply);
            }

            isLogin = ftpClient.login(connConfiguration.getUsername(), connConfiguration.getPassword());

            if (isLogin) {
                //设置传输协议
                ftpClient.enterLocalPassiveMode();
                ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
                info("登陆FTP服务器:" + connConfiguration.getUsername() + "@" + connConfiguration.getIp()+":"+connConfiguration.getPort());
                isLogin = true;
                ftpClient.setBufferSize(1024 *2);
                ftpClient.setDataTimeout(connConfiguration.getTimeOut());
            }
        } catch (Exception e) {
            rs.addError($.exception(e)).addMessage($.warn("登陆服务器失败：" + connConfiguration.getUsername() + "@" + connConfiguration.getIp()+":"+connConfiguration.getPort()));
        } finally {
            if(isLogin) async(new Runnable() {
                public void run() {
                    try {
                        int i=0;
                        while (isLogin && (i++)*1000 < connConfiguration.getTimeOut()) { // 如果没有主动退出，则等待超时
                            Thread.sleep(1000);
                        }

                        logout();
                    } catch (Exception  e) {
                        rs.addError($.exception(e));
                    }
                }
            });
        }

        return rs.setSuccess(isLogin);
    }

    /**
     * 退出/关闭服务器连接
     */
    public $Result logout()  {
        $Result rs = $.result();
        if (null != ftpClient && ftpClient.isConnected()) {
            try {
                if (isLogin && ftpClient.logout()) {
                    isLogin = false;
                    info("成功退出服务器：" + connConfiguration.getIp());
                }
            } catch (IOException e) {
                rs.addError($.exception(e)).addMessage("退出服务器异常：" + connConfiguration.getIp());
            } finally {
                try {
                    ftpClient.disconnect();    //关闭ftp服务器连接
                } catch (IOException e) {
                    rs.addError($.exception(e)).addMessage("关闭服务器异常：" + connConfiguration.getIp());
                }
            }
        }

        return rs;
    }

    //判断ftp服务器文件是否存在
    public boolean existFile(String path) throws IOException {
        if(!isLogin) login();

        boolean flag = false;
        FTPFile[] ftpFileArr = ftpClient.listFiles(path);
        if (ftpFileArr.length > 0) {
            flag = true;
        }
        return flag;
    }

    /**
     *
     * @param sourcePath
     * @param isReplace
     * @param destPaths
     * @return
     */
    public $Result uploadFile(String sourcePath, boolean isReplace, String... destPaths) {
        $Result rs = $.result();

        if (!isExists(sourcePath)) return rs.setSuccess(false);

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
                        rs.setSuccess(false).addError(exception(e1));
                        continue;
                    }
                }

                for (String s : sfile.list()) {
                    if (!uploadFile(concat(sourcePath, "/", s), isReplace, concat(destPath, "/", s)).isSuccess()) rs.setSuccess(false);
                }
            } else {
                InputStream is = null;

                try {
                    is = ftpClient.retrieveFileStream(new String(destPath.getBytes("GBK"), FTP.DEFAULT_CONTROL_ENCODING));

                    if (is != null && ftpClient.getReplyCode() != FTPReply.FILE_UNAVAILABLE) { // 如果文件存在
                        if (!isReplace) {
                            warn(String.format("%s is exists!", destPath));
                            rs.setSuccess(true);
                        } else {
                            ftpClient.deleteFile(destPath);
                            rs.setSuccess(uploadFile(sfile, destPath).isSuccess());
                        }
                    }
                } catch (IOException e) {
                    rs.setSuccess(false).addError(exception(e));
                } finally {
                    if (null != is) try {
                        is.close();
                    } catch (IOException e) {
                        warn(e);
                    }
                }
            }
        }

        return rs;
    }

    public $Result uploadFile(File localFile, String... remoteUpLoadPath)  {
        $Result rs = $.result();
        BufferedInputStream inputStream = null;
        boolean success = true;
        String fileName = localFile.getName();

        for(String r: remoteUpLoadPath) {
            try {
                info(concat("上传文件：", fileName, " >>>>>>> ", r));
                ftpClient.changeWorkingDirectory(r.trim()); //改变工作路径
                inputStream = new BufferedInputStream(new FileInputStream(localFile));
                if (ftpClient.storeFile(fileName, inputStream)) {
                    rs.addMessage("上传成功：" + fileName);
                } else {
                    success = false;
                }
            } catch (IOException e) {
                rs.addError($.exception(e)).addMessage("ftp上传文件失败");
            } finally {
                if (null != inputStream){
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        rs.addError($.exception(e)).addMessage("ftp上传文件关闭IO流失败");
                    }
                }
            }
        }

        return rs.setSuccess(success);
    }

    /**
     * 读取最新的文件
     * @param remoteUpLoadPath
     * @return
     */
    public  $Result readNewFile(String remoteUpLoadPath){
        $Result rs = $.result();
        InputStream in = null;
        BufferedReader br = null;

        try {
            info(concat("读取最新的文件：", remoteUpLoadPath));
            if(!login().isSuccess()) return rs.addMessage("未登陆！").setSuccess(false);
            if(!existFile(remoteUpLoadPath)) return rs.addMessage("路径不存在！").setSuccess(false);
            //切换FTP目录
            ftpClient.changeWorkingDirectory(remoteUpLoadPath);
            FTPFile[] ftpFiles = ftpClient.listFiles();
            FTPFile newFile = null;

            for(FTPFile file : ftpFiles){
                if(file.isFile() ){ // 捕获最新的文件
                    if(null == newFile || file.getTimestamp().getTimeInMillis() > newFile.getTimestamp().getTimeInMillis()) newFile = file;
                }
            }
            in = ftpClient.retrieveFileStream(newFile.getName());
            br = new BufferedReader(new InputStreamReader(in,"UTF-8"));
            String line = null;
            StringBuffer sb = new StringBuffer();
            while(null != (line=br.readLine())) {
                if(maxReadLength < sb.length()) {
                    $.warn("终止读取！超过最大读取行数"+maxReadLength+"，请修改maxReadLength配置！文件总大小："+ newFile.getSize());
                    break;
                }
                sb.append(line);
            }
            rs.setData(sb.toString());

            info("读取文件完成");
        } catch (Exception e) {
            rs.addError($.exception(e)).addMessage("ftp读取文件失败");
        } finally{
            if(null != in){
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return rs;
    }

    /**
     * 读取文件
     * @param remoteUpLoadFilePath
     * @return
     */
    public  $Result readFile(String remoteUpLoadFilePath){
        String remoteUpLoadPath = remoteUpLoadFilePath.substring(0, remoteUpLoadFilePath.lastIndexOf("/"));
        String filename = remoteUpLoadFilePath.substring(remoteUpLoadFilePath.lastIndexOf("/")+1, remoteUpLoadFilePath.length());
        return readFile(remoteUpLoadPath, filename);
    }

    /** * 读取ftp文件
     * @param remoteUpLoadPath FTP服务器文件目录 *
     * @return */
    public  $Result readFile(String remoteUpLoadPath, String filename){
        $Result rs = $.result();
        InputStream in = null;
        BufferedReader br = null;

        try {
            info(concat("读取文件：", remoteUpLoadPath + "/" + filename));
            if(!login().isSuccess()) return rs.addMessage("未登陆！").setSuccess(false);
            if(existFile(remoteUpLoadPath)) return rs.addMessage("路径不存在！").setSuccess(false);
            //切换FTP目录
            ftpClient.changeWorkingDirectory(remoteUpLoadPath);
            FTPFile[] ftpFiles = ftpClient.listFiles();
            for(FTPFile file : ftpFiles){
                if(file.isFile() && filename.equals(file.getName())){
                    in = ftpClient.retrieveFileStream(file.getName());
                    br = new BufferedReader(new InputStreamReader(in,"UTF-8"));
                    String line = null;
                    StringBuffer sb = new StringBuffer();
                    while(null != (line=br.readLine())) {
                        sb.append(line);
                    }
                    rs.setData(sb.toString());
                }
            }
            info("读取文件完成");
        } catch (Exception e) {
            rs.addError($.exception(e)).addMessage("ftp读取文件失败");
        } finally{
            if(null != in){
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return rs;
    }

    public $Result listFile(String remotePath) throws IOException {
        $Result rs = $.result();
        try {
            info(concat("获取文件列表：", remotePath));
            if (!login().isSuccess()) return rs.addMessage("未登陆！").setSuccess(false);
            if (existFile(remotePath)) return rs.addMessage("文件不存在！").setSuccess(false);
            //切换FTP目录
            ftpClient.changeWorkingDirectory(remotePath);
            FTPFile[] ftpFiles = ftpClient.listFiles();
            return rs.setData(ftpFiles);
        } catch (Exception e) {
            rs.addError($.exception(e)).addMessage($.warn("获取ftp文件列表失败！"+remotePath));
            throw e;
        }
    }

    /** * 下载文件 *
     * @param remoteUpLoadPath FTP服务器文件目录 *
     * @param filename 文件名称 *
     * @param localpath 下载后的文件路径 *
     * @return */
    public  $Result downloadFile(String remoteUpLoadPath, String localpath, String... filename){
        $Result rs = $.result();
        OutputStream os=null;

        try {
            info(concat("下载文件：", remoteUpLoadPath, " >>>>>>> ", localpath));
            if(!login().isSuccess()) return rs.addMessage("未登陆！").setSuccess(false);
            if(existFile(remoteUpLoadPath)) return rs.addMessage("文件不存在！").setSuccess(false);
            //切换FTP目录
            ftpClient.changeWorkingDirectory(remoteUpLoadPath);
            FTPFile[] ftpFiles = ftpClient.listFiles();
            for(FTPFile file : ftpFiles){
                if(file.isFile()){
                    if(null == filename || filename.length == 0 || Arrays.asList(filename).indexOf(file.getName()) != -1){
                        $.file.mkdir(localpath);
                        File localFile = new File(localpath + "/" + file.getName());
                        os = new FileOutputStream(localFile);
                        ftpClient.retrieveFile(file.getName(), os);
                    }
                } else {
                    downloadFile(remoteUpLoadPath+"/"+file.getName(), localpath+"/"+file.getName(), filename);
                }
            }
            info("下载文件成功");
        } catch (Exception e) {
            rs.addError($.exception(e)).addMessage("ftp下载文件失败");
        } finally{
            if(null != os){
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return rs;
    }

    /** * 下载最新的文件 *
     * @param remoteUpLoadPath FTP服务器文件目录 *
     * @param localpath 下载后的文件路径 *
     * @return */
    public  $Result downloadNewFile(String remoteUpLoadPath, String localpath){
        $Result rs = $.result();
        OutputStream os=null;

        try {
            info(concat("下载文件：", remoteUpLoadPath, " >>>>>>> ", localpath));
            if(!login().isSuccess()) return rs.addMessage("未登陆！").setSuccess(false);
            if(existFile(remoteUpLoadPath)) return rs.addMessage("文件不存在！").setSuccess(false);
            //切换FTP目录
            ftpClient.changeWorkingDirectory(remoteUpLoadPath);
            FTPFile[] ftpFiles = ftpClient.listFiles();
            FTPFile newFile = null;

            for(FTPFile file : ftpFiles){
                if(file.isFile() ){ // 捕获最新的文件
                    if(null == newFile || file.getTimestamp().getTimeInMillis() > newFile.getTimestamp().getTimeInMillis()) newFile = file;
                }
            }

            $.file.mkdir(localpath);
            File localFile = new File(localpath + "/" + newFile.getName());
            os = new FileOutputStream(localFile);
            ftpClient.retrieveFile(newFile.getName(), os);
            info("下载文件成功");
        } catch (Exception e) {
            rs.addError($.exception(e)).addMessage("ftp下载文件失败");
        } finally{
            if(null != os){
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return rs;
    }

    /** * 删除文件 *
     * @param pathname FTP服务器保存目录 *
     * @param filename 要删除的文件名称 *
     * @return */
    public boolean deleteFile(String pathname, String filename){
        boolean flag = false;
        try {
            $.info("开始删除文件");
            if(!login().isSuccess()) return flag;
            //切换FTP目录
            ftpClient.changeWorkingDirectory(pathname);
            ftpClient.dele(filename);
            ftpClient.logout();
            flag = true;
            $.info("删除文件成功");
        } catch (Exception e) {
            $.info("删除文件失败");
            e.printStackTrace();
        } finally {
            if(ftpClient.isConnected()){
                try{
                    ftpClient.disconnect();
                }catch(IOException e){
                    e.printStackTrace();
                }
            }
        }
        return flag;
    }

    public InetAddress getHost() {
        return host;
    }

    public $Ftp setHost(InetAddress host) {
        this.host = host;
        return this;
    }

    public $ConnConfiguration getConnConfiguration() {
        return connConfiguration;
    }

    public $Ftp setConnConfiguration($ConnConfiguration connConfiguration) {
        this.connConfiguration = connConfiguration;
        return this;
    }

    public long getMaxReadLength() {
        return maxReadLength;
    }

    public void setMaxReadLength(long maxReadLength) {
        this.maxReadLength = maxReadLength;
    }
}
