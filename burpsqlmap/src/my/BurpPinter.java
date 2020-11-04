package my;

import java.io.PrintWriter;

import burp.IBurpExtenderCallbacks;

public class BurpPinter {
	PrintWriter stdout;
	PrintWriter stderr;
	IBurpExtenderCallbacks callbacks;// 回调对象是burp和jar通讯的接口

	public BurpPinter(IBurpExtenderCallbacks callbacks) {
		callbacks = callbacks;
		stdout = new PrintWriter(callbacks.getStdout(), true);
		stderr = new PrintWriter(callbacks.getStderr(), true);
	}

	public void print(String mes) {
		stdout.println(mes);
	}

	public void printError(String mes) {
		stderr.println(mes);
	}

}
