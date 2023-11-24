package com.ailbb.alt.ftp.impl;

import com.ailbb.ajj.entity.$Progress;
import com.ailbb.ajj.entity.$Result;
import com.ailbb.ajj.entity.$Status;
import com.ailbb.alt.linux.ssh.$SSHInterface;

import java.io.InputStream;

/*
 * Created by Wz on 6/20/2018.
 */
public interface $FtpInterface {
    boolean isConnected();
    $FtpInterface connect() throws Exception;
    $FtpInterface disconnect();

    $Progress getProgress();

    $Result uploadFile(InputStream inputStream, long size, String destPath, String fileName) throws Exception;

}
