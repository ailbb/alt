package com.ailbb.alt.ftp.impl;

import com.ailbb.ajj.entity.$Progress;
import com.ailbb.alt.$;
import com.ailbb.ajj.entity.$ConnConfiguration;
import com.ailbb.ajj.entity.$Result;
import com.ailbb.ajj.log.$Logger;
import com.ailbb.alt.exception.$FtpException;
import org.apache.commons.net.ftp.*;

import java.io.*;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.TimeZone;

/*
 * Created by Wz on 6/20/2018.
 */
public class $XFtp implements $FtpInterface {
    public $Logger logger = $.logger;
    public static final int $PORT = 21;
    private $ConnConfiguration connConfiguration;
    private InetAddress host;
    private FTPClient ftpClient = null;
    private boolean login = false;
    private long maxReadLength = 10485760; // 最大读取长度10M
    private long compressEnableSize = 10*1024 * 1024L; // 启用压缩大小：10M
    private String tmpSuffix = ".tmp"; // 临时文件后缀
    boolean isCompress = true; // 是否压缩，默认是
    private String compressType = "gzip"; // 默认压缩方式是GZIP
    private String tmpDir = $.getPath() + "/apc/data/ftp_tmp"; // 默认压缩方式是GZIP

    public $XFtp($ConnConfiguration connConfiguration) {
        this.setConnConfiguration(connConfiguration);
    }

    public $XFtp(String host, int port, String userName, String pwd) {
        this(new $ConnConfiguration()
                .setIp(host)
                .setPort(port)
                .setUsername(userName)
                .setPassword(pwd)
        );
    }


    @Override
    public $FtpInterface connect()  { return login(); }

    /*
     * 退出/关闭服务器连接
     */
    @Override
    public $FtpInterface disconnect()  { return this.logout(); }

    @Override
    public $Progress getProgress() {
        return null;
    }

    @Override
    public boolean isConnected()  { return isLogin(); }

    public $FtpInterface login()  {
        $Result rs = $.result();

        if(isLogin()) return this;

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
                throw new $FtpException.$LoginErrorException(rs.setSuccess(login).setCode(reply).addMessage("连接ftp服务器失败, code:\t" + reply));
            }

            login = ftpClient.login(connConfiguration.getUsername(), connConfiguration.getPassword());

