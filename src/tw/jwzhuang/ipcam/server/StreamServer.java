package tw.jwzhuang.ipcam.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import tw.jwzhuang.ipcam.RecordService;
import tw.jwzhuang.ipcam.server.SocketServer.WorkerRunnable;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;

public class StreamServer extends Service {

	private final String TAG = this.getClass().getSimpleName();
	private byte[] buffer = null;
	private int startId = -1;
	public int videoWidth = 320;
	public int videoHeight = 240;
	private int videoRate = 20;
	private SocketServer server = null;
	private HandlerThread mThread = null;
	private Handler mThreadHandler;
	private ServiceBinder mBinder = null;
	private RecordService activity = null;
	private List<WorkerRunnable> streamToWorkers = null;
	private SharedPreferences preferences = null;
	
	@Override
	public void onCreate() {
		super.onCreate();
		mBinder = new ServiceBinder();
		streamToWorkers = new ArrayList<WorkerRunnable>();
		preferences = getSharedPreferences("code", MODE_PRIVATE);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		stopServerSocket();
		startServerSocket();
		if(startId != -1){
			this.stopSelf(startId);
		}
		this.startId = startId;
		return super.onStartCommand(intent, START_STICKY, startId);
	}
	
	@Override
	public void onDestroy() {
		stopServerSocket();
		super.onDestroy();
	}
	
	public void exitStreamServer(){
		stopSelf(startId);
	}
	
	public void setB(RecordService recordService) {
		this.activity = recordService;
	}
	
	public void setBuffer(byte[] bf){
		this.buffer = bf;
		
		//add length at byte[0] , byte[1]
		for(int i=0; i< streamToWorkers.size(); i++){
			WorkerRunnable worker = streamToWorkers.get(0);
			worker.sentMsg(buffer);
		}
	}
	
	public void setHeight(int h){
		this.videoHeight = h;
	}
	
	public void setRate(int r){
		this.videoRate  = r;
	}

	public void setWidth(int w){
		this.videoWidth = w;
	}
	
	private void startServerSocket(){
		mThread  = new HandlerThread("name");
        mThread.start();
        
        mThreadHandler=new Handler(mThread.getLooper());
        mThreadHandler.post(server = new SocketServer(this));
	}
	
	public void removeWorker(WorkerRunnable worker) {
		activity.startCacheBuf_StreamServer(-1);
		streamToWorkers.remove(worker);
	}
	
	private void stopServerSocket(){
		
		for(int i = 0;i < streamToWorkers.size(); i++){
			removeWorker(streamToWorkers.get(0));
		}
		
		if(server != null){
			try {
				server.closeServer();
				server = null;
			} catch (IOException e) {
				Log.e(TAG, e.getMessage());
			}
		}
		
		if (mThreadHandler != null) {
            mThreadHandler.removeCallbacksAndMessages(null);
        }

        if (mThread != null) {
            mThread.quit();
        }
        
        activity = null;
	}

	@Override
	public IBinder onBind(Intent arg0) {
		stopServerSocket();
		startServerSocket();
		return mBinder;
	}
	
	public class ServiceBinder extends Binder {
		
		public StreamServer getService(){
			return StreamServer.this;
		}
	}

	public boolean verifyUser(String pwd, WorkerRunnable worker) throws JSONException {
		JSONObject jobj = new JSONObject();
		jobj.put("cmd", "login");
		
		if(preferences.getString("randomcode", "*^(&%)&(%^*(").equals(pwd)){
			jobj.put("state", 0);
		}else{
			jobj.put("state", 1);
		}
		worker.sentMsg(jobj.toString());
		
		return true;
	}

	public void sentVideoParams(WorkerRunnable worker) throws JSONException {
		JSONObject jobj = new JSONObject();
		jobj.put("cmd", "getparams");
		jobj.put("state", 0);
		jobj.put("sps", Base64.encodeToString(activity.getSPS(), Base64.DEFAULT));
		jobj.put("pps", Base64.encodeToString(activity.getPPS(), Base64.DEFAULT));
		jobj.put("rate", videoRate);
		worker.sentMsg(jobj.toString());
	}

	public void sentVideoStream(boolean isSent, WorkerRunnable worker) throws JSONException, InterruptedException {
		JSONObject jobj = new JSONObject();
		jobj.put("cmd", "getstream");
		jobj.put("state", 0);
		worker.sentMsg(jobj.toString());
		Thread.sleep(1000);
		streamToWorkers.add(worker);
		activity.startCacheBuf_StreamServer(1);
	}
	
	public void flashLight(){
		activity.flashLight();
	}
}
