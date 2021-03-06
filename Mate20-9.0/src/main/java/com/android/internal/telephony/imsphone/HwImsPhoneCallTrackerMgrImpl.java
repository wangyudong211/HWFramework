package com.android.internal.telephony.imsphone;

import android.telephony.Rlog;
import com.android.ims.IHwImsCallEx;
import com.android.ims.ImsCall;
import com.android.ims.ImsException;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.metrics.TelephonyMetrics;

public class HwImsPhoneCallTrackerMgrImpl implements HwImsPhoneCallTrackerMgr {
    private static final boolean DBG = true;
    private static boolean ISDEBUGPHONE = true;
    private static final String LOG_TAG = "HwImsPhoneCallTrackerMgrImpl";
    private static final int ONE_CALL_LEFT_IN_IMS_CONF = 1;
    private static HwImsPhoneCallTrackerMgr mInstance = new HwImsPhoneCallTrackerMgrImpl();
    private TelephonyMetrics mMetrics = TelephonyMetrics.getInstance();

    public static HwImsPhoneCallTrackerMgr getDefault() {
        return mInstance;
    }

    public void hangupHisiImsConnection(ImsPhoneConnection conn, IHwImsPhoneCallTrackerInner tracker) throws CallStateException {
        ImsPhoneCall call = conn.getCall();
        if (call != null && call.getConnections() != null) {
            int size = call.getConnections().size();
            Rlog.i(LOG_TAG, "ImsPhoneCallTracker:hangup:size =" + size);
            if (!conn.isMultiparty() || size != 1) {
                hangupImsConnection(conn, tracker);
            } else if (call == tracker.getImsPhoneCallTracker().mForegroundCall) {
                hangupForegroundResumeBackground(conn.getCall(), tracker);
            } else if (call == tracker.getImsPhoneCallTracker().mBackgroundCall) {
                hangupWaitingOrBackground(conn.getCall(), tracker);
            } else {
                hangupImsConnection(conn, tracker);
            }
        }
    }

    public void hangupHisiImsCall(ImsPhoneCall call, IHwImsPhoneCallTrackerInner tracker) throws CallStateException {
        if (!call.isMultiparty() || call != tracker.getImsPhoneCallTracker().mForegroundCall) {
            hangupImsCall(call, tracker);
        } else {
            hangupForegroundResumeBackground(call, tracker);
        }
    }

    public void hangupConnectionByIndex(ImsPhoneCall call, int index, IHwImsPhoneCallTrackerInner tracker) throws CallStateException {
        int count = call.mConnections.size();
        for (int i = 0; i < count; i++) {
            ImsPhoneConnection cn = (ImsPhoneConnection) call.mConnections.get(i);
            if (cn.getImsIndex() == index) {
                hangupImsConnection(cn, tracker);
                return;
            }
        }
        throw new CallStateException("no gsm index found");
    }

    private void hangupForegroundResumeBackground(ImsPhoneCall call, IHwImsPhoneCallTrackerInner tracker) throws CallStateException {
        log("hangupForegroundResumeBackground");
        ImsCall imsCall = call.getImsCall();
        if (imsCall == null) {
            try {
                log("imsCall is null,faild");
            } catch (ImsException e) {
                throw new CallStateException(e.getMessage());
            }
        } else {
            IHwImsCallEx hwImsCallEx = imsCall.getHwImsCallEx();
            if (hwImsCallEx != null) {
                hwImsCallEx.hangupForegroundResumeBackground(501, imsCall);
            }
            this.mMetrics.writeOnImsCommand(tracker.getImsPhone().getPhoneId(), imsCall.getSession(), 4);
        }
    }

