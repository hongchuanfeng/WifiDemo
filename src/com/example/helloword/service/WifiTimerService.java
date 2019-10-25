package com.example.helloword.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


import com.example.helloword.R;
import com.example.helloword.WifiTestActivity;
import com.example.helloword.utils.NetWorkUtils;
import com.example.helloword.utils.WifiAutoConnectManager;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;




public class WifiTimerService extends Service{

	public static final String WIFI_SETTING_KEY="WIFI_SETTING";
	
	public static final String WIFI_NAME_KEY="WIFI_NAME";
	public static final String WIFI_PWD_KEY="WIFI_PWD";
	private SharedPreferences preferences;
	private SharedPreferences.Editor editor;
	
	private static String TAG = WifiTimerService.class.getName();
    private static final long LOOP_TIME = 10; //循环时间
    private static ScheduledExecutorService mExecutorService;

    List<ScanResult> mScanResultList = new ArrayList<ScanResult>();
    
    private BroadcastReceiver mWifiSearchBroadcastReceiver;
    private BroadcastReceiver mWifiConnectBroadcastReceiver;
    
    private WifiAutoConnectManager mWifiAutoConnectManager;
  
    
    ConnectAsyncTask mConnectAsyncTask = null;
    
    private List<ScanResult> mWifiList = new ArrayList<ScanResult>();
    private String currentWifi="hyc";
    private String password = "";//"hyc888888";
    boolean isLinked = false;
    String ssid;
    
    private IntentFilter mWifiSearchIntentFilter;
    private IntentFilter mWifiConnectIntentFilter;
    private int count = 0;

    WifiAutoConnectManager.WifiCipherType type = WifiAutoConnectManager.WifiCipherType.WIFICIPHER_NOPASS;
    

