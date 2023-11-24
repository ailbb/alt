package com.ailbb.alt.linux.ssh;

import com.ailbb.ajj.entity.$Result;
import com.ailbb.ajj.entity.$Status;
import com.ailbb.alt.$;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/*
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

    @Override
    public void run() {
        BufferedReader br = null;
        try {
            if(null == inputStream) return;
            br = new BufferedReader(new InputStreamReader(inputStream, character));
            String line = null;
            while ((line = br.readLine()) != null) {
                if (line != null) {
                    $.debugOut(line);

                    if(type.equals("message"))
                        rs.addMessage(line);
                    else
                        rs.addDataList(line);
                }
            }
        } catch (IOException e) {
            rs.addError(e);
            e.printStackTrace();
        } finally {
            $.file.closeStream(inputStream);
            $.file.closeStream(br);
        }
    }
}
