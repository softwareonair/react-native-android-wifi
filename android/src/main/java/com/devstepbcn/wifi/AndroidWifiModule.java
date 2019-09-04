package com.devstepbcn.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.SupplicantState;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.util.Log;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.uimanager.IllegalViewOperationException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;




public class AndroidWifiModule extends ReactContextBaseJavaModule {

	public class ConnectionResult {
		boolean status;
		String result;
		int statusCode;

		public  ConnectionResult(boolean status, String result, int statusCode){
			this.status = status;
			this.result = result;
			this.statusCode = statusCode;
		}

		public boolean getStatus(){
			return status;
		}

		public void setStatus(boolean status){
			this.status = status;
		}

		public int getStatusCode(){
			return statusCode;
		}

		public void setStatusCode(int statusCode){
			this.statusCode = statusCode;
		}

		public String getResult(){
			return result;
		}

		public void setResult(String result){
			this.result = result;
		}

		public String toJSON(){
			JSONObject jsonObject = new JSONObject();
			try{
				jsonObject.put("status", getStatus());
				jsonObject.put("result", getResult());
				jsonObject.put("statusCode", getStatusCode());

				return jsonObject.toString();
			} catch(JSONException e){
				e.printStackTrace();
				return "";
			}
		}


	}

	//WifiManager Instance
	WifiManager wifi;
	ConnectionManager cm;
	ReactApplicationContext reactContext;
	ConnectionResult connection;
	String currentSsid = "NONE";
	WifiInfo info;
	boolean busy = false;
	boolean isConnected = false;
	boolean authPassed = false;

