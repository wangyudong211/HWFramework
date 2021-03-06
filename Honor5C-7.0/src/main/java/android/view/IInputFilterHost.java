package android.view;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface IInputFilterHost extends IInterface {

    public static abstract class Stub extends Binder implements IInputFilterHost {
        private static final String DESCRIPTOR = "android.view.IInputFilterHost";
        static final int TRANSACTION_sendInputEvent = 1;

        private static class Proxy implements IInputFilterHost {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }

            public void sendInputEvent(InputEvent event, int policyFlags) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (event != null) {
                        _data.writeInt(Stub.TRANSACTION_sendInputEvent);
                        event.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    _data.writeInt(policyFlags);
                    this.mRemote.transact(Stub.TRANSACTION_sendInputEvent, _data, null, Stub.TRANSACTION_sendInputEvent);
                } finally {
                    _data.recycle();
                }
            }
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IInputFilterHost asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin == null || !(iin instanceof IInputFilterHost)) {
                return new Proxy(obj);
            }
            return (IInputFilterHost) iin;
        }

        public IBinder asBinder() {
            return this;
        }

        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case TRANSACTION_sendInputEvent /*1*/:
                    InputEvent inputEvent;
                    data.enforceInterface(DESCRIPTOR);
                    if (data.readInt() != 0) {
                        inputEvent = (InputEvent) InputEvent.CREATOR.createFromParcel(data);
                    } else {
                        inputEvent = null;
                    }
                    sendInputEvent(inputEvent, data.readInt());
                    return true;
                case 1598968902:
                    reply.writeString(DESCRIPTOR);
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }
    }

    void sendInputEvent(InputEvent inputEvent, int i) throws RemoteException;
}
