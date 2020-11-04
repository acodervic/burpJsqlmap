package jsql;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.alibaba.fastjson.JSON;

import burp.IBurpExtenderCallbacks;
import burp.IContextMenuInvocation;
import burp.IExtensionHelpers;
import burp.IHttpRequestResponse;
import burp.IParameter;
import burp.IRequestInfo;
import burp.BurpExtender;
import burp.BurpExtender.TaskEntry;
import my.BurpPinter;
import my.CallBackBase;
import my.Cookie;
import my.FileUtil;
import my.Util;
import thread.SqlmapStartThread;

public class SqlmapCallback implements CallBackBase {
	private IExtensionHelpers helpers;

	public static int num = 1;// 表格序号
	public static String persistentPath = "~/文档/sqlmap插件持久化数据/";// 文件夹必须存在
	public static String persistentFileName = "持久化数据.txt";
	public static String persistentAbsolutePath;

	public static String filepath = "/tmp/burpsqlmap/";
	public static String sucessStr1 = "back-end DBMS:";
	public static String sucessStr2 = "--cast";
	public static String sucessStr3 = "--hex";

	public static String filedStr1 = "all tested parameters do not appear to be injectable";
	public static String filedStr2 = "401 (Unauthorized) ";// 验证失败

	public static String successShell = "";
	public static String sqlmapGlobalOptions = "--level 3 –random-agent -v 3";

	public static BurpPinter bp = null;
	public static int nowRunningSqlmapTasksNum = 0;
	public static int maxTheadsNum = 15;
	public static List<TaskEntry> nowWaitingTasks = new ArrayList<TaskEntry>();

