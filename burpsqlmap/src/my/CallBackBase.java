package my;

import burp.IBurpExtenderCallbacks;
import burp.IContextMenuInvocation;

public interface CallBackBase {

	// 此方法是所有回调的入口
	public void CallBackVoid(IBurpExtenderCallbacks callbacks, IContextMenuInvocation invocation);
}
