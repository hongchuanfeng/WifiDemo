package com.example.helloword.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


import com.example.helloword.utils.NetWorkUtils;
import com.example.helloword.utils.SharedPrefUtils;
import com.example.helloword.utils.WifiAutoConnectManager;
import com.example.helloword.utils.WifiBaseInfo;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;




public class WifiTimerService extends Service{

	public static final String WIFI_SETTING_KEY="WIFI_SETTING";
	
	public static final String WIFI_NAME_KEY="WIFI_NAME";
	public static final String WIFI_PWD_KEY="WIFI_PWD";
	private SharedPreferences preferences;
	private SharedPreferences.Editor editor;
	
	private static String TAG = WifiTimerService.class.getName();
    private static final long LOOP_TIME = 15; //循环时间
    private static ScheduledExecutorService mExecutorService;

    List<ScanResult> mScanResultList = new ArrayList<ScanResult>();
    
    private BroadcastReceiver mWifiSearchBroadcastReceiver;
    private BroadcastReceiver mWifiConnectBroadcastReceiver;
    
    private WifiAutoConnectManager mWifiAutoConnectManager;
  
    
    ConnectAsyncTask mConnectAsyncTask = null;
    
    private List<ScanResult> mWifiList = new ArrayList<ScanResult>();
    private String currentWifi="";
    private String password = "";
    boolean isLinked = false;
    String ssid;
    
    private IntentFilter mWifiSearchIntentFilter;
    private IntentFilter mWifiConnectIntentFilter;
    private int count = 0;

    private WifiAutoConnectManager.WifiCipherType type = WifiAutoConnectManager.WifiCipherType.WIFICIPHER_NOPASS;
    
    private SharedPrefUtils shared;
    
    private Map wifimap = new HashMap(); 
    
    private boolean isNotRunning = true;

