package com.android.commands.monkey;

import android.app.IActivityManager;
import android.util.Log;
import android.view.IWindowManager;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MonkeyGetFrameRateEvent extends MonkeyEvent {
    private static final String LOG_FILE = "/sdcard/avgFrameRateOut.txt";
    private static final Pattern NO_OF_FRAMES_PATTERN = Pattern.compile(".*\\(([a-f[A-F][0-9]].*?)\\s.*\\)");
    private static final String TAG = "MonkeyGetFrameRateEvent";
    private static float mDuration;
    private static int mEndFrameNo;
    private static long mEndTime;
    private static int mStartFrameNo;
    private static long mStartTime;
    private static String mTestCaseName = null;
    private String GET_FRAMERATE_CMD = "service call SurfaceFlinger 1013";
    private String mStatus;

    public MonkeyGetFrameRateEvent(String status, String testCaseName) {
        super(4);
        this.mStatus = status;
        mTestCaseName = testCaseName;
    }

    public MonkeyGetFrameRateEvent(String status) {
        super(4);
        this.mStatus = status;
    }

    private float getAverageFrameRate(int totalNumberOfFrame, float duration) {
        if (duration > 0.0f) {
            return ((float) totalNumberOfFrame) / duration;
        }
        return 0.0f;
    }

    private void writeAverageFrameRate() {
        FileWriter writer = null;
        try {
            writer = new FileWriter(LOG_FILE, true);
            writer.write(String.format("%s:%.2f\n", new Object[]{mTestCaseName, Float.valueOf(getAverageFrameRate(mEndFrameNo - mStartFrameNo, mDuration))}));
            writer.close();
            try {
                writer.close();
            } catch (IOException e) {
                Log.e(TAG, "IOException " + e.toString());
            }
        } catch (IOException e2) {
            Log.w(TAG, "Can't write sdcard log file", e2);
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e3) {
                    Log.e(TAG, "IOException " + e3.toString());
                }
            }
        } catch (Throwable th) {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e4) {
                    Log.e(TAG, "IOException " + e4.toString());
                }
            }
            throw th;
        }
    }

    private String getNumberOfFrames(String input) {
        Matcher m = NO_OF_FRAMES_PATTERN.matcher(input);
        if (m.matches()) {
            return m.group(1);
        }
        return null;
    }

    public int injectEvent(IWindowManager iwm, IActivityManager iam, int verbose) {
        Process p = null;
        BufferedReader result = null;
        try {
            Process p2 = Runtime.getRuntime().exec(this.GET_FRAMERATE_CMD);
            int status = p2.waitFor();
            if (status != 0) {
                Logger.err.println(String.format("// Shell command %s status was %s", new Object[]{this.GET_FRAMERATE_CMD, Integer.valueOf(status)}));
            }
            BufferedReader result2 = new BufferedReader(new InputStreamReader(p2.getInputStream()));
            String output = result2.readLine();
            if (output != null) {
                if (this.mStatus == "start") {
                    mStartFrameNo = Integer.parseInt(getNumberOfFrames(output), 16);
                    mStartTime = System.currentTimeMillis();
                } else if (this.mStatus == "end") {
                    mEndFrameNo = Integer.parseInt(getNumberOfFrames(output), 16);
                    mEndTime = System.currentTimeMillis();
                    mDuration = (float) (((double) (mEndTime - mStartTime)) / 1000.0d);
                    writeAverageFrameRate();
                }
            }
            try {
                result2.close();
                if (p2 != null) {
                    p2.destroy();
                }
            } catch (IOException e) {
                Logger.err.println(e.toString());
            }
        } catch (Exception e2) {
            Logger logger = Logger.err;
            logger.println("// Exception from " + this.GET_FRAMERATE_CMD + ":");
            Logger.err.println(e2.toString());
            if (result != null) {
                result.close();
            }
            if (p != null) {
                p.destroy();
            }
        } catch (Throwable th) {
            if (result != null) {
                try {
                    result.close();
                } catch (IOException e3) {
                    Logger.err.println(e3.toString());
                    throw th;
                }
            }
            if (p != null) {
                p.destroy();
            }
            throw th;
        }
        return 1;
    }
}
