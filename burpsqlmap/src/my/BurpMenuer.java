package my;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenuItem;

import burp.BurpExtender;
import burp.IBurpExtenderCallbacks;
import burp.IContextMenuFactory;
import burp.IContextMenuInvocation;
import burp.IExtensionHelpers;
import burp.IHttpRequestResponse;

public class BurpMenuer implements IContextMenuFactory {
	static IBurpExtenderCallbacks callbacks;// 回调对象是burp和jar通讯的接口
	public static List<JMenuItem> menuItemList = new ArrayList<>();

	public JMenuItem menu;
	public BurpPinter bp;
	CallBackBase callBackObj;

	public BurpMenuer(String name, IBurpExtenderCallbacks callbacks, CallBackBase callBack) {
		this.menu = new JMenuItem(name);
		this.callbacks = callbacks;
		this.bp = new BurpPinter(callbacks);
		this.callBackObj = callBack;
	}

	@Override
	public List<JMenuItem> createMenuItems(final IContextMenuInvocation invocation) {
		List<JMenuItem> listMenuItems = new ArrayList<JMenuItem>();

		// 父级菜单
		JMenuItem jmenu = new JMenuItem("发送到sqlmap");
		jmenu.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				bp.print("创建菜单完毕,开始绑定callbackvoid");
				callBackObj.CallBackVoid(callbacks, invocation);
			}
		});
		listMenuItems.add(jmenu);
		return listMenuItems;

	}

}
