package thread;

import jsql.SqlmapCallback;

/**
 * 
 * @author w sqlmap等待的进程
 */
public class StartTaskThread extends Thread {
	public static boolean waitTask = true;

	@Override
	public void run() {
		// 一个任务完成之后,进行 等待任务的执行
		while (waitTask) {
			System.out.println("启动sqlmap  当前的任务数量:" + SqlmapCallback.nowRunningSqlmapTasksNum + "   目前还有"
					+ SqlmapCallback.nowWaitingTasks.size() + "任务处于等待状态！");

			try {
				Thread.sleep(3000);
				if (SqlmapCallback.nowRunningSqlmapTasksNum < SqlmapCallback.maxTheadsNum
						&& SqlmapCallback.nowWaitingTasks.size() != 0) { // 如果任务已经达到最大上线数，则添加进等待序列
					// 锁住
					// 找到第一个,任务并启动线程
					SqlmapCallback.startSqlmap(SqlmapCallback.nowWaitingTasks.get(0));
					SqlmapCallback.nowWaitingTasks.remove(0);
				}
			} catch (Exception e) {
				System.out.println(e);
			}

		}
		System.out.println("退出插件成功，销毁任务检测线程");
	}

}
