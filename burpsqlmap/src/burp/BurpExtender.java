package burp;

import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

import com.alibaba.fastjson.JSON;

import jsql.SqlmapCallback;
import my.BurpMenuer;
import my.BurpPinter;
import my.Util;
import thread.SqlmapStartThread;
import thread.StartTaskThread;

public class BurpExtender extends AbstractTableModel implements IBurpExtender, ITab, IMessageEditorController {

	public static BurpPinter burpPinter;
	public static IBurpExtenderCallbacks callbacks;
	private IExtensionHelpers helpers;
	private JSplitPane splitPane;
	private IMessageEditor requestViewer;
	private IMessageEditor responseViewer;
	public static List<TaskEntry> tasksList = new ArrayList<TaskEntry>();
	private IHttpRequestResponse currentlyDisplayedItem;
	public static Table logTable;
	public static BurpExtender burpExtender;
	public static JTextArea sqlmapPrintTextField;// 用来限制sqlmap的输出日志
	public static JTextArea sqlmapGlobalOptionsTextField;// 用来设置sqlmap的全局参数
	public static JTextArea sqlmapTatgetOptoonsTextField;// 用来设置sqlmap的单个目标参数
	public static JTextArea sqlmapGlobalCookiesTextField;// 用来设置sqlmap的请求的全局cookie，重复自动覆盖

	public static TaskEntry nowSelectedLog = null;
	//
	// implement IBurpExtender
	//