	@Override
	public IBinder onBind(Intent intent) {
		 Log.d(TAG, "onBind");
		return null;
	}
	
	
	@Override
    public void onCreate() {
        super.onCreate();
        
        Log.d(TAG, "onCreate");
        
        
        
//        preferences = getSharedPreferences(WIFI_SETTING_KEY, MODE_ENABLE_WRITE_AHEAD_LOGGING);
//	    editor = preferences.edit();
      //初始化wifi工具
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mWifiAutoConnectManager = WifiAutoConnectManager.newInstance(wifiManager);
        
        mWifiAutoConnectManager.openWifi();
        
        shared = new SharedPrefUtils(WifiTimerService.this);
	    
        currentWifi = WifiAutoConnectManager.getSSID();
        if(currentWifi!=null){
        	WifiBaseInfo wifiBaseInfo =  shared.getData(currentWifi, "");
    		if(wifiBaseInfo!=null){
    			ssid = wifiBaseInfo.getName();
    			password = wifiBaseInfo.getPwd();
    			
    			currentWifi = ssid;
    		}
        }

  
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
	

    
    private boolean getWifiInfo(){

    
		List<WifiBaseInfo> list =  shared.getListData();
		Log.i(TAG, "-------getWifiInfo():length="+list.size());
		if(list.size()==0){
			return false;
		}
		
		for (WifiBaseInfo wifiBaseInfo : list) {
	
			Log.i(TAG, wifiBaseInfo.toString());
			Log.i(TAG, "-------wifi Name ="+wifiBaseInfo.getName());
			if(isExistWifi(wifiBaseInfo.getName())){
				
				Log.i(TAG, "-------wifiBaseInfo.getName()"+wifiBaseInfo.getName());
				if(!wifimap.containsKey(wifiBaseInfo.getName())){
					wifimap.put(wifiBaseInfo.getName(), true);
				}
				
			}
		}
		
		checkWifiState();
		
		return true;
    }
    
    private void checkWifiState(){
    	 int closeCount = 0;
    	 Set<String> keys = wifimap.keySet(); 
         for (String key:keys) {
        	 boolean isCheck = (boolean)wifimap.get(key);
        	 if(isCheck==false){
        		 closeCount = closeCount+1;
        	 }
         }
         
         if(wifimap.size() > 0 && closeCount==wifimap.size()){
        	 for (String key:keys) {
        		 wifimap.put(key, true);
             }
         }
    }
    
    private String findWifi() {

    	Iterator iterator = wifimap.keySet().iterator();                
        while (iterator.hasNext()) {    
        	Object key = iterator.next(); 
        	Log.d(TAG, "====== findWifi key="+key + ",value="+wifimap.get(key));
        	if((boolean) wifimap.get(key)) return key.toString();         
        }  
        return null;
    }
    
    private boolean isExistWifi(String ssid){
    	Log.d(TAG, "====== mScanResultList.size ="+mScanResultList.size());
    	if(mScanResultList==null || mScanResultList.size()==0){
    		mScanResultList = WifiAutoConnectManager.getScanResults();
    	}
    	
    	Log.d(TAG, "====== mScanResultList.size ="+mScanResultList.size());
    	for (ScanResult sr : mScanResultList) {
    		Log.d(TAG, "====== current ssid:"+ssid+",sr.SSID="+sr.SSID);
    		Log.d(TAG, "====== currentWifi.equals(sr.SSID)="+ssid.equals(sr.SSID));
			if(ssid.equals(sr.SSID)){
				return true;
			}
		}
    	return false;
    }
    
    private void updateWifiState(String ssid,boolean state){
    	   Set<String> keys = wifimap.keySet(); 
           for (String key:keys) {
        	   if(key.equals(ssid)){
        		   wifimap.put(ssid, state);
        	   }
           }
    }
    
    private void checkWifi(){
		   try {
			   
			   mWifiAutoConnectManager.openWifi();
			   
			   boolean isNetwork = NetWorkUtils.isNetWork();
//			   boolean isNetwork = NetWorkUtils.ping();
			   Log.d(TAG, "====== isNetwork="+isNetwork);
			   if(isNetwork==false){

				   Log.d(TAG, "................无网络无网络无网络................");
				   
				   boolean isSuc = getWifiInfo();
				   if(isSuc==false){
					   Log.d(TAG, "=== getWifiInfo()=false ===");
					   return;
				   }
	
	
			       ssid = findWifi();
			       Log.d(TAG, "=== checkWifi,SSID="+ssid);
				   if(ssid==null) {
					   Log.d(TAG, "=== checkWifi,SSID=null ===");
					   return;
				   }

				   WifiBaseInfo wifiBaseInfo = shared.getData(ssid, "");
				   if(wifiBaseInfo==null){
					   Log.d(TAG, "=== checkWifi,wifiBaseInfo=null ===");
					   return;
				   }
				   password = wifiBaseInfo.getPwd();
				   Log.d(TAG, "=== 当前连接的Wifi:ssid="+ssid);
				   Log.d(TAG, "====== 当前连接的WiFi的password="+password);
				   type = WifiAutoConnectManager.getCipherType(ssid);
				   
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
           Log.i(TAG, "-----------wifi:"+ssid+",pwd:"+password+","+ssid+"已经成功连上网络....");
       } else if (state == NetworkInfo.DetailedState.CONNECTING) {
           isLinked = false;
           Log.i(TAG, "-----------wifi:"+ssid+",pwd:"+password+","+ssid+"连上失败....");
           
       } else if (state == NetworkInfo.DetailedState.DISCONNECTED) {
           isLinked = false;
           Log.i(TAG, "-----------wifi:"+ssid+",pwd:"+password+","+ssid+"连上失败....");
       } else if (state == NetworkInfo.DetailedState.DISCONNECTING) {
           isLinked = false;
           Log.i(TAG, "-----------wifi:"+ssid+",pwd:"+password+","+ssid+"连上失败....");
       } else if (state == NetworkInfo.DetailedState.FAILED) {
           isLinked = false;
           Log.i(TAG, "-----------wifi:"+ssid+",pwd:"+password+","+ssid+"连上失败....");
       } else if (state == NetworkInfo.DetailedState.IDLE) {

       } else if (state == NetworkInfo.DetailedState.OBTAINING_IPADDR) {

       } else if (state == NetworkInfo.DetailedState.SCANNING) {

       } else if (state == NetworkInfo.DetailedState.SUSPENDED) {

       }
	  
	  updateWifiState(ssid,isLinked);

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
	        	Log.d("wifidemo", "-------doInBackground-----ssid="+ssid);
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
                                if(enabled){
                                	boolean isNetwork = NetWorkUtils.isNetWork();
                                    updateWifiState(ssid, isNetwork);
                                }else{
                                	updateWifiState(ssid, enabled);
                                }
	                        }
	                    } catch (Exception e) {
	                        e.printStackTrace();
	                    }
	                }
	                
