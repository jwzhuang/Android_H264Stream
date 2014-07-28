package tw.jwzhuang.ipcam;

import android.content.Context;
import android.content.Intent;
import android.view.ext.SatelliteMenu.SateliteClickedListener;

public class MenuClickedListener implements SateliteClickedListener {
	private Context context = null;
	private boolean showedInfo = false;
	private boolean write_file = false;

	public MenuClickedListener(Context c) {
		context = c;
	}

	@Override
	public void eventOccured(int id) {
		switch(id){
		case 0:
			context.sendBroadcast(new Intent(IntentType.ExitApp));
			break;
		case 1:
			if(showedInfo){
				showedInfo = false;
				context.sendBroadcast(new Intent(IntentType.ExitClientInfo));
				break;
			}
			showedInfo = true;
			context.startService(new Intent(context, InfoService.class));
			break;
		case 2:
			if(write_file){
				write_file = false;
			}else{
				write_file = true;
			}
			((RecordService)context).startCacheBuf_WriteFile(write_file);
			break;
		}
	}

}
