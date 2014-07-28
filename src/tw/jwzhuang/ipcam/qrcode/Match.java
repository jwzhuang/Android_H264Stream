package tw.jwzhuang.ipcam.qrcode;

import org.json.JSONException;
import org.json.JSONObject;

import tw.jwzhuang.ipcam.R;
import tw.jwzhuang.ipcam.Utils;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;

public class Match extends Activity {
	
	private SharedPreferences mSharedPreferences = null;
	private String randomcode;
	private String localIP;
	private ImageView qrcodeImg = null;
	private Bitmap bmp = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.match);
		qrcodeImg = (ImageView) findViewById(R.id.img_qrcode);
		mSharedPreferences = getSharedPreferences("code",  MODE_PRIVATE);
		initParmas();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		try {
			generateQRCode();
		} catch (JSONException | WriterException e) {
			Log.e(Match.class.getSimpleName(), e.getMessage());
		}
	}

	@Override
	protected void onPause() {
		qrcodeImg.setImageBitmap(null);
		if(bmp != null){
			bmp.recycle();
		}
		super.onPause();
	}
	
	private void initParmas(){
		randomcode = mSharedPreferences.getString("randomcode","");
		if(randomcode.isEmpty()){
			randomcode = Utils.pwdGenerator(4, 4);
			mSharedPreferences.edit().putString("randomcode", randomcode).commit();
		}
		
		localIP = Utils.getLocalIpAddress();
	}
	
	private void generateQRCode() throws JSONException, WriterException{
		JSONObject jobj = new JSONObject();
		jobj.put("ip", localIP);
		jobj.put("pwd",randomcode);
		
		Point size = Utils.getScreenSize(getWindowManager());
		int dimension = size.x < size.y ? size.x : size.y;
		dimension = (int) (dimension * 0.75);
		
		QRCodeEncoder qrEncoder = new QRCodeEncoder(jobj.toString(),BarcodeFormat.QR_CODE,dimension);
		bmp = qrEncoder.encodeAsBitmap();
		qrcodeImg.setImageBitmap(bmp);
	}
}
