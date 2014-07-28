package tw.jwzhuang.ipcam;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import android.graphics.Point;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

public class Utils {
	
	/**
	 * 取得螢幕大小
	 * @param wm WindowManager
	 * @return
	 */
	public static Point getScreenSize(WindowManager wm){
		Display display = wm.getDefaultDisplay();
		Point size = new Point();
		display.getSize(size);
		return size;
	}
	
	/**
	 * 取得裝置的IP位址
	 * 
	 * @return
	 * @throws UnknownHostException
	 */
	public static String getLocalIpAddress() {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()
							&& (inetAddress instanceof Inet4Address)) {
						return inetAddress.getHostAddress().toString();
					}
				}
			}
		} catch (SocketException ex) {
			Log.e("ExFunctions", ex.toString());
		}
		return null;
	}
	
	/**
	 * 產生亂數密碼
	 * @param digitOfSet
	 * @param set
	 * @return String
	 */
	public static String pwdGenerator(int digitOfSet, int set) {
		String newPwd = "";
		String pwdSet = "";
//	        System.Random rand = new System.Random();	// for C#
	        int num = 0;
	        for (int i = 0; i < set; i++) {
	            if(i > 0)
			newPwd += "-";	//各組英數之間的分隔號
		    pwdSet = "";
	            while (pwdSet.length() < digitOfSet)  {
	            	num = (int)(Math.random()*(90-50+1))+50;	// for Java
	                if (num > 57 && num < 65)
	                    continue;	//排除 58~64 這區間的非英數符號
	                else if (num == 79 || num == 73)
	                    continue;	//排除 I 和 O
	                pwdSet += (char)num;	//將數字轉換為字元

	            }
	            newPwd += pwdSet;
	        }
	        return newPwd;
	}
}