            if (login) {
                //设置传输协议
                ftpClient.enterLocalPassiveMode();
                ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
                logger.info("登录FTP服务器:" + connConfiguration.getUsername() + "@" + connConfiguration.getIp()+":"+connConfiguration.getPort());
                ftpClient.setBufferSize(1024 *2);
                ftpClient.setDataTimeout(connConfiguration.getTimeOut());
            } else {
                rs.addMessage(logger.warn("登录服务器失败：" + connConfiguration.getUsername() + "@" + connConfiguration.getIp()+":"+connConfiguration.getPort()));
                rs.setSuccess(false).addMessage(logger.warn("["+ftpClient.getReplyCode()+"]" + "["+ftpClient.getReplyString()+"]"));
            }
        } catch (Exception e) {
            rs.addError(logger.exception(e)).addMessage(logger.warn("登录服务器失败：" + connConfiguration.getUsername() + "@" + connConfiguration.getIp()+":"+connConfiguration.getPort()));
        } finally {
            if(login) $.async(new Runnable() {
                public void run() {
                    try {
                        int i=0;
                        while (login && (i++)*1000 < connConfiguration.getTimeOut()) { // 如果没有主动退出，则等待超时
                            Thread.sleep(1000);
                        }

                        logout();
                    } catch (Exception  e) {
                        rs.addError(logger.exception(e));
                    }
                }
            });
        }


        return this;
    }

    public $FtpInterface logout()  {
        if (null != ftpClient && ftpClient.isConnected()) {
            try {
                if (login && ftpClient.logout()) {
                    login = false;
                    logger.info("成功退出服务器：" + connConfiguration.getIp());
                }
            } catch (IOException e) {
                logger.warn("退出服务器异常：[" + e + "]"+ connConfiguration.getIp());
            } finally {
                try {
                    ftpClient.disconnect();    //关闭ftp服务器连接
                } catch (IOException e) {
                    logger.warn("关闭服务器异常：[" + e + "]"+ connConfiguration.getIp());
                }
            }
        }

        return this;
    }

    //判断ftp服务器文件是否存在
    public boolean existFile(String path) throws IOException {
        if(!login) login();

        boolean flag = false;
        FTPFile[] ftpFileArr = ftpClient.listFiles(path);
        if (ftpFileArr.length > 0) {
            flag = true;
        }
        return flag;
    }

    /*
     *
     * @param sourcePath
     * @param isReplace
     * @param destPaths
     * @return
     */
    public $Result uploadFile(String sourcePath, boolean isReplace, String... destPaths) {
        $Result rs = $.result();

        if (!$.isExists(sourcePath)) return rs.setSuccess(true);

        // format path
        sourcePath = $.getPath(sourcePath);
        File sfile = $.getFile(sourcePath);

        for (String destPath : destPaths) {
            destPath = $.getPath(destPath).trim();
            if (!$.isFile(sourcePath)) {
                try {
                    ftpClient.changeWorkingDirectory(destPath); //改变工作路径
                } catch (IOException e) {
                    try {
                        ftpClient.makeDirectory(destPath);
                    } catch (IOException e1) {
                        rs.setSuccess(false).addError(logger.exception(e1));
                        continue;
                    }
                }

                for (String s : sfile.list()) {
                    if (!uploadFile($.concat(sourcePath, "/", s), isReplace, $.concat(destPath, "/", s)).isSuccess()) rs.setSuccess(false);
                }
            } else {
                InputStream is = null;

                try {
                    is = ftpClient.retrieveFileStream(new String(destPath.getBytes("GBK"), FTP.DEFAULT_CONTROL_ENCODING));

                    if (is != null && ftpClient.getReplyCode() != FTPReply.FILE_UNAVAILABLE) { // 如果获取到输入流，并且FTP已经连接
                        if (!isReplace) { // 如果文件存在
                            logger.warn(String.format("%s is exists!", destPath));
                            rs.setSuccess(true);
                        } else {
                            ftpClient.deleteFile(destPath);
                            rs.setSuccess(uploadFile(sfile, destPath).isSuccess());
                        }
                    }
                } catch (IOException e) {
                    rs.setSuccess(false).addError(logger.exception(e));
                } finally {
                    if (null != is) try {
                        is.close();
                    } catch (IOException e) {
                        logger.warn(e);
                    }
                }
            }
        }

        return rs;
    }

    public $Result uploadFile(File localFile, String... remoteUpLoadPath)  {
        $Result rs = $.result();
        if(!isConnected()) return rs.addMessage($.warn("无法获取连接，执行退出！"));

        BufferedInputStream inputStream = null;
        boolean success = true;
        String fileName = localFile.getName();

        for(String r: remoteUpLoadPath) {
            try {
                rs = uploadFile(inputStream, localFile.length(), r, fileName);
            } finally {
                $.file.closeStream(inputStream);
            }
        }

        return rs.setSuccess(success);
    }

    /*
     * 上传文件
     * @param inputStream 文件流
     * @param size 文件大小
     * @param destPath 目标路径
     * @param fileName 目标文件名
     * @return
     */
    @Override
    public $Result uploadFile(InputStream inputStream, long size, String destPath, String fileName) {
        $Result rs = $.result().setSuccess(false);

        if(!isConnected()) return rs.addMessage($.warn("无法获取连接，执行退出！"));

        $FtpKPI kpi = new $FtpKPI();

        long dt = System.currentTimeMillis();
        logger.info($.concat("上传文件：", fileName, " >>>>>>> ", destPath));

        try {
            // 创建服务器远程目录结构，创建失败直接返回
            if (_createDirecroty(destPath, ftpClient) == UploadFTP.UploadStatus.Create_Directory_Fail)
                return rs.setSuccess(false).addMessage("创建目录失败：" + destPath);

            ftpClient.changeWorkingDirectory(destPath.trim()); //改变工作路径

            File ftpFile = null;

            if(isCompress && size >= compressEnableSize) { // 如果需要压缩，则进行压缩
                logger.info("开始压缩 [" + fileName+"] ：" +$.doubled.toDouble(size/1024/1024, 2)+" MB >>> ");
                ftpFile = zipFile(inputStream, size, fileName);
                logger.info("压缩完成 [" + fileName+"] ：" +$.doubled.toDouble(size/1024/1024, 2)+" MB >>> " + $.doubled.toDouble(ftpFile.length()/1024/1024, 2) + " MB");
                inputStream = new BufferedInputStream(new FileInputStream(ftpFile));
            }

            // 设置以二进制流的方式传输
            ftpClient.enterLocalPassiveMode();
            ftpClient.setControlKeepAliveTimeout(60000);
            ftpClient.setControlKeepAliveReplyTimeout(60000);
            ftpClient.setDataTimeout(60000);
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            ftpClient.setFileTransferMode(FTP.STREAM_TRANSFER_MODE);

            String tmpFileName = fileName.contains(".") ? (fileName.substring(0, fileName.lastIndexOf(".")) + tmpSuffix) : fileName;

            if (ftpClient.storeFile(tmpFileName, inputStream)) {
                ftpClient.rename(tmpFileName, fileName); // 上传完成后，重命名
                rs.setSuccess(true).addMessage(logger.info("上传成功：" + fileName + "/" + (null == ftpFile ? size : ftpFile.length()) ));
                if(null != ftpFile) ftpFile.delete(); // 上传成功后，如果是压缩文件，则删除文件
            } else {
                rs.setSuccess(false).addMessage(logger.info("上传文件失败：" + fileName));
            }

        } catch (IOException e) {
            rs.addError(logger.exception(e)).addMessage("ftp上传文件失败");
        } catch (Exception e) {
            logger.error(e);
        }

        kpi.setWriteByte(size);
        kpi.setFilecount(1);

        return rs.setData(kpi);
    }

    /*
     * 压缩文件
     * @param file
     * @return
     */
    public File zipFile(File file){
        if ("zip".equalsIgnoreCase(compressType)) {
            return $.file.zip(file, tmpDir);
        } else {
            //gzip
            return $.file.gzip(file, tmpDir);
        }
    }

    /*
     * 压缩文件
     * @param inputStream
     * @return
     */
    public File zipFile(InputStream inputStream, long size, String fileName){
        if ("zip".equalsIgnoreCase(compressType)) {
            return $.file.compress.zip(inputStream, size, tmpDir, fileName);
        } else {
            //gzip
            return $.file.compress.gzip(inputStream, size, tmpDir, fileName);
        }
    }

    /*
     * 读取最新的文件
     * @param remoteUpLoadPath
     * @return
     */
    public  $Result readNewFile(String remoteUpLoadPath){
        $Result rs = $.result();
        InputStream in = null;
        BufferedReader br = null;

        try {
            logger.info($.concat("读取最新的文件：", remoteUpLoadPath));
            if(!login().isConnected()) return rs.addMessage("未登录！").setSuccess(false);
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

            rs.addMessage(newFile.getName()); // 保存文件名

            in = ftpClient.retrieveFileStream(newFile.getName());
            br = new BufferedReader(new InputStreamReader(in,"UTF-8"));
            String line = null;
            StringBuffer sb = new StringBuffer();

            while(null != (line=br.readLine())) {
                if(maxReadLength < sb.length()) {
                    logger.warn("终止读取！超过最大读取行数"+maxReadLength+"，请修改maxReadLength配置！文件总大小："+ newFile.getSize());
                    break;
                }
                sb.append(line);
                sb.append("\r\n");
            }
            rs.setData(sb.toString());

            logger.info("读取文件完成");
        } catch (Exception e) {
            rs.addError(logger.exception(e)).addMessage("ftp读取文件失败");
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

    /*
     * 读取文件
     * @param remoteUpLoadFilePath
     * @return
     */
    public  $Result readFile(String remoteUpLoadFilePath){
        String remoteUpLoadPath = remoteUpLoadFilePath.substring(0, remoteUpLoadFilePath.lastIndexOf("/"));
        String filename = remoteUpLoadFilePath.substring(remoteUpLoadFilePath.lastIndexOf("/")+1, remoteUpLoadFilePath.length());
        return readFile(remoteUpLoadPath, filename);
    }

    /* * 读取ftp文件
     * @param remoteUpLoadPath FTP服务器文件目录 *
     * @return */
    public  $Result readFile(String remoteUpLoadPath, String filename){
        $Result rs = $.result();
        InputStream in = null;
        BufferedReader br = null;

        try {
            logger.info($.concat("读取文件：", remoteUpLoadPath + "/" + filename));
            if(!login().isConnected()) return rs.addMessage("未登录！").setSuccess(false);
            if(!existFile(remoteUpLoadPath)) return rs.addMessage("路径不存在！").setSuccess(false);
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
            logger.info("读取文件完成");
        } catch (Exception e) {
            rs.addError(logger.exception(e)).addMessage("ftp读取文件失败");
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
            logger.info($.concat("获取文件列表：", remotePath));
            if (!login().isConnected()) return rs.addMessage("未登录！").setSuccess(false);
            if (!existFile(remotePath)) return rs.addMessage("文件不存在！").setSuccess(false);
            //切换FTP目录
            ftpClient.changeWorkingDirectory(remotePath);
            FTPFile[] ftpFiles = ftpClient.listFiles();
            return rs.setData(ftpFiles);
        } catch (Exception e) {
            rs.addError(logger.exception(e)).addMessage(logger.warn("获取ftp文件列表失败！"+remotePath));
            throw e;
        }
    }

    /* * 下载文件 *
     * @param remoteUpLoadPath FTP服务器文件目录 *
     * @param filename 文件名称 *
     * @param localpath 下载后的文件路径 *
     * @return */
    public  $Result downloadFile(String remoteUpLoadPath, String localpath, String... filename){
        $Result rs = $.result();
        OutputStream os=null;

        try {
            logger.info($.concat("下载文件：", remoteUpLoadPath, " >>>>>>> ", localpath));
            if(!login().isConnected()) return rs.addMessage("未登录！").setSuccess(false);
            if(!existFile(remoteUpLoadPath)) return rs.addMessage("文件不存在！").setSuccess(false);
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
            logger.info("下载文件成功");
        } catch (Exception e) {
            rs.addError(logger.exception(e)).addMessage("ftp下载文件失败");
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

    /* * 下载最新的文件 *
     * @param remoteUpLoadPath FTP服务器文件目录 *
     * @param localpath 下载后的文件路径 *
     * @return */
    public  $Result downloadNewFile(String remoteUpLoadPath, String localpath){
        $Result rs = $.result();
        OutputStream os=null;

        try {
            logger.info($.concat("下载文件：", remoteUpLoadPath, " >>>>>>> ", localpath));
            if(!login().isConnected()) return rs.addMessage("未登录！").setSuccess(false);
            if(!existFile(remoteUpLoadPath)) return rs.addMessage("文件不存在！").setSuccess(false);
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
            logger.info("下载文件成功");
        } catch (Exception e) {
            rs.addError(logger.exception(e)).addMessage("ftp下载文件失败");
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

    /* * 删除文件 *
     * @param pathname FTP服务器保存目录 *
     * @param filename 要删除的文件名称 *
     * @return */
    public boolean deleteFile(String pathname, String filename){
        boolean flag = false;
        try {
            logger.info("开始删除文件");
            if(!login().isConnected()) return flag;
            //切换FTP目录
            ftpClient.changeWorkingDirectory(pathname);
            ftpClient.dele(filename);
            ftpClient.logout();
            flag = true;
            logger.info("删除文件成功");
        } catch (Exception e) {
            logger.info("删除文件失败");
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

    /**
     * 递归创建远程服务器目录
     *
     * @param remote    远程服务器文件绝对路径
     * @param ftpClient FTPClient 对象
     * @return 目录创建是否成功
     * @throws IOException
     */
    private UploadFTP.UploadStatus _createDirecroty(String remote, FTPClient ftpClient) throws IOException {
        UploadFTP.UploadStatus status = UploadFTP.UploadStatus.Create_Directory_Success;
        String directory = remote;
        if (!directory.equalsIgnoreCase("/") && !ftpClient.changeWorkingDirectory(new String(directory.getBytes("GBK"), "iso-8859-1"))) {
            // 如果远程目录不存在，则递归创建远程服务器目录
            int start = 0;
            int end = 0;
            if (directory.startsWith("/")) {
                start = 1;
                ftpClient.changeWorkingDirectory("/");
            } else {
                start = 0;
            }
            end = directory.indexOf("/", directory.startsWith("/") ? 1 : 0);
            if(end == -1) end = directory.length();
            while (true) {
                String subDirectory = new String(remote.substring(start, end).getBytes("GBK"), "iso-8859-1");
                if (!ftpClient.changeWorkingDirectory(subDirectory)) {
                    if (ftpClient.makeDirectory(subDirectory)) {
                        ftpClient.changeWorkingDirectory(subDirectory);
                    } else {
                        logger.warn(String.format("创建目录失败：[%s] ", subDirectory));
                        logger.error(String.format("[%s] [%s]", ftpClient.getReplyCode(), ftpClient.getReplyString()));
                        return UploadFTP.UploadStatus.Create_Directory_Fail;
                    }
                }

                start = end + 1;
                end = directory.indexOf("/", start);

                // 检查所有目录是否创建完毕
                if (end <= start) {
                    break;
                }
            }
        }
        return status;
    }

    public InetAddress getHost() {
        return host;
    }

    public $XFtp setHost(InetAddress host) {
        this.host = host;
        return this;
    }

    public $ConnConfiguration getConnConfiguration() {
        return connConfiguration;
    }

    public $XFtp setConnConfiguration($ConnConfiguration connConfiguration) {
        this.connConfiguration = connConfiguration;
        return this;
    }

    public long getMaxReadLength() {
        return maxReadLength;
    }

    public void setMaxReadLength(long maxReadLength) {
        this.maxReadLength = maxReadLength;
    }

    public FTPClient getFtpClient() {
        return ftpClient;
    }

    public void setFtpClient(FTPClient ftpClient) {
        this.ftpClient = ftpClient;
    }

    public String getTmpSuffix() {
        return tmpSuffix;
    }

    public $XFtp setTmpSuffix(String tmpSuffix) {
        this.tmpSuffix = tmpSuffix;
        return this;
    }

    public boolean isCompress() {
        return isCompress;
    }

    public $XFtp setCompress(boolean compress) {
        isCompress = compress;
        return this;
    }

    public String getCompressType() {
        return compressType;
    }

    public $XFtp setCompressType(String compressType) {
        this.compressType = compressType;
        return this;
    }

    public boolean isLogin() {
        return login;
    }

    public String getTmpDir() {
        return tmpDir;
    }

    public $XFtp setTmpDir(String tmpDir) {
        this.tmpDir = tmpDir;
        return this;
    }

    public $Logger getLogger() {
        return logger;
    }

    public void setLogger($Logger logger) {
        this.logger = logger;
    }

    public long getCompressEnableSize() {
        return compressEnableSize;
    }

    public $XFtp setCompressEnableSize(long compressEnableSize) {
        this.compressEnableSize = compressEnableSize;
        return this;
    }
}
