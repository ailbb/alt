package com.ailbb.alt.ftp.impl;

import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.*;

public class UploadFTP {
    // 枚举类UploadStatus代码
    public enum UploadStatus {
        Uploading, // 上传中
        Create_Directory_Fail, // 远程服务器相应目录创建失败
        Create_Directory_Success, // 远程服务器闯将目录成功
        Upload_New_File_Success, // 上传新文件成功
        Upload_New_File_Failed, // 上传新文件失败
        File_Exits, // 文件已经存在
        Remote_Bigger_Local, // 远程文件大于本地文件
        Upload_From_Break_Success, // 断点续传成功
        Upload_From_Break_Failed, // 断点续传失败
        Delete_Remote_Faild; // 删除远程文件失败
    }

    // 枚举类DownloadStatus代码
    public enum DownloadStatus {
        Remote_File_Noexist, // 远程文件不存在
        Local_Bigger_Remote, // 本地文件大于远程文件
        Download_From_Break_Success, // 断点下载文件成功
        Download_From_Break_Failed, // 断点下载文件失败
        Download_New_Success, // 全新下载文件成功
        Download_New_Failed; // 全新下载文件失败
    }

    public FTPClient ftpClient = new FTPClient();
    private String ftpURL;
    private String userName;
    private String pwd;
    private int port;
    private String srcFile;
    private String destFile;
    private UploadStatus uploadStatus = UploadStatus.Uploading;
    private long process;

