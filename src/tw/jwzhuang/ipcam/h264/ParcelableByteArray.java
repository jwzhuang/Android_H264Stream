package tw.jwzhuang.ipcam.h264;

import android.os.Parcel;
import android.os.Parcelable;

public class ParcelableByteArray implements Parcelable {
    private byte[] _byte;

    public ParcelableByteArray(Parcel in) {
        readFromParcel(in);
    }


    public ParcelableByteArray(byte[] b) {
    	set_byte(b);
	}


	public byte[] get_byte() {
        return _byte;
    }

    public void set_byte(byte[] _byte) {
        this._byte = _byte;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
    	dest.writeInt(_byte.length); 
    	dest.writeByteArray(_byte); 
    }

    public void readFromParcel(Parcel in) {
        _byte = new byte[in.readInt()]; 
        in.readByteArray(_byte);
    }

    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() {
        public ParcelableByteArray createFromParcel(Parcel in) {
            return new ParcelableByteArray(in);
        }
        
        public ParcelableByteArray createFromParcel(byte[] b) {
            return new ParcelableByteArray(b);
        }

        public ParcelableByteArray[] newArray(int size) {
            return new ParcelableByteArray[size];
        }
    };

}