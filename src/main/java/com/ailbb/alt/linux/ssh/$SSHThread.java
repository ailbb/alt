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
public class $SSHThread implements Runnable {
    // 设置读取的字符编码
    private String character = "GB2312";
    private $Result rs;
    private InputStream inputStream;
    private String type;

    public $SSHThread(InputStream inputStream, $Result rs) {
        this(inputStream, rs, "data");
    }

    public $SSHThread(InputStream inputStream, $Result rs, String type) {
        this.inputStream = inputStream;
        this.rs = rs;
        this.type = type;
    }

    public void start() {
        Thread thread = new Thread(this);
        thread.setDaemon(true);//将其设置为守护线程
        thread.start();
    }

    public void run() {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(inputStream, character));
            String line = null;
            while ((line = br.readLine()) != null) {
                if (line != null) {
                    $.sout(line);

                    if(type.equals("message"))
                        rs.addMessage(line);
                    else
                        rs.addData(line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            $.file.closeStearm(inputStream);
            $.file.closeStearm(br);
        }
    }
}