	@Override
	public void CallBackVoid(IBurpExtenderCallbacks callbacks, IContextMenuInvocation invocation) {
		System.out.println("调用成功！");
		bp = new BurpPinter(callbacks);
		helpers = callbacks.getHelpers();
		bp.print("进入callback!");
		if (invocation != null) {
			IHttpRequestResponse[] messages = invocation.getSelectedMessages();

			// 按请求整合数据包内容
			List<List<IHttpRequestResponse>> requests = repeatDataList(helpers, messages);

			// 对数据包进行最长参数选择。 info:当同一个数据包进行请求多次后，在单次进行任务检测，自动从数据包中提取参数最多的那个请求发送给sqlmap
			List<IHttpRequestResponse> taskRequest = getMostParametersRequests(requests, helpers);

			// 启动sqlmap
			for (IHttpRequestResponse request : taskRequest) {
				try {
					TaskEntry task = createTask(request);
					if (nowRunningSqlmapTasksNum == maxTheadsNum) { // 如果任务已经达到最大上线数，则添加进等待序列
						nowWaitingTasks.add(task);
					} else {
						// 如果当前情况允许[当前允许的sqlmap检测线程没达到10]
						startSqlmap(task);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			BurpExtender.burpExtender.updateTable();

			// 持久化保存已经完成的数据
			// 就是保存LogEntry对象列表

			// sqlmapStartThread.追加数据到file(sqlmapCallback.持久化数据文件绝对路径+"/"+sqlmapCallback.持久化数据文件名称,
			// JSON.toJSONString(BurpExtender.log));

		}
	}

	/**
	 * 过滤出参数最多的请求
	 *
	 * @param requests
	 * @param helpers
	 * @return
	 */
	public static List<IHttpRequestResponse> getMostParametersRequests(List<List<IHttpRequestResponse>> requests,
			IExtensionHelpers helpers) {

		List<IHttpRequestResponse> newRequestList = new ArrayList<IHttpRequestResponse>();
		for (List<IHttpRequestResponse> request : requests) {
			if (request.size() == 1) {
				newRequestList.add(request.get(0));
			} else {
				int parmNum = -1;
				int mostParmsNumRequestIndex = 0;
				// 对同一借口多次请求整理出参数最多的请求
				for (int i = 0; i < request.size(); i++) {
					IHttpRequestResponse req = request.get(i);
					int parmsNum = helpers.analyzeRequest(req.getRequest()).getParameters().size();
					if (parmsNum >= parmNum) {
						parmNum = parmsNum;
						mostParmsNumRequestIndex = i;
					}
				}

				// 添加进列表
				newRequestList.add(request.get(mostParmsNumRequestIndex));
				System.out.println("最大参数个数为:" + parmNum + "个 ");

			}

		}
		return newRequestList;
	}

	// 此方法会将一次性接受所有的数据包集合进行整合，相同数据包(参数不同/自动丢弃没有参数或者只有cookie的数据包)，并将其存放进一个list中，每个请求都是一个list
	public static List<List<IHttpRequestResponse>> repeatDataList(IExtensionHelpers helpers,
			IHttpRequestResponse[] messages) {
		// 所有请求，相同的请求(参数不同的)放在同一个list中
		List<List<IHttpRequestResponse>> newRequests = new ArrayList<List<IHttpRequestResponse>>();
		bp.print("获取到选中信息对象" + messages.length);

		// 第一次循环将相同的放入同一个list
		for (int i = 0; i < messages.length; i++) {
			if (messages[i] == null) {
				continue;
			}
			IRequestInfo request = helpers.analyzeRequest(messages[i]);// 获得数据包信息对象
			List<IParameter> parms = request.getParameters();
			if (parms.size() == 0) {// 没有参数的不处理
				System.out.println("该请求没有参数跳过:" + request.getUrl());
				continue;
			}

			// 循环判断如果当前参数只有cookie则不处理
			List<IParameter> o1 = request.getParameters();
			Boolean hasParms = false;// 是否有除了cookie之外的参数
			for (int p = 0; p < o1.size(); p++) {
				if (o1.get(p).getType() != 2) {// 说明就存在不是cookie的参数了则继续检查
					hasParms = true;
					break;
				}
				;

			}
			if (!hasParms) {
				System.out.println("该请求只有cookie跳过:" + request.getUrl());
				continue;
			}

			// 相同的url请求路径，不包含？和post报文的请求都将视为相同请求

			List<IHttpRequestResponse> newReq = new ArrayList<IHttpRequestResponse>();

			// 先添加当前的请求节点的数据
			newReq.add(messages[i]);
			System.out.println("添加一个数据包:" + new String(messages[i].getRequest()));

			// 进行检查
			try {
				for (int i1 = (i + 1); i1 < (messages.length); i1++) {
					IRequestInfo req = helpers.analyzeRequest(messages[i1]);

					if (request.getUrl().getPath().equals(req.getUrl().getPath())) {
						System.out.println();
						System.out.println("发现 一个重复的数据包" + req.getUrl());
						List<IParameter> o = req.getParameters();
						for (IParameter iParameter : o) {
							System.out.println(iParameter.getName() + "   =  " + iParameter.getValue());
						}

						newReq.add(messages[i1]);
						// 当检测到数据包后会在第二层循环中直接将数据包添加到list.为了防止重复检查将已经添加到list中的数据
						// 包对应的messages的下标的数据置空,下标值为i1
						messages[i1] = null;
					} else {

					}

				}

			} catch (Exception e) {
				// TODO: handle exception
				System.out.println(e);
			}
			newRequests.add(newReq);

		}

		System.out.println("数据包重复整合完毕");
		return newRequests;

	}

	public TaskEntry createTask(IHttpRequestResponse requestAndRepsone) throws Exception {
		System.out.println("启动sqlmap   目前还有" + SqlmapCallback.nowWaitingTasks.size() + "任务处于等待状态！");
		String filename = System.currentTimeMillis() + ".txt";
		Util.Linuxexec("touch " + filepath + filename);
		// 获取请求保存到文件
		byte[] req = requestAndRepsone.getRequest();
		byte[] rep = requestAndRepsone.getResponse();

		String data = new String(req);
		// 对全局cooke做处理
		data = setGlobalCookieToReq(data);

		bp.print("==============");
		bp.print(data);
		// 替换显示请求

		// String[] http数据s = data.split("\n");
		/// for (String string : http数据s) {
		// System.out.println(string);
		// }
		// String url = http数据s[0];
		String url = data.substring(0, data.indexOf("\n"));// 只能通过字符串截取获得数据 不能通过helper！否则会报错！
		// if(url.length()>45) {
		// url=url.substring(0, 25);
		// }
		String host = data.substring(data.indexOf("Host") + 4, data.indexOf("\n", data.indexOf("Host")));
		// tring host = http数据s[1].replaceAll("host", " ").trim();
		// 一行一行的写入
		FileUtil.writeString(data, new File(filepath + filename));

		int row = BurpExtender.tasksList.size();

		BurpExtender.TaskEntry task = new BurpExtender.TaskEntry(num, host + " " + url, "", "等待检测中！",
				requestAndRepsone);
		task.setRequest(data.getBytes());// 设置cookie之后的
		task.filename = filename;
		task.filepath = filepath;
		// 吧修改后的数据包显示在日志中
		task.setSqlmapStartLog(data);
		BurpExtender.tasksList.add(task);
		num++;
		return task;

	}

	public static void startSqlmap(TaskEntry task) {
		try {

			// 启动sqlmap
			SqlmapStartThread sqlmapTaskThead = new SqlmapStartThread(task);
			sqlmapTaskThead.start();
			bp.print(" " + task.filename + " sqlmap已经启动   ");
			task.testStatus = "正在检测";
		} catch (Exception e) {
			bp.printError(e.toString());
			bp.print("一个请求写入失败！！");

		}

	}

	public static String setGlobalCookieToReq(String requestString) {
		String cookieText = BurpExtender.sqlmapGlobalOptionsTextField.getText().replaceAll("Cookie:", "");
		if (cookieText.trim().length() < 3) {
			return requestString;
		}
		System.out.println(3);

		// guest_id=1; from_page_72093089=2; 53ct_10403665823015=123123;
		// 读取到的cooke为 key1=val1;key2=val2 的格式所以先解析全局输入的cooke
		String[] globalCookie = cookieText.split(";");
		List<Cookie> globalCookieList = new ArrayList<Cookie>();
		String cookie = "";
		System.out.println(4);

		for (int i = 0; i < globalCookie.length; i++) {
			cookie = globalCookie[i];
			String key = cookie.split("=")[0];
			String val = cookie.split("=")[1];
			globalCookieList.add(new Cookie(key, val));
		}
		System.out.println(5);

		// 获取数据包中的cookie
		if (requestString.indexOf("Cookie:") >= 0) {// 说明存在cookie可以替换
			String newRequest = "";
			// 找到cookie行
			String[] datas = requestString.split("\n");
			for (String line : datas) {
				if (line.indexOf("Cookie:") >= 0) {// 说明是cookie行
					// 提取老的cookie
					List<Cookie> oldCookie = new ArrayList<Cookie>();
					line = line.replace("Cookie:", "");
					String[] data = line.split(";");
					for (int i = 0; i < data.length; i++) {
						String parm = parm = data[i];

						if (parm.length() >= 2) {
							String key = parm.split("=")[0];
							String val = parm.split("=")[1];
							oldCookie.add(new Cookie(key, val));
						}
					}

					// 重新组成cookie
					String cookieStr = "";

					for (int i = 0; i < globalCookieList.size(); i++) { // a b c d e f h
						boolean a = false;
						int sameIndex = -1;
						for (int i1 = 0; i1 < oldCookie.size(); i1++) { // a b c d
							if (globalCookieList.get(i).getKey().trim()
									.equals(oldCookie.get(i1).getKey().toString().trim())) {
								System.out.println("删除" + oldCookie.get(i1).getKey());
								a = true;
								sameIndex = i1;
								break;
							}

						}
						if (a) {
							oldCookie.remove(sameIndex);
						}
					}

					for (int i = 0; i < oldCookie.size(); i++) {
						globalCookieList.add(oldCookie.get(i));
					}

					// 组成新的cooke字符串
					for (int i = 0; i < globalCookieList.size(); i++) {
						cookieStr = cookieStr
								+ (globalCookieList.get(i).getKey() + "=" + globalCookieList.get(i).getValue() + ";");
					}

					cookieStr = cookieStr.replace("\n", "");
					cookieStr = cookieStr.substring(0, cookieStr.length() - 1);
					System.out.println("==" + cookieStr);

					newRequest += ("Cookie:" + cookieStr + "\n");

				} else {// 拼接非cookie行
					newRequest += line + "\n";
				}
			}

			return newRequest.replaceAll("\n", "\r\n");
		} else {// 直接将cookie加入到数据包中
			String[] lines = requestString.split("\r\n");
			String req = "";
			for (int i = 0; i < lines.length; i++) {
				if (i == 2) {
					req += lines[i] + "\r\n" + cookie + "\r\n";

				} else {
					req += lines[i] + "\r\n";

				}

			}

			System.out.println("原始数据没有cookie" + "添加cookie" + cookie);
			return req;
		}

	}
}
