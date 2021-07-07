package com.ailbb.alt.linux.ssh;

import com.ailbb.ajj.entity.$Result;

/*
 * Created by Wz on 7/10/2019.
 */
public interface $SSHInterface {
    boolean isConnected();
    $SSHInterface connect() throws Exception;
    $SSHInterface disconnect();
    $Result executeCmd(String cmd) throws Exception;
}
