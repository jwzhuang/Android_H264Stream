package tw.jwzhuang.ipcam;

import org.json.JSONException;

import tw.jwzhuang.ipcam.qrcode.Match;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.google.zxing.WriterException;

public class IPCam extends Activity {

	private static long back_pressed;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity);
	}

	public void click_rec(View view) {
		startService(new Intent(this, RecordService.class));
	}
	
	public void click_qrcode(View view) throws WriterException, JSONException {
		startActivity(new Intent(this,Match.class));
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	public void onBackPressed() {
		
		if(!isMyServiceRunning(RecordService.class)){
			this.sendBroadcast(new Intent(IntentType.ExitApp));
			super.onBackPressed();
			return;
		}

		if (back_pressed + 2000 > System.currentTimeMillis()){
			this.sendBroadcast(new Intent(IntentType.ExitApp));
			super.onBackPressed();
		}else{
			Toast.makeText(getBaseContext(), R.string.pressagain,
					Toast.LENGTH_SHORT).show();
		}
		back_pressed = System.currentTimeMillis();
		
	}

	private boolean isMyServiceRunning(Class<?> serviceClass) {
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager
				.getRunningServices(Integer.MAX_VALUE)) {
			if (serviceClass.getName().equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}
}
