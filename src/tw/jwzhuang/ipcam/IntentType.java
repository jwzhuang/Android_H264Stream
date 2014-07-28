package tw.jwzhuang.ipcam;


enum CMD {
    EXITAPP, NEWCLIENT, CLIENTINFO, EXITCLIENTINFO 
}

public class IntentType {
	public final static String ExitApp = IntentType.class.getName() + ".ExitApp";
	public final static String ClientInfo = IntentType.class.getName() + ".ClientInfo";
	public final static String NewClient = IntentType.class.getName() + ".NewClient";
	public final static String ExitClientInfo = IntentType.class.getName() + ".ExitClientInfo";
	public final static String Hide = IntentType.class.getName() + ".Hide";
}
