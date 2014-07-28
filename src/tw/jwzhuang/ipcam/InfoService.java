package tw.jwzhuang.ipcam;

import java.lang.ref.WeakReference;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class InfoService extends Service {

	private WindowManager wm = null;
	private WindowManager.LayoutParams wmParams = null;
	private View view;
	private BroadcastReceiver m_br = null;
	private InfoHandler infoHandler = new InfoHandler(this);
	private int startId = -1;
	private TextView clients_tx = null;
	private TextView ip_tx = null;
	
	@Override
	public void onCreate() {
		super.onCreate();
		view = LayoutInflater.from(this).inflate(R.layout.info, null);
		clients_tx = (TextView) view.findViewById(R.id.clients_tx);
		ip_tx = (TextView) view.findViewById(R.id.ip_tx);
		ip_tx.setText(Utils.getLocalIpAddress());
		createView();
	}

	private void createView() {
		// 获取WindowManager
		wm = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
	
		// 设置LayoutParams(全局变量）相关参数
		wmParams = ((MyApplication) getApplication()).getMywmParams();
		wmParams.type = 4999;
		wmParams.flags |= WindowManager.LayoutParams.FORMAT_CHANGED;
		wmParams.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
		wmParams.gravity = Gravity.LEFT | Gravity.TOP; // 调整悬浮窗口至左上角
//		wmParams.gravity = Gravity.LEFT | Gravity.CENTER; // 调整悬浮窗口至左上角
		// 以屏幕左上角为原点，设置x、y初始值
		wmParams.x = 0;
		// 设置悬浮窗口长宽数据
		wmParams.width = WindowManager.LayoutParams.MATCH_PARENT;
		wmParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
		wmParams.format = PixelFormat.RGBA_8888;
		wm.addView(view, wmParams);
//		view.setVisibility(View.GONE);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if(this.startId > -1){
			stopSelf(this.startId);
		}
		this.startId = startId;
		_RegisterReceiver();
		sendBroadcast(new Intent(IntentType.ClientInfo));
		return super.onStartCommand(intent, Service.START_STICKY, startId);
	}

	@Override
	public void onDestroy() {
		if (m_br != null) {
			unregisterReceiver(m_br);
			m_br = null;
		}
		
		wm.removeView(view);
		android.os.Process.killProcess(android.os.Process.myPid());
		super.onDestroy();
	}
	

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	/**
	 * 註冊動態Receiver
	 */
	private void _RegisterReceiver() {
		m_br = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String strAction = intent.getAction();
				Message msg = Message.obtain();
				if(strAction.equals(IntentType.ExitApp) || strAction.equals(IntentType.ExitClientInfo)){
					msg.obj = CMD.EXITAPP;
				}else if(strAction.equals(IntentType.NewClient)){
					msg.obj = CMD.NEWCLIENT;
					msg.arg1 = intent.getExtras().getInt("clients");
				}
				infoHandler.sendMessage(msg);
			}
		};

		IntentFilter filter = new IntentFilter();
		filter.addAction(IntentType.NewClient);
		filter.addAction(IntentType.ExitApp);
		filter.addAction(IntentType.ExitClientInfo);
		registerReceiver(m_br, filter, null, null);
	}
	
	private static class InfoHandler extends Handler{
		WeakReference<InfoService> srv;  
		
		public InfoHandler(InfoService srv){
			this.srv = new WeakReference<InfoService>(srv);
		}
		
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			
			InfoService s = srv.get();
			
			switch((CMD) msg.obj){
				case EXITAPP:
					s.stopSelf(s.startId);
					break;
				case NEWCLIENT:
					s.clients_tx.setText(String.valueOf(msg.arg1));
					break;
				default:
					break;
			}
		}
		
	}
}