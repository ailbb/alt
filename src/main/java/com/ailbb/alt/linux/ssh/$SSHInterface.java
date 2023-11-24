package com.ailbb.alt.linux.ssh;

import com.ailbb.ajj.entity.$Result;
import com.ailbb.ajj.entity.$Status;

/*
 * Created by Wz on 7/10/2019.
 */
public interface $SSHInterface {
    boolean isConnected();
    $SSHInterface connect() throws Exception;
    $SSHInterface disconnect();
    $Result execCmd(String cmd) throws Exception;
    $Result batchExecuteCmd(String... cmd) throws Exception;
    $Result batchExecuteCmdSudo(String... cmd) throws Exception;

    $Status getStatus();
    $Result getResult();
}
