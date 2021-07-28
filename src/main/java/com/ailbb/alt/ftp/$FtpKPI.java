package com.ailbb.alt.ftp;

public class $FtpKPI {
    long writeByte; // 写的大小
    long filecount; // 文件数量
    long record; // 记录行数
    double flow; // 流量（时间，大小）

    public long getWriteByte() {
        return writeByte;
    }

    public void setWriteByte(long writeByte) {
        this.writeByte = writeByte;
    }

    public long getFilecount() {
        return filecount;
    }

    public void setFilecount(long filecount) {
        this.filecount = filecount;
    }

    public long getRecord() {
        return record;
    }

    public void setRecord(long record) {
        this.record = record;
    }

    public double getFlow() {
        return flow;
    }

    public void setFlow(double flow) {
        this.flow = flow;
    }
}