	@Override
	public IBinder onBind(Intent intent) {
		 Log.d(TAG, "onBind");
		return null;
	}
	
	
	@Override
    public void onCreate() {
        super.onCreate();
        
        Log.d(TAG, "onCreate");
        
        preferences = getSharedPreferences(WIFI_SETTING_KEY, MODE_ENABLE_WRITE_AHEAD_LOGGING);
	    editor = preferences.edit();
	    
	    getWifiInfo();
		 
        
        //初始化wifi工具
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mWifiAutoConnectManager = WifiAutoConnectManager.newInstance(wifiManager);
        
        mWifiAutoConnectManager.openWifi();
        
        initWifiSate();
       
        
        registerReceiver(mWifiSearchBroadcastReceiver, mWifiSearchIntentFilter);
        registerReceiver(mWifiConnectBroadcastReceiver, mWifiConnectIntentFilter);
        
        
        mExecutorService = Executors.newScheduledThreadPool(2);
        mExecutorService.scheduleAtFixedRate(mRunnable, LOOP_TIME, LOOP_TIME, TimeUnit.SECONDS);
    
    }

	
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }


    
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        
        unregisterReceiver(mWifiSearchBroadcastReceiver);
        unregisterReceiver(mWifiConnectBroadcastReceiver);
        
        mExecutorService.shutdown();
        mExecutorService = null;
        mRunnable = null;
    }

   
    
    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            count++;
            Log.d(TAG, "============ count:" + count);
            checkWifi();
        }
    };
	

    
    private void getWifiInfo(){
    	ssid = preferences.getString(WifiTimerService.WIFI_NAME_KEY,"hyc");
		password = preferences.getString(WifiTimerService.WIFI_PWD_KEY,"hyc888888");
    }
    
    private ScanResult findWifi() {
    	
    	Log.d(TAG, "====== mScanResultList="+mScanResultList);
    	for (ScanResult sr : mScanResultList) {
    		Log.d(TAG, "====== currentWifi.equals(sr.SSID)="+currentWifi.equals(sr.SSID));
			if(currentWifi.equals(sr.SSID)){
				return sr;
			}
		}
    	return null;
    }
    
    private void checkWifi(){
		   try {
			   
			   mWifiAutoConnectManager.openWifi();
			   
			   boolean isNetwork = NetWorkUtils.isNetWork();
			   Log.d(TAG, "====== isNetwork="+isNetwork);
			   if(isNetwork==false){

				   getWifiInfo();
				   
				   //重连wifi
				   Log.d(TAG, "=== checkWifi:无网络....");
				   
			       Log.d(TAG, "==========currentWifi="+currentWifi);
			       Log.d(TAG, "==========checkWifi=========password="+password);
			        
			     
	
				   ScanResult srResult = findWifi();
				   if(srResult==null) {
					   Log.d(TAG, "=== checkWifi,ScanResult=null ===");
					   return;
				   }
				   
				   ssid = srResult.SSID;
				     
				   Log.d(TAG, "====== checkWifi:ssid="+ssid);
				   type = WifiAutoConnectManager.getCipherType(ssid);
				   Log.d(TAG, "=== findWifi:ssid="+ssid);
				   if (mConnectAsyncTask != null) {
		                mConnectAsyncTask.cancel(true);
		                mConnectAsyncTask = null;
		            }
		            mConnectAsyncTask = new ConnectAsyncTask(ssid, password, type);
		            mConnectAsyncTask.execute();
		            
			   }
			} catch (Exception e) {
				e.printStackTrace();
			}
		   
	  }
    
	
   private void initWifiSate() {
	   
	   
	   //wifi 搜索结果接收广播
       mWifiSearchBroadcastReceiver = new BroadcastReceiver() {
       	
           @Override
           public void onReceive(Context context, Intent intent) {
        	   Log.d(TAG, "====== mWifiSearchBroadcastReceiver onReceive ======");
               String action = intent.getAction();
   
               if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {// 扫描结果改表
                   mScanResultList = WifiAutoConnectManager.getScanResults();
               }
           }
           
       };
       
       
       mWifiSearchIntentFilter = new IntentFilter();
       mWifiSearchIntentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
       mWifiSearchIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
       mWifiSearchIntentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
       
       
    	
        //wifi 状态变化接收广播
        mWifiConnectBroadcastReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                    int wifState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                    if (wifState != WifiManager.WIFI_STATE_ENABLED) {
//                        Toast.makeText(WifiTestActivity.this, "没有wifi", Toast.LENGTH_SHORT).show();
                    }
                } else if (action.equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)) {
                    int linkWifiResult = intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, 123);
                    
                    Log.e("wifidemo", ssid + "linkWifiResult:" + linkWifiResult);
                    
                    if (linkWifiResult == WifiManager.ERROR_AUTHENTICATING) {
                        Log.e("wifidemo", ssid + "onReceive:密码错误");
                        
                    }
                } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                    NetworkInfo.DetailedState state = ((NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO)).getDetailedState();
                   
                    setWifiState(state);
                    
                }
            }
        };
        
        mWifiConnectIntentFilter = new IntentFilter();
        mWifiConnectIntentFilter.addAction(WifiManager.ACTION_PICK_WIFI_NETWORK);
        mWifiConnectIntentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mWifiConnectIntentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        mWifiConnectIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        
     
    
    }

	   
       
    /**
    * 显示wifi状态
    *
    * @param state
    */
    @SuppressLint("NewApi")
   	public void setWifiState(final NetworkInfo.DetailedState state) {
           
     
	  if (state == NetworkInfo.DetailedState.AUTHENTICATING) {

       } else if (state == NetworkInfo.DetailedState.BLOCKED) {

       } else if (state == NetworkInfo.DetailedState.CONNECTED) {
  
           isLinked = true;
          
       } else if (state == NetworkInfo.DetailedState.CONNECTING) {
           isLinked = false;
           
       } else if (state == NetworkInfo.DetailedState.DISCONNECTED) {
           isLinked = false;
           
       } else if (state == NetworkInfo.DetailedState.DISCONNECTING) {
           isLinked = false;
           
       } else if (state == NetworkInfo.DetailedState.FAILED) {
           isLinked = false;

       } else if (state == NetworkInfo.DetailedState.IDLE) {

       } else if (state == NetworkInfo.DetailedState.OBTAINING_IPADDR) {

       } else if (state == NetworkInfo.DetailedState.SCANNING) {

       } else if (state == NetworkInfo.DetailedState.SUSPENDED) {

       }
    }
       
    
   
	   
	   /**
	     * 连接指定的wifi
	   */
	class ConnectAsyncTask extends AsyncTask<Void, Void, Boolean> {
	        private String ssid;
	        private String password;
	        private WifiAutoConnectManager.WifiCipherType type;
	        WifiConfiguration tempConfig;

	        public ConnectAsyncTask(String ssid, String password, WifiAutoConnectManager.WifiCipherType type) {
	            this.ssid = ssid;
	            this.password = password;
	            this.type = type;
	        }

	        @Override
	        protected void onPreExecute() {
	            super.onPreExecute();
	 
	        }

	        @Override
	        protected Boolean doInBackground(Void... voids) {
	        	Log.d("wifidemo", "-------doInBackground-----");
	        	// 打开wifi
	            mWifiAutoConnectManager.openWifi();
	            // 开启wifi功能需要一段时间(我在手机上测试一般需要1-3秒左右)，所以要等到wifi
	            // 状态变成WIFI_STATE_ENABLED的时候才能执行下面的语句
	            while (mWifiAutoConnectManager.wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLING) {
	                try {
	                    // 为了避免程序一直while循环，让它睡个100毫秒检测……
	                    Thread.sleep(100);

	                } catch (InterruptedException ie) {
	                    Log.e("wifidemo", ie.toString());
	                }
	            }

	            tempConfig = mWifiAutoConnectManager.isExsits(ssid);
	            //禁掉所有wifi
	            for (WifiConfiguration c : mWifiAutoConnectManager.wifiManager.getConfiguredNetworks()) {
	                mWifiAutoConnectManager.wifiManager.disableNetwork(c.networkId);
	            }
				
	            if (tempConfig != null) {
	                Log.d("wifidemo", ssid + "配置过！");
	                
	                boolean result = mWifiAutoConnectManager.wifiManager.enableNetwork(tempConfig.networkId, true);
	                Log.d("wifidemo", "==========isLinked="+isLinked);
	                
	                if (!isLinked && type != WifiAutoConnectManager.WifiCipherType.WIFICIPHER_NOPASS) {
	                	
	                	try {
	                    	
	                        Thread.sleep(5000);//超过5s提示失败
	                        
	                        if (!isLinked) {
	                        	
	                            Log.d("wifidemo", ssid + "连接失败！");
	                            //mWifiAutoConnectManager.wifiManager.disableNetwork(tempConfig.networkId);
	                            
	                            Log.d("wifidemo",  "----------------"+ssid+"重新连接中.....");
	                            //如果连接失败  删除原来的networkId 重新假如
	                            mWifiAutoConnectManager.wifiManager.removeNetwork(tempConfig.networkId);
	                            WifiConfiguration wifiConfig = mWifiAutoConnectManager.createWifiInfo(ssid, password, type);
	                            int netID = mWifiAutoConnectManager.wifiManager.addNetwork(wifiConfig);
                                boolean enabled = mWifiAutoConnectManager.wifiManager.enableNetwork(netID, true);
                                Log.d("wifidemo", "enableNetwork status enable=" + enabled);
//                               
                                
	                        }
	                    } catch (Exception e) {
	                        e.printStackTrace();
	                    }
	                }
	                
	                Log.d("wifidemo", "result=" + result);
	                return result;
	                
	            } else {
	            	
	                Log.d("wifidemo", ssid + "没有配置过！");
	                
	                if (type != WifiAutoConnectManager.WifiCipherType.WIFICIPHER_NOPASS) {
	                	
                         new Thread(new Runnable() {
                             @Override
                             public void run() {
                                 WifiConfiguration wifiConfig = mWifiAutoConnectManager.createWifiInfo(ssid, password, type);
                                 if (wifiConfig == null) {
                                     Log.d("wifidemo", "wifiConfig is null!");
                                     return;
                                 }
                                 Log.d("wifidemo", wifiConfig.SSID);

                                 int netID = mWifiAutoConnectManager.wifiManager.addNetwork(wifiConfig);
                                 boolean enabled = mWifiAutoConnectManager.wifiManager.enableNetwork(netID, true);
                                 Log.d("wifidemo", "enableNetwork status enable=" + enabled);
//                                
                                 
                             }
                         }).start();
                         
	                } else {
	                    WifiConfiguration wifiConfig = mWifiAutoConnectManager.createWifiInfo(ssid, password, type);
	                    if (wifiConfig == null) {
	                        Log.d("wifidemo", "wifiConfig is null!");
	                        return false;
	                    }
	                    Log.d("wifidemo", wifiConfig.SSID);
	                    int netID = mWifiAutoConnectManager.wifiManager.addNetwork(wifiConfig);
	                    boolean enabled = mWifiAutoConnectManager.wifiManager.enableNetwork(netID, true);
	                    Log.d("wifidemo", "enableNetwork status enable=" + enabled);
//	                  
	                    return enabled;
	                    
	                }
	                
	                return false;

	            }
	        }

	        @Override
	        protected void onPostExecute(Boolean aBoolean) {
	            super.onPostExecute(aBoolean);
	            
	        }
	    }
	   


}
