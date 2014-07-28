package tw.jwzhuang.ipcam.h264;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import android.util.Log;

public class H264Protocol extends H264Header {
	
	public static int findMdatIndex(DataInputStream in, int MAXFRAMEBUFFER)
			throws IOException {
		int seqIndex = 0;
		byte c;
		for (int i = 0; i < MAXFRAMEBUFFER; i++) {
			c = (byte) in.readUnsignedByte();
			if (c == mMdat[seqIndex]) {
				seqIndex++;
				if (seqIndex == mMdat.length)
					return (i + 1) - mMdat.length;
			} else
				seqIndex = 0;
		}
		return -1;
	}
	
	public static byte[] readH264Bytes(DataInputStream in, int MAXFRAMEBUFFER) throws IOException{
		//重新定位在frame 長度 00 00 xx xx 
		in.mark(MAXFRAMEBUFFER); 
		in.reset();
		//取得frame length
		byte [] len = new byte[4];
		in.readFully(len);
		in.reset();
		in.skip(len.length);
		in.mark(MAXFRAMEBUFFER);
		int DataLength = ((len[2] & 0xFF) << 8) | (len[3] & 0xFF);
		
		//取得frame Data
		byte [] content = new byte[DataLength];
		in.readFully(content);
		in.reset();
		in.skip(content.length);
		return content;
	}

	public static H264Header findSPSAndPPS(String samplefile, int videoWidth, int videoHeight) throws Exception{
		H264Header h264Header = null;
		final String TAG = "H264Protocol";
		int startMdatIndex = 0;
		byte[] SPS;
	 	byte[] PPS;
	 	byte[] ftyp = new byte[28];
		
		File file = new File(samplefile);
		FileInputStream fileInput = new FileInputStream(file);
		
		int length = (int)file.length();
		byte[] data = new byte[length];
		
		fileInput.read(data);
		fileInput.close();
		final byte[] mdat = new byte[]{0x6D,0x64,0x61,0x74};
		final byte[] avcc = new byte[]{0x61,0x76,0x63,0x43};
		
		for(int i=0 ; i<length; i++){
			if(data[i] == mdat[0] && data[i+1] == mdat[1] && data[i+2] == mdat[2] && data[i+3] == mdat[3]){
				startMdatIndex = i+4;//find mdat
				break;
			}
		}
		
		
		for(int i=0 ; i<length; i++){
			if(data[i] == avcc[0] && data[i+1] == avcc[1] && data[i+2] == avcc[2] && data[i+3] == avcc[3]){
				h264Header = new H264Header();
				System.arraycopy(data, 0, ftyp, 0, 28);
				h264Header.setFTYP(ftyp);
				h264Header.setStartMdatIndex(startMdatIndex);
				Log.e(TAG, "StartMdatPlace:" + startMdatIndex);
				int sps_start = i+3+7;//其中i+3指到avcc的c，再加7跳过6位AVCDecoderConfigurationRecord参数
				
				//sps length and sps data
				byte[] sps_3gp = new byte[2];//sps length
				sps_3gp[1] = data[sps_start];
				sps_3gp[0] = data[sps_start + 1];
				int sps_length = bytes2short(sps_3gp);
				Log.e(TAG, "sps_length :" + sps_length);
				
				sps_start += 2;//skip length
				SPS = new byte[sps_length];
				System.arraycopy(data, sps_start, SPS, 0, sps_length);
				for(int si=0;si<sps_length;si++)
				Log.e(TAG, "==========SPS :" + si + SPS[si]);
				h264Header.setSPS(SPS);
				//pps length and pps data
				int pps_start = sps_start + sps_length + 1;
				byte[] pps_3gp =new byte[2];
				pps_3gp[1] = data[pps_start];
				pps_3gp[0] =data[pps_start+1];
				int pps_length = bytes2short(pps_3gp);
				Log.e(TAG, "PPS LENGTH:"+pps_length);
				
				pps_start+=2;
				
				PPS = new byte[pps_length];
				System.arraycopy(data, pps_start, PPS,0,pps_length);
				for (int pi =0;pi<pps_length;pi++)
				   Log.e(TAG, "==========PPS :" +pi + PPS[pi]);
				
				//Save PPS
				h264Header.setPPS(PPS);
//				
				Log.e(TAG, "==========SPS :" + SPS+ ",  PPS :" +PPS);
				return h264Header;
			}
		}
		return h264Header;
	}
	
	private static short bytes2short(byte[] b) {
		short mask = 0xff;
		short temp = 0;
		short res = 0;
		for (int i = 0; i < 2; i++) {
			res <<= 8;
			temp = (short) (b[1 - i] & mask);
			res |= temp;
		}
		return res;
	}
}
