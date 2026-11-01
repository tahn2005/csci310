package com.team23.trojanrideshare.data.firestore;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/**
 * Repositories refuse writes while the device is offline so that nothing is queued or
 * half-saved (NFR-3, FR-2.E2, FR-3.E9, FR-4.E4).
 */
public class NetworkMonitor {

    private final ConnectivityManager cm;

    public NetworkMonitor(Context context) {
        cm = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public boolean isOnline() {
        if (cm == null) return true;
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
