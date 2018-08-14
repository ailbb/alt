package com.ailbb.alt.linux;

public class SSHResult {
	/**成功是否*/
	private boolean success = false;
	/**执行的脚本命令*/
	private String cmd = null;
	/**执行状态,默认为-99*/
	private Integer status = -99;
	/**请求信息*/
	private String message;
	/**错误信息*/
	private String error;

	public SSHResult(String cmd) {
		this.cmd = cmd;
	}

	public Integer getStatus() {
		return status;
	}

	public SSHResult setStatus(Integer status) {
		this.status = status;
		return this;
	}

	public String getMessage() {
		return message;
	}

	public SSHResult setMessage(String message) {
		this.message = message;
		return this;
	}

	public String getError() {
		return error;
	}

	public SSHResult setError(String error) {
		this.error = error;
		return this;
	}

	public boolean isSuccess() {
		return success;
	}

	public SSHResult setSuccess(boolean success) {
		this.success = success;
		return this;
	}

	@Override
	public String toString() {
		return "SSHResult{" +
				"\r\n success=" + success +
				"\r\n cmd='" + cmd + '\'' +
				"\r\n status=" + status +
				"\n\n message='" + message + '\'' +
				"\n\n error='" + error + '\'' +
				"\r\n}";
	}
}