	@Override
	public void registerExtenderCallbacks(final IBurpExtenderCallbacks callbacks) {
		this.burpExtender = this;
		this.burpPinter = new BurpPinter(callbacks);
		// keep a reference to our callbacks object
		this.callbacks = callbacks;
		// obtain an extension helpers object
		this.helpers = callbacks.getHelpers();

		// 添加startTaskThread进程
		StartTaskThread tasksProcesser = new StartTaskThread();
		tasksProcesser.start();
		callbacks.registerExtensionStateListener(new IExtensionStateListener() {

			@Override
			public void extensionUnloaded() {// 插件卸载的时候销毁等待任务的线程
				tasksProcesser.waitTask = false;
			}
		});

		// set our extension name
		callbacks.setExtensionName("SQLMAP");
		BurpMenuer popMenu = new BurpMenuer("发送sqlmap", callbacks, new SqlmapCallback());
		callbacks.registerContextMenuFactory(popMenu);
		// 创建插件数据缓存文件夹
		Util.Linuxexec("mkdir " + SqlmapCallback.filepath);
		Util.Linuxexec("mkdir " + SqlmapCallback.persistentPath);
		Util.Linuxexec("touch " + SqlmapCallback.persistentPath + SqlmapCallback.persistentFileName);
		SqlmapCallback.persistentAbsolutePath = Util.Linuxexec("cd " + SqlmapCallback.persistentPath + "   && pwd")
				.replaceAll("\n", "");
		System.out.println("持久化数据文件绝对路径" + SqlmapCallback.persistentAbsolutePath);
		// create our UI
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				// main split pane
				splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

				// table of log entries
				logTable = new Table(BurpExtender.this);
				JScrollPane scrollPane = new JScrollPane(logTable);
				splitPane.setLeftComponent(scrollPane);

				// tabs with request/response viewers
				JTabbedPane tabs = new JTabbedPane();
				requestViewer = callbacks.createMessageEditor(BurpExtender.this, false);
				responseViewer = callbacks.createMessageEditor(BurpExtender.this, false);
				// 添加标签
				tabs.addTab("Request", requestViewer.getComponent());
				tabs.addTab("Response", responseViewer.getComponent());

				// 设置sql运行信息的panle
				JPanel sqlmapoutPanle = new JPanel();
				sqlmapoutPanle.setLayout(new GridLayout(1, 1));
				sqlmapPrintTextField = new JTextArea();

				// 在文本框上添加滚动条
				JScrollPane printTextFieldScrollPane = new JScrollPane(sqlmapPrintTextField);
				sqlmapoutPanle.add(printTextFieldScrollPane);
				tabs.addTab("sql运行信息", sqlmapoutPanle);

				// 设置参数panle
				JPanel panel = new JPanel();
				panel.setLayout(new GridLayout(1, 1));
				sqlmapGlobalOptionsTextField = new JTextArea(SqlmapCallback.sqlmapGlobalOptions);
				// 在文本框上添加滚动条
				panel.add(new JScrollPane(sqlmapGlobalOptionsTextField));
				tabs.addTab("sqlmap全局参数", panel);

				// 添加一个全局cookie的标签
				tabs.addTab("sqlmap全局cookie", sqlmapGlobalCookiesTextField);

				// 设置单一参数tab
				JPanel sqlmapTargetOptionPanel = new JPanel();
				sqlmapTargetOptionPanel.setLayout(new GridLayout(1, 1));
				sqlmapTatgetOptoonsTextField = new JTextArea("无");
				// 在文本框上添加滚动条
				JScrollPane sqlmapTargetOptionScrollPanel = new JScrollPane(sqlmapTatgetOptoonsTextField);
				sqlmapTargetOptionPanel.add(sqlmapTargetOptionScrollPanel);
				tabs.addTab("sqlmap单目标参数", sqlmapTargetOptionPanel);

				sqlmapTatgetOptoonsTextField.addKeyListener(new KeyListener() {

					@Override
					public void keyTyped(KeyEvent e) {

					}

					@Override
					public void keyReleased(KeyEvent e) {
						// 改变内容后刷新
						// 获取选中的对象
						if (nowSelectedLog != null) {
							nowSelectedLog.options = sqlmapTatgetOptoonsTextField.getText();
							burpExtender.burpPinter.print("当前目标参数已经修修改:" + nowSelectedLog.options + "  全局参数:"
									+ sqlmapTatgetOptoonsTextField.getText());
						}

					}

					@Override
					public void keyPressed(KeyEvent e) {
						// TODO 自动生成的方法存根

					}
				});

				// 绑定表格事件
				logTable.addMouseListener(new MouseListener() {

					@Override
					public void mouseReleased(MouseEvent arg0) {
						// TODO 自动生成的方法存根
						if (arg0.getClickCount() == 2) {// 停止sqlmap 或者 开始sqlmap当前行
							// 获取当前选中的目标
							TaskEntry task = getNowSelectTask();
							if (task != null) {
								if (task.sqlmapProcess != null) {
									// 删除掉进程
									if (task.sqlmapProcess.destroyForcibly() != null) {
										burpExtender.burpPinter.print("停止序号为" + task.num + "的sqlmap检测进程成功！");
										task.sqlmapProcess = null;
										task.testStatus = "停止检测";
									} else {
										burpExtender.burpPinter.print("停止序号为" + task.num + "的sqlmap检测进程失败！");
										task.testStatus = "正在检测";
									}
								} else if (task.sqlmapProcess == null) {
									// 重新开启检测
									SqlmapStartThread sqlmapCheckThead = new SqlmapStartThread(task);
									sqlmapCheckThead.start();
									task.testStatus = "正在检测";
									burpExtender.burpPinter.print("序号为" + task.num + "的sqlmap检测进程启动成功！");

								}

								;
							}
						}
					}

					@Override
					public void mousePressed(MouseEvent arg0) {

					}

					@Override
					public void mouseExited(MouseEvent arg0) {

					}

					@Override
					public void mouseEntered(MouseEvent arg0) {

					}

					@Override
					public void mouseClicked(MouseEvent arg0) {

					}
				});
				splitPane.setRightComponent(tabs);

				// customize our UI components
				callbacks.customizeUiComponent(splitPane);
				callbacks.customizeUiComponent(logTable);
				callbacks.customizeUiComponent(scrollPane);
				callbacks.customizeUiComponent(tabs);

				// add the custom tab to Burp's UI
				callbacks.addSuiteTab(BurpExtender.this);
				// 绑定持久化表格();
			}
		});
	}

	//
	// implement ITab
	//

	@Override
	public String getTabCaption() {
		return "sqlmap";
	}

	@Override
	public Component getUiComponent() {
		return splitPane;
	}

	public TaskEntry getNowSelectTask() {
		for (TaskEntry task : tasksList) {
			if (task.selected) {
				return task;
			}
		}
		return null;
	}
	//
	// extend AbstractTableModel
	//

	@Override
	public int getRowCount() {
		return tasksList.size();
	}

	@Override
	public int getColumnCount() {
		return 6;// 定义表格列数
	}

	@Override
	public String getColumnName(int columnIndex) {
		switch (columnIndex) {
			case 0:
				return "序号";
			case 1:
				return "添加时间";
			case 2:
				return "URL";
			case 3:
				return "检测结果";
			case 4:
				return "检测状态";
			case 5:
				return "shell";

			default:
				return "";
		}
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		return String.class;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		TaskEntry logEntry = tasksList.get(rowIndex);

		switch (columnIndex) {
			case 0:
				return logEntry.num + "";
			case 1:
				return logEntry.taskTime;
			case 2:
				return logEntry.url.toString();
			case 3:
				return logEntry.testResults.toString();
			case 4:
				return logEntry.testStatus.toString();
			case 5:
				return logEntry.sqlmapShell.toString();
			default:
				return "";
		}
	}

	//
	// implement IMessageEditorController
	// this allows our request/response viewers to obtain details about the messages
	// being displayed
	//

	@Override
	public byte[] getRequest() {
		return currentlyDisplayedItem.getRequest();
	}

	@Override
	public byte[] getResponse() {
		return currentlyDisplayedItem.getResponse();
	}

	@Override
	public IHttpService getHttpService() {
		return currentlyDisplayedItem.getHttpService();
	}

	//
	// extend JTable to handle cell selection
	//

	public class Table extends JTable {
		public Table(TableModel tableModel) {
			super(tableModel);
		}

		@Override
		public void changeSelection(int row, int col, boolean toggle, boolean extend) {
			// 重新选择重置状态
			for (TaskEntry logEntry : tasksList) {
				logEntry.selected = false;
			}
			// show the log entry for the selected row
			TaskEntry logEntry = tasksList.get(row);
			System.out.println("当前共有" + tasksList.size() + "  传递的row" + row);
			if (logEntry != null) {
				System.out.println("logEntry不为空");
				nowSelectedLog = logEntry;
				// 刷新单一参数文本域
				sqlmapTatgetOptoonsTextField.setText("当前未选中");
				if (nowSelectedLog != null) {
					sqlmapTatgetOptoonsTextField.setText(nowSelectedLog.options);

				}
				// 设置选中状态
				nowSelectedLog.selected = true;
				if (nowSelectedLog.request != null) {
					requestViewer.setMessage(nowSelectedLog.request, true);
					System.out.println("接收到的数据包消息请求为null");
				} else {

				}
				if (nowSelectedLog.repsone != null) {
					responseViewer.setMessage(nowSelectedLog.repsone, true);

				} else {
					System.out.println("接收到的数据包消息响应为null");

				}
				currentlyDisplayedItem = nowSelectedLog.IHttpRequestResponse;
				sqlmapPrintTextField.setText(nowSelectedLog.sqlmapStartLog);
				super.changeSelection(row, col, toggle, extend);
			} else {
				System.out.println("logEntry为空");

			}

			System.out.println("点击结束");
		}

	}

	//
	// class to hold details of each log entry
	//

	public static class TaskEntry {

		public boolean selected = false;// 选中则刷新sql运行状态窗口
		int num;// 序号
		public String url = "没有数据";;
		public String testResults = "没有数据";;
		public String taskTime = "没有数据";;
		public String testStatus = "没有数据";
		byte[] request = "没有数据".toString().getBytes();
		byte[] repsone = "没有数据".toString().getBytes();
		String sqlmapStartLog = "没有数据";;
		public String filename = "没有数据";;
		public String filepath = "没有数据";;
		public String sqlmapShell = "没有数据";;
		public Process sqlmapProcess;
		public String options = "";

		public void setSqlmapStartLog(String log) {
			this.sqlmapStartLog = log;
		}

		public String getSqlmapStartLog() {
			return sqlmapStartLog;
		}

		public boolean isSlected() {
			return selected;
		}

		public void setSelected(boolean selected) {
			this.selected = selected;
		}

		public int getNum() {
			return num;
		}

		public void setNum(int num) {
			this.num = num;
		}

		public String getUrl() {
			return url;
		}

		public void setUrl(String url) {
			this.url = url;
		}

		public String getTestResults() {
			return testResults;
		}

		public void setTestResults(String resultes) {
			this.testResults = resultes;
		}

		public String getFilename() {
			return filename;
		}

		public void setFilename(String filename) {
			this.filename = filename;
		}

		public String getFilepath() {
			return filepath;
		}

		public void setFilepath(String filepath) {
			this.filepath = filepath;
		}

		public String getSqlmapShell() {
			return sqlmapShell;
		}

		public void setSqlmapShell(String sqlmapShell) {
			this.sqlmapShell = sqlmapShell;
		}

		public Process getSqlmapProcess() {
			return sqlmapProcess;
		}

		public void setSqlmapProcess(Process sqlmapProcess) {
			this.sqlmapProcess = sqlmapProcess;
		}

		public IHttpRequestResponse getIHttpRequestResponse() {
			return IHttpRequestResponse;
		}

		public void setIHttpRequestResponse(IHttpRequestResponse iHttpRequestResponse) {
			IHttpRequestResponse = iHttpRequestResponse;
		}

		public void appendSqlmapLog(String txt) {
			this.sqlmapStartLog = this.sqlmapStartLog + txt + "\n";
			// 刷新sqlmap运行状态
			if (selected) {
				sqlmapPrintTextField.append(txt + "\n");
				// 显示最新行
				sqlmapPrintTextField.setCaretPosition(sqlmapPrintTextField.getText().length());
			}
		}

		IHttpRequestResponse IHttpRequestResponse;

		public TaskEntry() {
			super();
		}

		public TaskEntry(int tool, String url, String testResults, String testStatus,
				IHttpRequestResponse IHttpRequestResponse) {
			this.num = tool;
			this.url = url;
			this.testResults = testResults;
			this.testStatus = testStatus;
			this.request = IHttpRequestResponse.getRequest();
			this.repsone = IHttpRequestResponse.getResponse();
			this.IHttpRequestResponse = IHttpRequestResponse;
			this.sqlmapStartLog = "没有运行";
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			String dateString = formatter.format(new Date());
			this.taskTime = dateString;
		}

		/**
		 * @return the selected
		 */
		public boolean isSelected() {
			return selected;
		}

		/**
		 * @return the taskTime
		 */
		public String getTaskTime() {
			return taskTime;
		}

		/**
		 * @param taskTime the taskTime to set
		 */
		public void setTaskTime(String taskTime) {
			this.taskTime = taskTime;
		}

		/**
		 * @return the testStatus
		 */
		public String getTestStatus() {
			return testStatus;
		}

		/**
		 * @param testStatus the testStatus to set
		 */
		public void setTestStatus(String testStatus) {
			this.testStatus = testStatus;
		}

		/**
		 * @return the request
		 */
		public byte[] getRequest() {
			return request;
		}

		/**
		 * @param request the request to set
		 */
		public void setRequest(byte[] request) {
			this.request = request;
		}

		/**
		 * @return the repsone
		 */
		public byte[] getRepsone() {
			return repsone;
		}

		/**
		 * @param repsone the repsone to set
		 */
		public void setRepsone(byte[] repsone) {
			this.repsone = repsone;
		}

		/**
		 * @return the options
		 */
		public String getOptions() {
			return options;
		}

		/**
		 * @param options the options to set
		 */
		public void setOptions(String options) {
			this.options = options;
		}

	}

	public void updateTable() {
		fireTableDataChanged();
	}

	public void bindPersistentTable() {

		try {
			String oldData = readLastLine(
					new File(SqlmapCallback.persistentAbsolutePath + "/" + SqlmapCallback.persistentFileName), "UTF-8")
							.trim();
			System.out.println(oldData);
			burpExtender.tasksList = JSON.parseArray(oldData, TaskEntry.class);
			updateTable();
			System.out
					.println("上一次记录的json" + burpExtender.tasksList + "  当前的记录有" + burpExtender.tasksList.size() + "条");

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static String readLastLine(File file, String charset) throws IOException {
		if (!file.exists() || file.isDirectory() || !file.canRead()) {
			return null;
		}
		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile(file, "r");
			long len = raf.length();
			if (len == 0L) {
				return "";
			} else {
				long pos = len - 1;
				while (pos > 0) {
					pos--;
					raf.seek(pos);
					if (raf.readByte() == '\n') {
						break;
					}
				}
				if (pos == 0) {
					raf.seek(0);
				}
				byte[] bytes = new byte[(int) (len - pos)];
				raf.read(bytes);
				if (charset == null) {
					return new String(bytes);
				} else {
					return new String(bytes, charset);
				}
			}
		} catch (FileNotFoundException e) {
		} finally {
			if (raf != null) {
				try {
					raf.close();
				} catch (Exception e2) {
				}
			}
		}
		return null;
	}

}