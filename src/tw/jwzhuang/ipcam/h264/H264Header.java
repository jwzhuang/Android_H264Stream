package tw.jwzhuang.ipcam.h264;

public class H264Header {
	protected int startMdatIndex = 0;
	protected byte[] SPS;
	protected byte[] PPS;
	protected byte[] FTYP;

	protected final byte[] mHead = new byte[]{0x00,0x00,0x00,0x01};
	protected final static byte [] mMdat = new byte[] { (byte) 0x6D, (byte) 0x64, (byte) 0x61, (byte) 0x74 };
	
	public byte[] getHead() {
		return mHead;
	}
	
	public byte[] getFTYP() {
		return FTYP;
	}
	
	public void setFTYP(byte[] fTYP) {
		FTYP = fTYP;
	}
	
	public void setFTYP(byte[] temp, int index) {
		FTYP = new byte[index];
		System.arraycopy(temp, 0, FTYP, 0, index);
	}

	public int getStartMdatIndex() {
		return startMdatIndex;
	}
	
	public void setStartMdatIndex(int startMdatIndex) {
		this.startMdatIndex = startMdatIndex;
	}
	
	public byte[] getSPS() {
		return SPS;
	}
	
	public void setSPS(byte[] sPS) {
		SPS = sPS;
	}
	
	public void setSPS(byte[] temp, int index) {
		SPS = new byte[index];
		System.arraycopy(temp, 0, SPS, 0, index);
	}
	
	public byte[] getPPS() {
		return PPS;
	}
	
	public void setPPS(byte[] pPS) {
		PPS = pPS;
	}

	public void setPPS(byte[] temp, int index) {
		PPS = new byte[index];
		System.arraycopy(temp, 0, PPS, 0, index);
	}
}
