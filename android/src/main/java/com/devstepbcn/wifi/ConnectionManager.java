package com.devstepbcn.wifi;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;

/**
 * ConnectionManager
 */

public class ConnectionManager {
    private Context context;
    private Activity activity;
    private static final String WPA = "WPA";
    private static final String WEP = "WEP";
    private static final String OPEN = "Open";
    private final static String TAG = "WiFiConnector";


    public ConnectionManager(Context context) {
        this.context = context;
        
    }

    public void enableWifi() {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
            Log.v("ReactNative", "ConnectionManager: " + "Wifi Turned On");
        }
    }

    public int requestWIFIConnection(String networkSSID, String networkPass) {
        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            //Check ssid exists
            if (scanWifi(wifiManager, networkSSID)) {
                if (getCurrentSSID(wifiManager) != null && getCurrentSSID(wifiManager).equals("\"" + networkSSID + "\"")) {
                    Log.v("ReactNative", "ConnectionManager: " + "Already Connected With " + networkSSID);
                    return 0;
                }
                //Security type detection
                String SECURE_TYPE = checkSecurity(wifiManager, networkSSID);
                if (SECURE_TYPE == null) {
                    Log.v("ReactNative", "ConnectionManager: " + "Unable to find Security type for " + networkSSID);
                    return 1;
                }
                if (SECURE_TYPE.equals(WPA)) {
                    boolean r = WPA(networkSSID, networkPass, wifiManager);
                    if(r){
                        return 9;
                    } else { 
                        return 10;
                    }
                    //return 7;
                } else if (SECURE_TYPE.equals(WEP)) {
                    WEP(networkSSID, networkPass);
                    return 8;
                } else {
                    OPEN(wifiManager, networkSSID);
                    return 9;
                }
                

            }
            /*connectME();*/
        } catch (Exception e) {
            Log.v("ReactNative", "ConnectionManager: " + "Error Connecting WIFI " + e);
            return 5;
        }
        return 3;
    }

    private boolean WPA(String networkSSID, String networkPass, WifiManager wifiManager) {
        WifiConfiguration wc = new WifiConfiguration();
        wc.SSID = "\"" + networkSSID + "\"";
        wc.preSharedKey = "\"" + networkPass + "\"";
        wc.status = WifiConfiguration.Status.ENABLED;
        wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        wc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        int id = wifiManager.addNetwork(wc);
        wifiManager.disconnect();
        wifiManager.enableNetwork(id, true);
        boolean res = wifiManager.reconnect();
        return res;
    }

    private void WEP(String networkSSID, String networkPass) {
    }

    private void OPEN(WifiManager wifiManager, String networkSSID) {
        WifiConfiguration wc = new WifiConfiguration();
        wc.SSID = "\"" + networkSSID + "\"";
        wc.hiddenSSID = true;
        wc.priority = 0xBADBAD;
        wc.status = WifiConfiguration.Status.ENABLED;
        wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        int id = wifiManager.addNetwork(wc);
        wifiManager.disconnect();
        wifiManager.enableNetwork(id, true);
        wifiManager.reconnect();
    }

    boolean scanWifi(WifiManager wifiManager, String networkSSID) {
        Log.e(TAG, "scanWifi starts");
        List<ScanResult> scanList = wifiManager.getScanResults();
        for (ScanResult i : scanList) {
            if (i.SSID != null) {
                Log.e(TAG, "SSID: " + i.SSID);
            }

            if (i.SSID != null && i.SSID.equals(networkSSID)) {
                Log.e(TAG, "Found SSID: " + i.SSID);
                return true;
            }
        }
        Log.v("ReactNative", "ConnectionManager: " + "SSID " + networkSSID + " Not Found");
        return false;
    }

    public String getCurrentSSID(WifiManager wifiManager) {
        String ssid = null;
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (networkInfo.isConnected()) {
            final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
            if (connectionInfo != null && !TextUtils.isEmpty(connectionInfo.getSSID())) {
                ssid = connectionInfo.getSSID();
            }
        }
        return ssid;
    }

    private String checkSecurity(WifiManager wifiManager, String ssid) {
        List<ScanResult> networkList = wifiManager.getScanResults();
        for (ScanResult network : networkList) {
            if (network.SSID.equals(ssid)) {
                String Capabilities = network.capabilities;
                if (Capabilities.contains("WPA")) {
                    return WPA;
                } else if (Capabilities.contains("WEP")) {
                    return WEP;
                } else {
                    return OPEN;
                }

            }
        }
        return null;
    }




}