	//Constructor
	public AndroidWifiModule(ReactApplicationContext reactContext) {
		super(reactContext);

		wifi = (WifiManager)reactContext.getSystemService(Context.WIFI_SERVICE);
		cm = new ConnectionManager(reactContext.getBaseContext());
		cm.enableWifi();
		this.reactContext = reactContext;

		WifiInfo winfo = wifi.getConnectionInfo();
		String ssid = winfo.getSSID();
		if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
			ssid = ssid.substring(1, ssid.length() - 1);
		}
		currentSsid = ssid;
	}

	//Name for module register to use:
	@Override
	public String getName() {
		return "AndroidWifiModule";
	}

	//Method to load wifi list into string via Callback. Returns a stringified JSONArray
	@ReactMethod
	public void loadWifiList(Callback successCallback, Callback errorCallback) {
		try {
			List < ScanResult > results = wifi.getScanResults();
			JSONArray wifiArray = new JSONArray();

			for (ScanResult result: results) {
				JSONObject wifiObject = new JSONObject();
				if(!result.SSID.equals("")){
					try {
			            wifiObject.put("SSID", result.SSID);
			            wifiObject.put("BSSID", result.BSSID);
			            wifiObject.put("capabilities", result.capabilities);
			            wifiObject.put("frequency", result.frequency);
			            wifiObject.put("level", result.level);
			            wifiObject.put("timestamp", result.timestamp);

			            //softwareonair_2.4GHz20==softwareonair_2.4GHz20

			            if(currentSsid.equals(result.SSID)){ 
			            	wifiObject.put("connected", true); 
			            } else {
			            	wifiObject.put("connected", false);
			            }

			            //Other fields not added
			            //wifiObject.put("operatorFriendlyName", result.operatorFriendlyName);
			            //wifiObject.put("venueName", result.venueName);
			            //wifiObject.put("centerFreq0", result.centerFreq0);
			            //wifiObject.put("centerFreq1", result.centerFreq1);
			            //wifiObject.put("channelWidth", result.channelWidth);
						//Log.v("ReactNative", "NETWORKRECEIVED: cssid:" + ssid);
					} catch (JSONException e) {
          				errorCallback.invoke(e.getMessage());
					}
					wifiArray.put(wifiObject);
				}
			}
			successCallback.invoke(wifiArray.toString());
		} catch (IllegalViewOperationException e) {
			errorCallback.invoke(e.getMessage());
		}
	}

	//Method to check if wifi is enabled
	@ReactMethod
	public void isEnabled(Callback isEnabled) {
		isEnabled.invoke(wifi.isWifiEnabled());
	}

	//Method to connect/disconnect wifi service
	@ReactMethod
	public void setEnabled(Boolean enabled) {
		wifi.setWifiEnabled(enabled);
	}

	@ReactMethod
	public void findAndConnect(String ssid, String password, Callback ssidFound) {
		List < ScanResult > results = wifi.getScanResults();

		connection = new ConnectionResult(false, "", 0000);
		for (ScanResult result: results) {
			String resultString = "" + result.SSID;
			if (ssid.equals(resultString)) {


				connection = connectTo(result, password, ssid);

				//connection = cTo(result, password, ssid);
			}
		}

		ssidFound.invoke(connection.toJSON());
	}

	@ReactMethod
	public void connectionStatus(Callback connectionStatusResult) {
		ConnectivityManager connManager = (ConnectivityManager) getReactApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		if (mWifi.isConnected()) {
			connectionStatusResult.invoke(true);
		} else {
			connectionStatusResult.invoke(false);
		}
	}

	public ConnectionResult connectTo(ScanResult result, String password, String ssid) {
		connection = new ConnectionResult(false, "Network ID -1", 1008);
		WifiConfiguration wfc = new WifiConfiguration();
		wfc.SSID = "\"".concat(ssid).concat("\"");
		wfc.status = WifiConfiguration.Status.DISABLED;
		wfc.priority = 40;

		String caps = result.capabilities;

	 	if(caps.indexOf("WPA") > -1 || caps.indexOf("WPA2") > -1){
	 		wfc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
			wfc.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
			wfc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
			wfc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
			wfc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
			wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
			wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
			wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
			wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
			wfc.preSharedKey = "\"".concat(password).concat("\"");
	 	} else if (caps.indexOf("WEP") > -1) {
			wfc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
			wfc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
			wfc.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
			wfc.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
			wfc.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
			wfc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
			wfc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
			wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
			wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
			wfc.wepKeys[0] = "\"".concat(password).concat("\"");
			wfc.wepTxKeyIndex = 0;
	 	} else {
	 		//Open Networks (keyless)
			wfc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
			wfc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
			wfc.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
			wfc.allowedAuthAlgorithms.clear();
			wfc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
			wfc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
			wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
			wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
			wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
			wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
	 	}

	 	authPassed = false;

	 	BroadcastReceiver receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
				//WifiInfo wifiInfo = wifi.getConnectionInfo();
				//SupplicantState supplicantState = wifiInfo.getSupplicantState();
				//NetworkInfo.DetailedState supState = wifiInfo.getDetailedStateOf(supplicantState);				
				
				if(info.getDetailedState().toString() == "AUTHENTICATING"){
					authPassed = true;
				};
				if(authPassed){
					switch(info.getDetailedState().toString()){
						case "CONNECTED": 
							connection.status = true;
							WifiInfo winfo = wifi.getConnectionInfo();
							String ssid = winfo.getSSID();
							if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
								ssid = ssid.substring(1, ssid.length() - 1);
							}
							currentSsid = ssid;
							busy = false;
							authPassed = false;
							getReactApplicationContext().getCurrentActivity().unregisterReceiver(this);
						break;
						case "DISCONNECTED":
							connection.status = false;
					 		connection.statusCode = 1007;
					 		busy = false;
					 		authPassed = false;
							getReactApplicationContext().getCurrentActivity().unregisterReceiver(this);
						break;
						case "AUTHENTICATING":

						break;
						default: 
							Log.v("ReactNative", "NETWORKRECEIVED: " + intent.getAction() + ": DEFAULT HIT=" + info.getDetailedState().toString());
						break;
					};
					
					
				};
			};
		};

		/*
		disconnected ve diğer meseleleri yapılandır

		AUTHENTICATING'den sonra CONNECTED geldiyse bağlandın,
		DISCONNECTED geldiyse bağlanamadın.
		ama şifrenin yanlış olduğunu nereden bileceğiz?

		Apps should instead use the ConnectivityManager.NetworkCallback API to learn about connectivity changes. See ConnectivityManager#registerDefaultNetworkCallback and ConnectivityManager#registerNetworkCallback. These will give a more accurate picture of the connectivity state of the device and let apps react more easily and quickly to changes.
		*/

		IntentFilter filter = new IntentFilter();
		filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		getReactApplicationContext().getCurrentActivity().registerReceiver(receiver, filter);
	 	int networkid = wifi.addNetwork(wfc);

	 	if(networkid != -1){
	 		wifi.disconnect();
	 		wifi.enableNetwork(networkid, true);
	 		wifi.reconnect();
	 		busy = true;
	 		while(true){
				if(!busy){
					break;
				} else { Thread.yield();  Log.v("ReactNative", "YIELDED"); }
			}
	 	}

		return connection;
	 	/*
			bir inline broadcastReceiver tanımlanıp enableNetwork bu bR'ın onCreate'inde çağırılacak,
			ardından bir süre bR beklenip belirli sonuçlara göre bR kapatılacak.


			Bağlantıdaki değişikliklerin izlenebilmesi için uygulanabilecek yöntemler;
			ADB logcat kayıtları baz alınacaktır.

			1) enableNetwork çağırılmadan önce timestamp kaydedilecek, çağrı tamamlandıktan sonra logcat kayıtları çekilip, timestamp'dan itibaren 10 saniyelik
			kayıt incelenecektir.
			2) Thread içerisinde bir watchdog oluşturulup logcat tepkileriyle anlık sonuç üretilecektir.
	 	*/
	}

	public void clearLog(){
     try {
         Process process = new ProcessBuilder()
         .command("logcat", "-c")
         .redirectErrorStream(true)
         .start();
    } catch (IOException e) {
    }
}

	@ReactMethod
	public void disconnect(Callback callback) {
		boolean result = wifi.disconnect();
		callback.invoke(result);
	}

	@ReactMethod
	public void getSSID(final Callback callback) {
		WifiInfo info = wifi.getConnectionInfo();

		// This value should be wrapped in double quotes, so we need to unwrap it.
		String ssid = info.getSSID();
		if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
			ssid = ssid.substring(1, ssid.length() - 1);
		}

		callback.invoke(ssid);
	}

	//This method will return the basic service set identifier (BSSID) of the current access point
	@ReactMethod
	public void getBSSID(final Callback callback) {
		WifiInfo info = wifi.getConnectionInfo();

		String bssid = info.getBSSID();

		callback.invoke(bssid.toUpperCase());
	}

	//This method will return current wifi signal strength
	@ReactMethod
	public void getCurrentSignalStrength(final Callback callback) {
		int linkSpeed = wifi.getConnectionInfo().getRssi();
		callback.invoke(linkSpeed);
	}

	//This method will return current wifi frequency
	@ReactMethod
	public void getFrequency(final Callback callback) {
		WifiInfo info = wifi.getConnectionInfo();
		int frequency = info.getFrequency();
		callback.invoke(frequency);
	}

	//This method will return current IP
	@ReactMethod
	public void getIP(final Callback callback) {
		WifiInfo info = wifi.getConnectionInfo();
		String stringip = longToIP(info.getIpAddress());
		callback.invoke(stringip);
	}

	//This method will remove the wifi network as per the passed SSID from the device list
	@ReactMethod
	public void isRemoveWifiNetwork(String ssid, final Callback callback) {
	    List<WifiConfiguration> mWifiConfigList = wifi.getConfiguredNetworks();
	    for (WifiConfiguration wifiConfig : mWifiConfigList) {
			String comparableSSID = ('"' + ssid + '"'); //Add quotes because wifiConfig.SSID has them
			if(wifiConfig.SSID.equals(comparableSSID)) {
				wifi.removeNetwork(wifiConfig.networkId);
				wifi.saveConfiguration();
				currentSsid = "NONE";
				callback.invoke(true);
				return;
			}
	    }
		callback.invoke(false);
	}

	@ReactMethod
	public void reScanAndLoadWifiList(Callback successCallback, Callback errorCallback) {
		WifiReceiver receiverWifi = new WifiReceiver(wifi, successCallback, errorCallback);
	   	getReactApplicationContext().getCurrentActivity().registerReceiver(receiverWifi, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
	    wifi.startScan();
	}

	@ReactMethod
	public void getDhcpServerAddress(Callback callback) {
		DhcpInfo dhcpInfo = wifi.getDhcpInfo();
		String ip = longToIP(dhcpInfo.serverAddress);
		callback.invoke(ip);
	}

	public static String longToIP(int longIp){
		StringBuffer sb = new StringBuffer("");
		String[] strip=new String[4];
		strip[3]=String.valueOf((longIp >>> 24));
		strip[2]=String.valueOf((longIp & 0x00FFFFFF) >>> 16);
		strip[1]=String.valueOf((longIp & 0x0000FFFF) >>> 8);
		strip[0]=String.valueOf((longIp & 0x000000FF));
		sb.append(strip[0]);
		sb.append(".");
		sb.append(strip[1]);
		sb.append(".");
		sb.append(strip[2]);
		sb.append(".");
		sb.append(strip[3]);
		return sb.toString();
	}

	private WifiConfiguration IsExist(String SSID) {
		List<WifiConfiguration> existingConfigs = wifi.getConfiguredNetworks();
		for (WifiConfiguration existingConfig : existingConfigs) {
			if (existingConfig.SSID.equals("\"" + SSID + "\"")) {
				return existingConfig;
			}
		}
		return null;
	}

	class WifiReceiver extends BroadcastReceiver {

		private Callback successCallback;
		private Callback errorCallback;
		private WifiManager wifi;

		public WifiReceiver(final WifiManager wifi, Callback successCallback, Callback errorCallback) {
			super();
			this.successCallback = successCallback;
			this.errorCallback = errorCallback;
			this.wifi = wifi;
		}

		// This method call when number of wifi connections changed
      	public void onReceive(Context c, Intent intent) {
			c.unregisterReceiver(this);

			try {
				List < ScanResult > results = this.wifi.getScanResults();
				JSONArray wifiArray = new JSONArray();

				for (ScanResult result: results) {
					JSONObject wifiObject = new JSONObject();
					if(!result.SSID.equals("")){
						try {
				            wifiObject.put("SSID", result.SSID);
				            wifiObject.put("BSSID", result.BSSID);
				            wifiObject.put("capabilities", result.capabilities);
				            wifiObject.put("frequency", result.frequency);
				            wifiObject.put("level", result.level);
				            wifiObject.put("timestamp", result.timestamp);
						} catch (JSONException e) {
	          				this.errorCallback.invoke(e.getMessage());
							return;
						}
						wifiArray.put(wifiObject);
					}
				}
				this.successCallback.invoke(wifiArray.toString());
				return;
			} catch (IllegalViewOperationException e) {
				this.errorCallback.invoke(e.getMessage());
				return;
			}
		}
	}
}

