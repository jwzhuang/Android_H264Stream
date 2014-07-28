package tw.jwzhuang.ipcam.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

import android.util.Log;

public class SocketServer implements Runnable {

	private final String TAG = "H264 Stream Server";
	private ServerSocket serverSocket = null;
	private Boolean readStream = true;
	private final int ServerPort = 47226;
	private StreamServer streamServer = null;
	private List<WorkerRunnable> workers = null;

	public SocketServer(StreamServer streamServer) {
		this.streamServer = streamServer;
		workers = new ArrayList<WorkerRunnable>();
	}

	@Override
	public void run() {
		Log.d(TAG, String.format("%s Run %d", this.getClass().getSimpleName(),
				ServerPort));
		try {
			// 建立serverSocket
			serverSocket = new ServerSocket(ServerPort);
			// boolean verify = false;
			// 等待連線
			while (readStream) {
				Socket client = serverSocket.accept();
				WorkerRunnable worker = new WorkerRunnable(client);
				workers.add(worker);
				new Thread(worker).start();
			}
		} catch (Exception e) {
		}
	}

	public void closeServer() throws IOException {
		readStream = false;
		if (serverSocket != null) {
			serverSocket.close();
			serverSocket = null;
			Log.d(TAG, "null connect");
		}
		
		for(int i = 0;i < workers.size();i++){
			WorkerRunnable worker = workers.get(0);
			if(worker != null){
				worker.closeAll();
			}
			workers.clear();
		}
	}

	public class WorkerRunnable implements Runnable {

		private Socket client = null;
		private boolean isConnected = false;
		private int aliveMsg = 3;
		private BufferedInputStream in = null;
		private BufferedOutputStream out = null;

		public WorkerRunnable(Socket clientSocket) {
			this.client = clientSocket;
			this.isConnected = true;
//			checkAlive();
		}

		public void sentMsg(String str) {
			sentMsg(str.getBytes());
		}

		public void sentMsg(final byte[] data) {
			if (null != out) {
				new Thread() {
					public void run() {
						try {
							// 送出字串
							out.write(data);
							out.flush();
							// Log.d(VideoDBSet.ServiceTAG, new String(m));
	
						} catch (Exception e) {
							try {
								closeAll();
							} catch (Exception e1) {
								e1.printStackTrace();
							}
						}
					}
				}.start();
			}
		}
		
		public void closeAll() throws IOException{
			
			streamServer.removeWorker(this);
			
			if (in != null) {
				in.close();
				in = null;
				Log.d(TAG, "null in");
			}

			if (out != null) {
				out.close();
				out = null;
				Log.d(TAG, "null out");
			}
			
			if(client != null){
				client.close();
			}
		}
		
		private String getSocketStr(String str) throws UnsupportedEncodingException {
			String groupStr = "";
			// String patternStr = "([\\S]+)";

			String patternStr = String.format("([^%s]+)", new String(new byte[1]));
			Pattern pattern = Pattern.compile(patternStr);
			Matcher matcher = pattern.matcher(str);
			while (matcher.find()) {
				for (int i = 0; i <= matcher.groupCount() - 1; i++) {
					if (groupStr.length() < matcher.group(i).length()) {
						groupStr = matcher.group(i);
					}
				}
			}
			return groupStr;
		}
		
		@Override
		public void run() {
			try {
				out = new BufferedOutputStream(client.getOutputStream());
				in = new BufferedInputStream(client.getInputStream());
				boolean verify = false;
				while (isConnected && readStream) {// 接收連線
					byte[] content = new byte[1024];
					in.read(content);
					String input = getSocketStr(new String(content, "UTF8"));
					Log.d("ddd", input);
					JSONObject jobj = new JSONObject(input);
					if (jobj.optString("cmd") != null) {
						if (jobj.getString("cmd").equals("login")) {
							verify = streamServer.verifyUser(
									jobj.getString("pwd"),this);
						} else if (jobj.getString("cmd").equals("alive")) {
							aliveMsg++;
						} else if (jobj.getString("cmd").equals("getparams")
								&& verify) {
							streamServer.sentVideoParams(this);
						} else if (jobj.getString("cmd").equals("getstream")
								&& verify) {
							streamServer.sentVideoStream(true,this);
						}else if (jobj.getString("cmd").equals("flashlight")
								&& verify) {
							streamServer.flashLight();
						}
					}
				}
			} catch (Exception e) {
			}
		}
	}
}
