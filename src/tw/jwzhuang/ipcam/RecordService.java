package tw.jwzhuang.ipcam;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;

import tw.jwzhuang.ipcam.h264.H264Header;
import tw.jwzhuang.ipcam.h264.H264Protocol;
import tw.jwzhuang.ipcam.h264.ParcelableByteArray;
import tw.jwzhuang.ipcam.server.StreamServer;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.media.MediaRecorder;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.view.ext.SatelliteMenu;
import android.view.ext.SatelliteMenuItem;
import android.widget.FrameLayout;


public class RecordService extends Service implements SurfaceHolder.Callback, MediaRecorder.OnErrorListener,
MediaRecorder.OnInfoListener{

	private WindowManager wm = null;
	private WindowManager.LayoutParams wmParams = null;
	private View view;
	private BroadcastReceiver m_br = null;
	private RecordHandler recHandler = new RecordHandler();
	private int startId = -1;
	
	private final String TAG = "RecordService";
	private final byte[] SOI_MARKER = { (byte) 0xFF, (byte) 0xD8 };
	private final byte[] EOF_MARKER = { (byte) 0xFF, (byte) 0xD9 };
	private LocalSocket receiver, sender;
	private LocalServerSocket lss;
	private MediaRecorder mMediaRecorder = null;
	private boolean mMediaRecorderRecording = false;
	private SurfaceView mSurfaceView = null;
	private SurfaceHolder mSurfaceHolder = null;
	private boolean startRecording = false;
	private List<ParcelableByteArray> bufferList = null;
	private Thread t;
	private H264Header h264Header = null;
//	private int videoWidth = 176;
//	private int videoHeight = 144;
	private int videoWidth = 320;
	private int videoHeight = 240;
	private int videoRate = 30; //至少20張解碼才正常
	private SharedPreferences sharedPreferences;
	private final String mediaShare = "media";
	private String fd = Environment.getExternalStorageDirectory()+"/videotest.mp4";
	private final int MAXFRAMEBUFFER = 2048*videoRate;//*10;//20K
	private ServiceConnection mServiceConn = null;
	private StreamServer mStreamServer = null;
	private int cacheBufferforStreamServer = 0;
	private boolean cacheBufferforWriteFile = false;
	private boolean createFile = false;
	private Camera camera = null;
	private Parameters mParameters = null;
	private boolean isLighOn = false;
	private WakeLock wl = null;
	private FrameLayout fLayout = null;
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		PowerManager pm = ((PowerManager)getSystemService(Context.POWER_SERVICE));
		wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BackLight");
		
		sharedPreferences = this.getSharedPreferences(mediaShare, MODE_PRIVATE);
		createView();
		
		bufferList = new ArrayList<ParcelableByteArray>();
		mSurfaceView = (SurfaceView) view.findViewById(R.id.surface_camera);
		SurfaceHolder holder = mSurfaceView.getHolder();
		holder.addCallback(this);
		if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
			holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		mSurfaceView.setVisibility(View.VISIBLE);
		
		initializeMenu();
		
		initializeServiceConnection();
		if(mStreamServer == null){
			bindService(new Intent(this,StreamServer.class), mServiceConn, BIND_AUTO_CREATE);
		}
		
	}

	private void createView() {
		// 获取WindowManager
		wm = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
		// 设置LayoutParams(全局变量）相关参数
		wmParams = ((MyApplication) getApplication()).getMywmParams();
		wmParams.type = 5000;
		wmParams.flags |= WindowManager.LayoutParams.FORMAT_CHANGED;
		wmParams.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
		wmParams.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
		wmParams.gravity = Gravity.CENTER | Gravity.TOP; // 调整悬浮窗口至左上角
		// 以屏幕左上角为原点，设置x、y初始值
		wmParams.x = 0;
		// 设置悬浮窗口长宽数据
		wmParams.width = WindowManager.LayoutParams.MATCH_PARENT;
		wmParams.height = WindowManager.LayoutParams.MATCH_PARENT;
		wmParams.format = PixelFormat.RGBA_8888;
		
		view = LayoutInflater.from(this).inflate(R.layout.main, null);
        
		wm.addView(view, wmParams);
		view.setVisibility(View.INVISIBLE);
		view.setVisibility(View.VISIBLE);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if(this.startId > -1){
			stopSelf(this.startId);
		}
		
		this.startId = startId;
		_RegisterReceiver();
		
		if(initializeLocalSocket() == false){
			this.stopSelf(startId);
		}
		
		if (wl != null)
	    	wl.acquire();
		
		return super.onStartCommand(intent, Service.START_STICKY, startId);
	}

	@Override
	public void onDestroy() {
		
		if (wl != null){
			wl.release();
		}	
		
		cacheBufferforStreamServer = 0;
		cacheBufferforWriteFile = false;
		if(mStreamServer != null){
			mStreamServer.exitStreamServer();
			mStreamServer = null;
			unbindService(mServiceConn);
		}
		
		if (mMediaRecorderRecording) {
			h264Header = null;
			stopVideoRecording();
			try {
				lss.close();
				receiver.close();
				sender.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		mSurfaceView = null;
		mSurfaceHolder = null;
		mMediaRecorder = null;
		if (t != null) {
			startRecording = false;
		}
		
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
				if(strAction.equals(IntentType.ExitApp)){
					msg.obj = CMD.EXITAPP;
				}else if(strAction.equals(IntentType.ClientInfo)){
					msg.obj = CMD.CLIENTINFO;
				}
				recHandler.sendMessage(msg);
			}
		};

		IntentFilter filter = new IntentFilter();
		filter.addAction(IntentType.ExitApp);
		filter.addAction(IntentType.ClientInfo);
		registerReceiver(m_br, filter, null, null);
	}
	
	private class RecordHandler extends Handler{
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			CMD cmd = (CMD) msg.obj;
			switch(cmd){
			case EXITAPP:
				stopSelf(startId);
				break;
			case CLIENTINFO:
				Intent it = new Intent(IntentType.NewClient);
				it.putExtra("clients", cacheBufferforStreamServer);
				sendBroadcast(it);
				break;
			}
		}
		
	}

	@Override
	public void onInfo(MediaRecorder mr, int what, int extra) {
		switch (what) {
		case MediaRecorder.MEDIA_RECORDER_INFO_UNKNOWN:
			System.out.println("MEDIA_RECORDER_INFO_UNKNOWN");
			break;
		case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
			System.out.println("MEDIA_RECORDER_INFO_MAX_DURATION_REACHED");
			break;
		case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED:
			System.out.println("MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED");
			break;
		}
	}

	@Override
	public void onError(MediaRecorder mr, int what, int extra) {
		if (what == MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN) {
			System.out.println("MEDIA_RECORDER_ERROR_UNKNOWN");
			this.stopSelf(startId);
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		mSurfaceHolder = holder;
		if (!mMediaRecorderRecording) {
			loadSPSAndPPS();
			initializeVideo();
			startVideoRecording();
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		
	}
	
	private void stopVideoRecording() {
		System.out.println("stopVideoRecording");
		if (mMediaRecorderRecording || mMediaRecorder != null) {
			if (t != null)
				startRecording = false;
//				t.interrupt();
			releaseMediaRecorder();
		}
	}

	private void startVideoRecording() {
		(t = new Thread() {

			public void run() {
				
				try {
					if(h264Header == null) {
						Log.e(TAG, "Rlease MediaRecorder and get SPS and PPS");
						Thread.sleep(3000);
						//释放MediaRecorder资源
						releaseMediaRecorder();
						//从已采集的视频数据中获取SPS和PPS
						h264Header = H264Protocol.findSPSAndPPS(fd,videoWidth,videoHeight);
						if(h264Header != null){
							//记录到xml文件里
							String mdatStr = String.format("mdat_%d%d.mdat",videoWidth,videoHeight);
							Editor editor = sharedPreferences.edit();
							editor.putInt(mdatStr, h264Header.getStartMdatIndex());
							editor.commit();
							
							//save ftyp
							FileOutputStream file_out = RecordService.this.openFileOutput(
									String.format("%d%d.ftyp",videoWidth,videoHeight), Context.MODE_PRIVATE);
							file_out.write(h264Header.getFTYP());
							file_out.close();
							
							//save sps
							file_out = RecordService.this.openFileOutput(
									String.format("%d%d.sps",videoWidth,videoHeight), Context.MODE_PRIVATE);
							file_out.write(h264Header.getSPS());
							file_out.close();
							
							//save pps
							file_out = RecordService.this.openFileOutput(
							String.format("%d%d.pps",videoWidth,videoHeight), Context.MODE_PRIVATE);
							file_out.write(h264Header.getPPS());
							file_out.close();
						}
//						
						//找到后重新初始化MediaRecorder
						initializeVideo();
					}	
				} catch (Exception e) {
					return;
				}
				
				startRecording = true;
				processData();
				DataInputStream dis = null;
				try {
					dis = new DataInputStream(new BufferedInputStream(receiver.getInputStream(),MAXFRAMEBUFFER)); //BufferedInputStream 為了使用mark 與 reset
//					dis.read(buffer, 0, 32); //過濾多餘的ftyp
				} catch (IOException e1) {
					return;
				}
				while (startRecording) {
					try {
						int h264length = dis.readInt();
//						
						if(h264length > 0){
							dis.mark(MAXFRAMEBUFFER);
							//取得mdat index
							int mDatIndex = H264Protocol.findMdatIndex(dis,MAXFRAMEBUFFER);
							dis.reset();
							dis.skip(mDatIndex);
//							byte [] mdat = new byte[4];
//							dis.readFully(mdat);
//							dis.reset();
							dis.skip(4); //Mdat Length
						}
						while (startRecording && h264length > 0) {
							byte [] frameData = H264Protocol.readH264Bytes(dis,MAXFRAMEBUFFER);
							
							if(cacheBufferforStreamServer > 0 || cacheBufferforWriteFile){
								if((frameData[0] & 0x1F) == 5){
									bufferList = new ArrayList<ParcelableByteArray>();
									for(int i=bufferList.size() -1 ;i> 0;i--){
										bufferList.remove(i);
									}
								}
								ParcelableByteArray byteArray = new ParcelableByteArray(frameData);
								bufferList.add(byteArray);
							}
						}
	                    
					} catch (IOException e) {
						break;
					}
				}
			}
		}).start();
	}
	
	private void initializeMenu(){
		SatelliteMenu menu = new SatelliteMenu(this);
		float distance = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 170, getResources().getDisplayMetrics());
		menu.setSatelliteDistance((int) distance);
		menu.setExpandDuration(500);
		menu.setCloseItemsOnClick(true);
		menu.setTotalSpacingDegree(90);
		menu.setMainImage(R.drawable.main);
		List<SatelliteMenuItem> items = new ArrayList<SatelliteMenuItem>();
		items.add(new SatelliteMenuItem(0, R.drawable.ic_1)); //Exit App
		items.add(new SatelliteMenuItem(1, R.drawable.ic_3)); //Show Info
		items.add(new SatelliteMenuItem(2, R.drawable.ic_4)); //Write File
		menu.addItems(items);        
        menu.setOnItemClickedListener(new MenuClickedListener(this));
        menu.setBackgroundColor(Color.TRANSPARENT);
        int wh = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200, getResources().getDisplayMetrics());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
        		wh, wh);
        params.gravity=Gravity.BOTTOM | Gravity.LEFT;
        params.leftMargin = 5;
        params.bottomMargin = 5;
        fLayout = (FrameLayout) view.findViewById(R.id.layout);
        fLayout.addView(menu,params);
	}
	
	private boolean initializeLocalSocket(){
		receiver = new LocalSocket();
		try {
			lss = new LocalServerSocket("VideoCamera");
			receiver.connect(new LocalSocketAddress("VideoCamera"));
			receiver.setReceiveBufferSize(500000);
			receiver.setSendBufferSize(500000);
			sender = lss.accept();
			sender.setReceiveBufferSize(500000);
			sender.setSendBufferSize(500000);
		} catch (IOException e) {
			return false;
		}
		return true;
	}
	
	private void initializeServiceConnection(){
		mServiceConn = new ServiceConnection(){

			@Override
			public void onServiceConnected(ComponentName name, IBinder binder) {
				mStreamServer = ((StreamServer.ServiceBinder)binder).getService();
				mStreamServer.setWidth(videoWidth);
				mStreamServer.setHeight(videoHeight);
				mStreamServer.setRate(videoRate);
				mStreamServer.setB(RecordService.this);
			}

			@Override
			public void onServiceDisconnected(ComponentName name) {
				Log.i(TAG, String.format("Stream Service Disconneciton %s", name.getClassName()));
			}
		};
	}

	private boolean initializeVideo() {
		System.out.println("initializeVideo");
		if (mSurfaceHolder == null)
			return false;
		mMediaRecorderRecording = true;
		if (mMediaRecorder == null)
			mMediaRecorder = new MediaRecorder();
		else
			mMediaRecorder.reset();
		camera  = Camera.open();
		if(this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
			camera.setDisplayOrientation(90);
		else 
			camera.setDisplayOrientation(0);
        camera.unlock();
        mMediaRecorder.setCamera(camera);
		mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
//		mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
		//CamcorderProfile camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
		//mediaRecorder.setProfile(camcorderProfile);//构造CamcorderProfile，使用高质量视频录制
		mMediaRecorder.setVideoFrameRate(videoRate);
		mMediaRecorder.setVideoSize(videoWidth, videoHeight);
		mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
		mMediaRecorder.setMaxDuration(0);
		mMediaRecorder.setMaxFileSize(0);
		if(h264Header == null)
		{
			Log.e(TAG, "==============  SPS  is null!!!!!!!!!!");
			try {
				File file = new File(fd);
				if (file.exists())
					file.delete();
			} catch (Exception ex) {
				Log.v("System.out", ex.toString());
			}
			mMediaRecorder.setOutputFile(fd);
		}
		else
		{
			Log.e(TAG,"=============== SPS have value!!!!!!!");
			mMediaRecorder.setOutputFile(sender.getFileDescriptor());
		}
		try {
			mMediaRecorder.setOnInfoListener(this);
			mMediaRecorder.setOnErrorListener(this);
			mMediaRecorder.prepare();
			mMediaRecorder.start();
		} catch (IOException exception) {
			releaseMediaRecorder();
			stopSelf(startId);
			return false;
		}
		return true;
	}
	
	private void releaseCamera() throws IOException{
		if(camera == null){
			return;
		}
		mParameters = camera.getParameters();
		if(mParameters.getFlashMode() == Camera.Parameters.FLASH_MODE_TORCH){
			mParameters.setFlashMode(Parameters.FLASH_MODE_OFF);
			camera.setParameters(mParameters);
		}
		
		camera.reconnect();
		camera.stopPreview();
		camera.release();
		camera = null;
	}

	private void releaseMediaRecorder() {
		System.out.println("Releasing media recorder.");
		if (mMediaRecorder != null) {
			if (mMediaRecorderRecording) {
				try {
					System.out.println("Releasing media recorder2");
					mMediaRecorder.setOnErrorListener(null);
					mMediaRecorder.setOnInfoListener(null);
					mMediaRecorder.stop();
					releaseCamera();
				} catch (RuntimeException e) {
					System.out.println("stop fail: " + e.getMessage());
				} catch (IOException e) {
					System.out.println("stop fail: " + e.getMessage());
				}
				mMediaRecorderRecording = false;
			}
			mMediaRecorder.reset();
			mMediaRecorder.release();
			mMediaRecorder = null;
		}
	}
	
	public byte[] getSPS(){
		return h264Header.getSPS();
	}
	
	public byte[] getPPS(){
		return h264Header.getPPS();
	}
	
	// 得到序列参数集SPS和图像参数集PPS,如果已经存储在本地
	private void loadSPSAndPPS() {
		
		int mdatIndex = sharedPreferences.getInt(
				String.format("mdat_%d%d.mdat", videoWidth, videoHeight), -1);

		if (mdatIndex != -1) {
			h264Header = new H264Header();
			h264Header.setStartMdatIndex(mdatIndex);
			byte[] temp = new byte[100];
			try {
				
				FileInputStream file_in = openFileInput(String.format("%d%d.ftyp", videoWidth, videoHeight));

				int index = 0;
				int read = 0;
				while (true) {
					read = file_in.read(temp, index, 10);
					if (read == -1)
						break;
					else
						index += read;
				}
				Log.e(TAG, "=====get ftyp length:" + index);
				h264Header.setFTYP(temp,index);

				file_in.close();
				
				
				file_in = openFileInput(String.format("%d%d.sps", videoWidth, videoHeight));

				index = 0;
				while (true) {
					read = file_in.read(temp, index, 10);
					if (read == -1)
						break;
					else
						index += read;
				}
				Log.e(TAG, "=====get sps length:" + index);
				h264Header.setSPS(temp, index);

				file_in.close();

				index = 0;
				// read PPS
				file_in = openFileInput(String.format("%d%d.pps", videoWidth, videoHeight));
				while (true) {
					read = file_in.read(temp, index, 10);
					if (read == -1)
						break;
					else
						index += read;
				}
				Log.e(TAG, "==========get pps length:" + index);
				h264Header.setPPS(temp, index);
			} catch (FileNotFoundException e) {
				Log.e(TAG, e.toString());
			} catch (IOException e) {
				Log.e(TAG, e.toString());
			}
		} else {
			Log.e(TAG, "==============StartMdatPlace = -1");
			h264Header = null;
		}
	}
	
	public void startCacheBuf_StreamServer(int i){
		cacheBufferforStreamServer += i;
		
		if(cacheBufferforStreamServer < 0){
			return;
		}
		
		Intent it = new Intent(IntentType.NewClient);
		it.putExtra("clients", cacheBufferforStreamServer);
		sendBroadcast(it);
		
		if(cacheBufferforStreamServer < 1){
			return;
		}
		
		byte[] dd = ArrayUtils.addAll(SOI_MARKER, null);
		dd = ArrayUtils.addAll(dd,h264Header.getHead());
		dd = ArrayUtils.addAll(dd,h264Header.getSPS());
		dd = ArrayUtils.addAll(dd,EOF_MARKER);
		mStreamServer.setBuffer(dd);
		
		dd = ArrayUtils.addAll(SOI_MARKER, null);
		dd = ArrayUtils.addAll(dd,h264Header.getHead());
		dd = ArrayUtils.addAll(dd,h264Header.getPPS());
		dd = ArrayUtils.addAll(dd,EOF_MARKER);
		mStreamServer.setBuffer(dd);
	}
	
	public void startCacheBuf_WriteFile(boolean b){
		cacheBufferforWriteFile = b;
		createFile = b;
	}
	
	public void processData(){
		new Thread(){
			@Override
			public void run() {
				super.run();
				try {
					FileOutputStream fos = null;
					while(startRecording){
						if(bufferList.size() > 0){
							if(cacheBufferforWriteFile){
								if(createFile){
									String name = Environment.getExternalStorageDirectory() + "/my.h264";//TODO changefile
									fos = new FileOutputStream(name);
//									fos.write(h264Header.getFTYP());
									fos.write(h264Header.getHead());
									fos.write(h264Header.getSPS());
									fos.write(h264Header.getHead());
									fos.write(h264Header.getPPS());
									createFile = false;
								}
								
								fos.write(h264Header.getHead());
								fos.write(bufferList.get(0).get_byte());
							}
							if(cacheBufferforStreamServer > 0 && mStreamServer != null){
								byte[] dd = ArrayUtils.addAll(SOI_MARKER, null);
								dd = ArrayUtils.addAll(dd,h264Header.getHead());
								dd = ArrayUtils.addAll(dd,copyLastBufferData());
								dd = ArrayUtils.addAll(dd,EOF_MARKER);
								mStreamServer.setBuffer(dd);
							}
							bufferList.remove(0);
//							bufferList.clear();
						}
					}
					
					if(fos != null){
						fos.close();
					}
					
			        
				} catch (FileNotFoundException e) {
				} catch (IOException e) {
				} finally{
					bufferList.clear();
				}
			}
		}.start();
	}
	
	private byte[] copyLastBufferData(){
//		return ArrayUtils.addAll(bufferList.get(0).get_byte(), null);
		return ArrayUtils.addAll(bufferList.get(0).get_byte(), null);
//			return ArrayUtils.addAll(bufferList.get(bufferList.size()-1).get_byte(), null);
	}
	
	public void flashLight() {
		if (camera == null) {
			return;
		}
		mParameters = camera.getParameters();
		if (isLighOn) {
			Log.i("info", "torch is turn off!");
			mParameters.setFlashMode(Parameters.FLASH_MODE_OFF);
			camera.setParameters(mParameters);
			isLighOn = false;
		} else {
			Log.i("info", "torch is turn on1");
			mParameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
			Log.i("info", "torch is turn on2");
			camera.setParameters(mParameters);
			Log.i("info", "torch is turn on3");
			isLighOn = true;
		}
	}
}