    public UploadFTP() {
        // 设置将过程中使用到的命令输出到控制台
        this.ftpClient.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));
    }

    /**
     * * 连接到FTP服务器
     *
     * @param hostname 主机名
     * @param port     端口
     * @param username 用户名
     * @param password 密码
     * @return 是否连接成功
     * @throws IOException
     */
    public boolean connect(String hostname, int port, String username, String password) throws IOException {
        ftpClient.setControlEncoding("GBK");
        ftpClient.enterLocalPassiveMode();
        ftpClient.setControlKeepAliveTimeout(60000);
        ftpClient.setControlKeepAliveReplyTimeout(60000);
        ftpClient.setDataTimeout(60000);
        ftpClient.connect(hostname, port);
        if (FTPReply.isPositiveCompletion(ftpClient.getReplyCode())) {
            if (ftpClient.login(username, password)) {
                return true;
            }
        }
        disconnect();
        return false;
    }

    /**
     * * 断开与远程服务器的连接
     *
     * @throws IOException
     */
    public void disconnect() throws IOException {
        if (ftpClient.isConnected()) {
            ftpClient.logout();
            ftpClient.disconnect();
        }
    }

    /**
     * 从FTP服务器上下载文件,支持断点续传，上传百分比汇报
     *
     * @param remote 远程文件路径
     * @param local  本地文件路径
     * @return 上传的状态
     * @throws IOException
     */
    public DownloadStatus download(String remote, String local) throws IOException {
        // 设置以二进制方式传输
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        DownloadStatus result;

        // 检查远程文件是否存在
        FTPFile[] files = ftpClient.listFiles(new String(remote.getBytes("GBK"), "iso-8859-1"));
        if (files.length != 1) {
            System.out.println("远程文件不存在");
            return DownloadStatus.Remote_File_Noexist;
        }

        long lRemoteSize = files[0].getSize();
        File f = new File(local);
        // 本地存在文件，进行断点下载
        if (f.exists()) {
            long localSize = f.length();
            // 判断本地文件大小是否大于远程文件大小
            if (localSize >= lRemoteSize) {
                System.out.println("本地文件大于远程文件，下载中止");
                return DownloadStatus.Local_Bigger_Remote;
            }

            // 进行断点续传，并记录状态
            FileOutputStream out = new FileOutputStream(f, true);
            ftpClient.setRestartOffset(localSize);
            InputStream in = ftpClient.retrieveFileStream(new String(remote.getBytes("GBK"), "iso-8859-1"));
            byte[] bytes = new byte[1024];
            long step = lRemoteSize / 100;
            long process = localSize / step;
            int c;
            while ((c = in.read(bytes)) != -1) {
                out.write(bytes, 0, c);
                localSize += c;
                long nowProcess = localSize / step;
                if (nowProcess > process) {
                    process = nowProcess;
                    if (process % 10 == 0)
                        System.out.println("下载进度：" + process);
                    // TODO 更新文件下载进度,值存放在process变量中
                }
            }
            in.close();
            out.close();
            boolean isDo = ftpClient.completePendingCommand();
            if (isDo) {
                result = DownloadStatus.Download_From_Break_Success;
            } else {
                result = DownloadStatus.Download_From_Break_Failed;
            }
        } else {
            OutputStream out = new FileOutputStream(f);
            InputStream in = ftpClient.retrieveFileStream(new String(remote.getBytes("GBK"), "iso-8859-1"));
            byte[] bytes = new byte[1024];
            long step = lRemoteSize / 100;
            long process = 0;
            long localSize = 0L;
            int c;
            while ((c = in.read(bytes)) != -1) {
                out.write(bytes, 0, c);
                localSize += c;
                long nowProcess = localSize / step;
                if (nowProcess > process) {
                    process = nowProcess;
                    if (process % 10 == 0) {
                        System.out.println("下载进度：" + process);
                        this.process = process;
                    }
                    // TODO 更新文件下载进度,值存放在process变量中
                }
            }
            in.close();
            out.close();
            boolean upNewStatus = ftpClient.completePendingCommand();
            if (upNewStatus) {
                result = DownloadStatus.Download_New_Success;
            } else {
                result = DownloadStatus.Download_New_Failed;
            }
        }
        return result;
    }

    /*
     * 对服务器的文件进行重命名 from:源文件 to:目标文件
     */
    public boolean rename(String from, String to) throws IOException {
        return ftpClient.rename(from, to);
    }

    /**
     * 上传文件到FTP服务器，支持断点续传
     *
     * @param local  本地文件名称，绝对路径
     * @param remote 远程文件路径，使用/home/directory1/subdirectory/file.ext或是
     *               http://www.guihua.org /subdirectory/file.ext
     *               按照Linux上的路径指定方式，支持多级目录嵌套，支持递归创建不存在的目录结构
     * @return 上传结果
     * @throws IOException
     */
    public UploadStatus upload(String local, String remote) throws IOException {
        ftpClient.enterLocalPassiveMode();
        // 设置以二进制流的方式传输
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        UploadStatus result;
        // 对远程目录的处理
        String remoteFileName = remote;
        if (remote.contains("/")) {
            remoteFileName = remote.substring(remote.lastIndexOf("/") + 1);
            // 创建服务器远程目录结构，创建失败直接返回
            if (_createDirecroty(remote, ftpClient) == UploadStatus.Create_Directory_Fail) {
                return UploadStatus.Create_Directory_Fail;
            }
        }

        // 检查远程是否存在文件
        FTPFile[] files = ftpClient.listFiles(new String(remoteFileName.getBytes("GBK"), "iso-8859-1"));
        if (files.length == 1) {
            long remoteSize = files[0].getSize();
            File f = new File(local);
            long localSize = f.length();
            if (remoteSize == localSize) {
                return UploadStatus.File_Exits;
            } else if (remoteSize > localSize) {
                return UploadStatus.Remote_Bigger_Local;
            }

            // 尝试移动文件内读取指针,实现断点续传
            result = _uploadFile(remoteFileName, f, ftpClient, remoteSize);

            // 如果断点续传没有成功，则删除服务器上文件，重新上传
            if (result == UploadStatus.Upload_From_Break_Failed) {
                if (!ftpClient.deleteFile(remoteFileName)) {
                    return UploadStatus.Delete_Remote_Faild;
                }
                result = _uploadFile(remoteFileName, f, ftpClient, 0);
            }
        } else {
            result = _uploadFile(remoteFileName, new File(local), ftpClient, 0);
        }
        return result;
    }

    public boolean upload(InputStream in, String remote) throws IOException {
        // 设置以二进制流的方式传输
        ftpClient.enterLocalPassiveMode();
        ftpClient.setControlKeepAliveTimeout(60000);
        ftpClient.setControlKeepAliveReplyTimeout(60000);
        ftpClient.setDataTimeout(60000);
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        ftpClient.setFileTransferMode(FTP.STREAM_TRANSFER_MODE);
        // 对远程目录的处理
        if (remote.contains("/")) {
            // 创建服务器远程目录结构，创建失败直接返回
            if (_createDirecroty(remote, ftpClient) == UploadStatus.Create_Directory_Fail)
                return false;
            remote = remote.substring(remote.lastIndexOf("/") + 1);
        }
        return ftpClient.storeFile(remote, in);
    }

    /**
     * 递归创建远程服务器目录
     *
     * @param remote    远程服务器文件绝对路径
     * @param ftpClient FTPClient 对象
     * @return 目录创建是否成功
     * @throws IOException
     */
    private UploadStatus _createDirecroty(String remote, FTPClient ftpClient) throws IOException {
        UploadStatus status = UploadStatus.Create_Directory_Success;
        String directory = remote.substring(0, remote.lastIndexOf("/") + 1);
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
            while (true) {
                String subDirectory = new String(remote.substring(start, end).getBytes("GBK"), "iso-8859-1");
                if (!ftpClient.changeWorkingDirectory(subDirectory)) {
                    if (ftpClient.makeDirectory(subDirectory)) {
                        ftpClient.changeWorkingDirectory(subDirectory);
                    } else {
                        System.out.println("创建目录失败");
                        return UploadStatus.Create_Directory_Fail;
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

    /**
     * 上传文件到服务器,新上传和断点续传
     *
     * @param remoteFile 远程文件名，在上传之前已经将服务器工作目录做了改变
     * @param localFile  本地文件 File句柄，绝对路径
     * @param remoteSize 需要显示的处理进度步进值
     * @param ftpClient  FTPClient 引用
     * @return
     * @throws IOException
     */
    private UploadStatus _uploadFile(String remoteFile, File localFile, FTPClient ftpClient, long remoteSize) throws IOException {
        UploadStatus status;
        // 显示进度的上传
        long step = localFile.length() / 100;
        if (step == 0) {
            step = 1;
        }
        long process = 0;
        long localreadbytes = 0L;
        RandomAccessFile raf = new RandomAccessFile(localFile, "r");
        OutputStream out = ftpClient.appendFileStream(new String(remoteFile.getBytes("GBK"), "iso-8859-1"));
        if (out == null) {
            ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
            ftpClient.storeFile(remoteFile, bais);
            bais.close();
            out = ftpClient.appendFileStream(new String(remoteFile.getBytes("GBK"), "iso-8859-1"));
        }
        // 断点续传
        if (remoteSize > 0) {
            ftpClient.setRestartOffset(remoteSize);
            process = remoteSize / step;
            raf.seek(remoteSize);
            localreadbytes = remoteSize;
        }
        byte[] bytes = new byte[1024];
        int c;
        while ((c = raf.read(bytes)) != -1) {
            out.write(bytes, 0, c);
            localreadbytes += c;
            if (localreadbytes / step != process) {
                process = localreadbytes / step;
                System.out.println("上传进度:" + process + "%");
                this.process = process;
                // TODO 汇报上传状态
            }
        }
        out.flush();
        raf.close();
        out.close();
        boolean result = ftpClient.completePendingCommand();
        if (remoteSize > 0) {
            status = result ? UploadStatus.Upload_From_Break_Success : UploadStatus.Upload_From_Break_Failed;
        } else {
            status = result ? UploadStatus.Upload_New_File_Success : UploadStatus.Upload_New_File_Failed;
        }
        this.uploadStatus = status;
        return status;
    }

    /**
     * 异步上传
     */
    public void asynchUpload(String local, String remote) {
        this.srcFile = local;
        this.destFile = remote;
        Thread t = new Thread() {
            public void run() {
                try {
                    synchUpload(srcFile, destFile);
                } catch (Exception e) {
                    // TODO: handle exception
                }
            }

        };
        t.start();
    }

    public long download() {
        // TODO Auto-generated method stub
        return 0;
    }

    public long getDownloadProcess() {
        // TODO Auto-generated method stub
        return 0;
    }

    public String getDownloadStatus() {
        // TODO Auto-generated method stub
        return null;
    }

    public long getUploadProcess() {
        return this.process;
    }

    public String getUploadStatus() {
        return this.uploadStatus.name();
    }

    public long synchUpload(String local, String remote) {
        boolean finish = false;
        while (!finish) {
            try {
                this.connect(ftpURL, port, userName, pwd);
                this.upload(srcFile, destFile);
                finish = true;
            } catch (Exception e) {
                System.out.println(e);
                System.out.println("开始续传...");
            } finally {
                try {
                    this.disconnect();
                    Thread.sleep(5000);
                } catch (Exception ex) {
                    System.out.println(ex);
                }
            }
        }
        return this.process;
    }
}
