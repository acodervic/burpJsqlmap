package my;

import java.io.InputStreamReader;
import java.io.LineNumberReader;

import burp.BurpExtender;
import burp.BurpExtender.TaskEntry;

public class Util {

	// sqlmapCheckUrl是sqlmap检测的url
	public static String Linuxexec(String cmd) {
		try {
			BurpExtender.burpPinter.print("执行的shell:" + cmd);
			String[] cmdA = { "/bin/sh", "-c", cmd };
			Process process = Runtime.getRuntime().exec(cmdA);
			LineNumberReader br = new LineNumberReader(new InputStreamReader(process.getInputStream()));
			StringBuffer sb = new StringBuffer();
			String line;

			while ((line = br.readLine()) != null) {
				BurpExtender.burpPinter.print(line);
				sb.append(line).append("\n");
			}

			return sb.toString();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}

	// sqlmapCheckUrl是sqlmap检测的url
	public static String startSqlmap(String cmd, BurpExtender.TaskEntry task) {
		try {
			task.sqlmapShell = cmd;
			BurpExtender.burpPinter.print("执行的shell:" + cmd);
			task.appendSqlmapLog(cmd + "=============================================");
			String[] cmdA = { "/bin/sh", "-c", cmd };
			task.sqlmapProcess = Runtime.getRuntime().exec(cmdA);
			LineNumberReader br = new LineNumberReader(new InputStreamReader(task.sqlmapProcess.getInputStream()));
			StringBuffer sb = new StringBuffer();
			String line;

			while ((line = br.readLine()) != null) {
				task.appendSqlmapLog(line + ("\n"));
				sb.append(line).append("\n");
			}

			return sb.toString();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}

}
