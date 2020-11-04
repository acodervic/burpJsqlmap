package thread;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import com.alibaba.fastjson.JSON;

import burp.BurpExtender;
import jsql.SqlmapCallback;
import my.Util;

//用来启动sqlmap的线程对象
public class SqlmapStartThread extends Thread {

	BurpExtender.TaskEntry LogEntry;

	public SqlmapStartThread(BurpExtender.TaskEntry LogEntry) {

		this.LogEntry = LogEntry;

	}

	@Override
	public void run() {
		SqlmapCallback.nowRunningSqlmapTasksNum = SqlmapCallback.nowRunningSqlmapTasksNum + 1;
		String resultString = Util.startSqlmap(
				"sqlmap  -l " + LogEntry.filepath + LogEntry.filename + "  --batch " + SqlmapCallback.successShell + " "
						+ BurpExtender.sqlmapGlobalOptionsTextField.getText() + " " + LogEntry.options,
				LogEntry);
		// 检测完毕
		LogEntry.testStatus = "检测完毕,请查看结果";
		// 查询检测结果
		if (resultString.indexOf(SqlmapCallback.sucessStr1) >= 0) {
			LogEntry.testResults = "sqlmap利用成功!";
			// 添加一个问题到当前站点地图
			// BurpExtender.callbacks.addScanIssue(new );

		}
		if (resultString.indexOf(SqlmapCallback.sucessStr2) >= 0
				&& resultString.indexOf(SqlmapCallback.sucessStr3) >= 0) {
			LogEntry.testResults = "sqlmap利用成功!(cast/hex)";

		}
		if (resultString.indexOf(SqlmapCallback.filedStr1) >= 0) {
			LogEntry.testResults = "sqlmap利用失败!";
		}
		if (resultString.indexOf(SqlmapCallback.filedStr2) >= 0) {
			LogEntry.testResults = "sqlmap利用失败!(401未授权)";

		}
		SqlmapCallback.nowRunningSqlmapTasksNum = SqlmapCallback.nowRunningSqlmapTasksNum - 1;
		// 持久化保存已经完成的数据
		// 就是保存LogEntry对象列表
		// 追加数据到file(sqlmapCallback.持久化数据文件绝对路径+"/"+sqlmapCallback.持久化数据文件名称,
		// JSON.toJSONString(BurpExtender.log));

		BurpExtender.burpExtender.updateTable();
		super.run();
	}

}
