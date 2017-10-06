package com.opentechlancer.appusagemetrics.Constant;

import android.content.Context;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * Created by pr0 on 10/5/17.
 */

public class ConnectionUtils {

    public static int getPort(Context context) {
        int localPort = SharedPreferencesDB.getInstance(context).getPreferenceIntValue("port", -1);
        if (localPort < 0) {
            localPort = getNextFreePort();
            SharedPreferencesDB.getInstance(context).setPreferenceIntValue("port", localPort);
        }
        return localPort;
    }

    public static int getNextFreePort() {
        int localPort = -1;
        try {
            ServerSocket s = new ServerSocket(0);
            localPort = s.getLocalPort();

            //closing the port
            if (s != null && !s.isClosed()) {
                s.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return localPort;
    }
}