	                Log.d("wifidemo", "result=" + result);
	                if(result==false){
	                	Log.d("wifidemo", "result2222222222222222=");
	                	mWifiAutoConnectManager.wifiManager.removeNetwork(tempConfig.networkId);
	                    WifiConfiguration wifiConfig = mWifiAutoConnectManager.createWifiInfo(ssid, password, type);
	                    int netID = mWifiAutoConnectManager.wifiManager.addNetwork(wifiConfig);
	                    boolean enabled = mWifiAutoConnectManager.wifiManager.enableNetwork(netID, true);
	                    Log.d("wifidemo", "22222222222enabled="+enabled);
	                }
	                
	                if(result){
                    	boolean isNetwork = NetWorkUtils.isNetWork();
                        updateWifiState(ssid, isNetwork);
                    }else{
                    	updateWifiState(ssid, result);
                    }
	                
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
                                 
                                 if(enabled==false){
                                     
                                 	Log.d("wifidemo", "333333333=");
                                 	mWifiAutoConnectManager.wifiManager.removeNetwork(netID);
                                     wifiConfig = mWifiAutoConnectManager.createWifiInfo(ssid, password, type);
                                     netID = mWifiAutoConnectManager.wifiManager.addNetwork(wifiConfig);
                                     enabled = mWifiAutoConnectManager.wifiManager.enableNetwork(netID, true);
                                     Log.d("wifidemo", "33333333,enabled="+enabled);
                                 
                                 }
                                 
                                 if(enabled){
                                 	boolean isNetwork = NetWorkUtils.isNetWork();
                                     updateWifiState(ssid, isNetwork);
                                 }else{
                                 	updateWifiState(ssid, enabled);
                                 }
                                 
                                
                                 
                             }
                         }).start();
                         
	                } else {
	                	
	                	Log.d("wifidemo", ssid + "没有配置过！WifiAutoConnectManager.WifiCipherType.WIFICIPHER_NOPASS");
	                    
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
	                    if(enabled){
                         	boolean isNetwork = NetWorkUtils.isNetWork();
                             updateWifiState(ssid, isNetwork);
                         }else{
                         	updateWifiState(ssid, enabled);
                         }
                         
	                    
	                }
	                
	                return false;

	            }
	        }

	        @Override
	        protected void onPostExecute(Boolean aBoolean) {
	            super.onPostExecute(aBoolean);
	            
	        }
	    }
	   

	private void openWifi(final String ssid,final String password,final WifiAutoConnectManager.WifiCipherType type){
		
		
		new Thread(new Runnable() {
            @Override
            public void run() {
                
            	
        		Log.d("wifidemo", "-------doInBackground-----");
            	Log.d("wifidemo", "-------doInBackground-----ssid="+ssid);
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

                WifiConfiguration tempConfig = mWifiAutoConnectManager.isExsits(ssid);
                //禁掉所有wifi
                for (WifiConfiguration c : mWifiAutoConnectManager.wifiManager.getConfiguredNetworks()) {
                    mWifiAutoConnectManager.wifiManager.disableNetwork(c.networkId);
                }
        		
                mWifiAutoConnectManager.wifiManager.removeNetwork(tempConfig.networkId);
                WifiConfiguration wifiConfig = mWifiAutoConnectManager.createWifiInfo(ssid, password, type);
                int netID = mWifiAutoConnectManager.wifiManager.addNetwork(wifiConfig);
                boolean enabled = mWifiAutoConnectManager.wifiManager.enableNetwork(netID, true);
                //Log.d("wifidemo", "enableNetwork status enable=" + enabled);
            }
        }).start();
		
	
	}

}