    private void hangupWaitingOrBackground(ImsPhoneCall call, IHwImsPhoneCallTrackerInner tracker) throws CallStateException {
        log("hangupWaitingOrBackground");
        ImsCall imsCall = call.getImsCall();
        if (imsCall == null) {
            try {
                log("imsCall is null,faild");
            } catch (ImsException e) {
                throw new CallStateException(e.getMessage());
            }
        } else {
            IHwImsCallEx hwImsCallEx = imsCall.getHwImsCallEx();
            if (hwImsCallEx != null) {
                hwImsCallEx.hangupWaitingOrBackground(501, imsCall);
            }
            this.mMetrics.writeOnImsCommand(tracker.getImsPhone().getPhoneId(), imsCall.getSession(), 4);
        }
    }

    private void hangupImsCall(ImsPhoneCall call, IHwImsPhoneCallTrackerInner tracker) throws CallStateException {
        log("hangupImsCall ImsPhoneCall");
        int size = call.getConnections().size();
        for (int i = 0; i < size; i++) {
            hangupImsConnection((ImsPhoneConnection) call.getConnections().get(i), tracker);
        }
    }

    private void hangupImsConnection(ImsPhoneConnection conn, IHwImsPhoneCallTrackerInner tracker) throws CallStateException {
        log("hangupImsConnection ImsPhoneConnection");
        ImsPhoneCall call = conn.getCall();
        if (call.getConnections().size() != 0) {
            ImsCall imsCall = conn.getImsCall();
            boolean rejectCall = false;
            if (call == tracker.getImsPhoneCallTracker().mRingingCall) {
                if (ISDEBUGPHONE) {
                    log("(is ringingCall) hangup incoming...");
                }
                rejectCall = true;
            } else if (call == tracker.getImsPhoneCallTracker().mForegroundCall) {
                if (call.isDialingOrAlerting()) {
                    if (ISDEBUGPHONE) {
                        log("(is foregndCall) hangup dialing or alerting...");
                    }
                } else if (ISDEBUGPHONE) {
                    log("(is foregndCall) hangup foreground...");
                }
            } else if (call != tracker.getImsPhoneCallTracker().mBackgroundCall) {
                throw new CallStateException("ImsPhoneCall " + call + "doesn't belong to ImsPhoneCallTracker " + this);
            } else if (ISDEBUGPHONE) {
                log("(is backgndCall) hangup waiting or background...");
            }
            conn.onHangupLocal();
            if (call.getConnections().size() == 1) {
                conn.getCall().setState(Call.State.DISCONNECTING);
            }
            log("hangupImsConnection imsCall  :: " + imsCall);
            try {
                ImsPhoneConnection connection = tracker.getImsPhoneConnection();
                if (imsCall != null) {
                    if (rejectCall) {
                        HwCustImsPhoneCallTracker mCust = tracker.getHwCustImsPhoneCallTracker();
                        if (mCust == null || mCust.getRejectCallCause(call) == -1) {
                            imsCall.reject(504);
                            this.mMetrics.writeOnImsCommand(tracker.getImsPhone().getPhoneId(), imsCall.getSession(), 3);
                        } else {
                            log("rejectCallForCause !!!");
                            mCust.rejectCallForCause(imsCall);
                            this.mMetrics.writeOnImsCommand(tracker.getImsPhone().getPhoneId(), imsCall.getSession(), 3);
                        }
                    } else {
                        imsCall.terminate(501);
                        this.mMetrics.writeOnImsCommand(tracker.getImsPhone().getPhoneId(), imsCall.getSession(), 4);
                    }
                } else if (connection != null && call == tracker.getImsPhoneCallTracker().mForegroundCall) {
                    connection.update(null, Call.State.DISCONNECTED);
                    connection.onDisconnect();
                    tracker.removeConnectionHw(connection);
                    tracker.updatePhoneStateHw();
                    tracker.getImsPhoneCallTracker().removeMessages(tracker.getEventPendingMo());
                }
                tracker.getImsPhone().notifyPreciseCallStateChanged();
            } catch (ImsException e) {
                throw new CallStateException(e.getMessage());
            }
        } else {
            throw new CallStateException("no connections");
        }
    }

    private void log(String msg) {
        Rlog.d(LOG_TAG, msg);
    }
}
