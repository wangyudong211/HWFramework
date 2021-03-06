package com.android.internal.net;

import android.os.Parcel;
import android.os.Parcelable;

public class VpnInfo implements Parcelable {
    public static final Parcelable.Creator<VpnInfo> CREATOR = new Parcelable.Creator<VpnInfo>() {
        public VpnInfo createFromParcel(Parcel source) {
            VpnInfo info = new VpnInfo();
            info.ownerUid = source.readInt();
            info.vpnIface = source.readString();
            info.primaryUnderlyingIface = source.readString();
            return info;
        }

        public VpnInfo[] newArray(int size) {
            return new VpnInfo[size];
        }
    };
    public int ownerUid;
    public String primaryUnderlyingIface;
    public String vpnIface;

    public String toString() {
        return "VpnInfo{ownerUid=" + this.ownerUid + ", vpnIface='" + this.vpnIface + '\'' + ", primaryUnderlyingIface='" + this.primaryUnderlyingIface + '\'' + '}';
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.ownerUid);
        dest.writeString(this.vpnIface);
        dest.writeString(this.primaryUnderlyingIface);
    }
}
