package tw.jwzhuang.ipcam;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.Application;
import android.view.WindowManager;

public class MyApplication extends Application {
	
	private WindowManager.LayoutParams wmParams=new WindowManager.LayoutParams();
	private static MyApplication instance;
    //取得Application实例
    public static MyApplication getInstance() {
        return instance;
    }
    //存储打开的Activity
    private List<Activity> mActivities = new ArrayList<Activity>();
//    private List<Service> mService = new ArrayList<Service>();
    //增加Activity
    public void addActivity(Activity activity)
    {
        mActivities.add(activity);
    }
    
//    public void addService(Service service)
//    {
//    	mService.add(service);
//    }
   
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;    //这句很重要。不初始化就没法用到Application了。
    }
   
    @Override
    public void onTerminate()
    {
        super.onTerminate(); //首先要调用这个
        
//        for(Service service : mService) //遍历Activity，一个个finish
//        	service.stopSelf();
        
        for(Activity activity : mActivities) //遍历Activity，一个个finish
            activity.finish();
        System.exit(0); //退出程序
    }
    
    
	public WindowManager.LayoutParams getMywmParams(){
		return wmParams;
	}
}
