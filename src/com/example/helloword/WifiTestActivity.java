package com.example.helloword;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.example.helloword.service.WifiTimerService;
import com.example.helloword.utils.NetWorkUtils;
import com.example.helloword.utils.SharedPrefUtils;
import com.example.helloword.utils.WifiAutoConnectManager;
import com.example.helloword.utils.WifiBaseInfo;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class WifiTestActivity extends Activity implements View.OnClickListener {


    private SharedPreferences preferences;
    private SharedPreferences.Editor editor;
    
	EditText et_pwd;
	TextView mWifiState;//wifi状态
    TextView mWifiName;//Wi-Fi名称
    TextView mMac;//物理地址
    TextView mIP;//ip地址
    TextView mGateway;//网关地址
    ListView mListWifi;//Wi-Fi列表
    Button mBtnSearch;//搜索Wi-Fi
    Button mBtnConnect;//连接Wi-Fi
    WifiListAdapter mWifiListAdapter;
    public static final int WIFI_SCAN_PERMISSION_CODE = 2;
    WorkAsyncTask mWorkAsyncTask = null;
    ConnectAsyncTask mConnectAsyncTask = null;
    List<ScanResult> mScanResultList = new ArrayList<ScanResult>();
    public static String ssid = "";
    WifiAutoConnectManager.WifiCipherType type = WifiAutoConnectManager.WifiCipherType.WIFICIPHER_NOPASS;
    public static String password = "";
    FrameLayout progressbar;
    boolean isLinked = false;

    String gateway = "";
    String mac = "";
    
    SharedPrefUtils shared;
    String currentSsidString = "";
    String currentPassword = "";
    
    /**
     * 处理信号量改变或者扫描结果改变的广播
     */
    private BroadcastReceiver mWifiSearchBroadcastReceiver;
    private IntentFilter mWifiSearchIntentFilter;
    private BroadcastReceiver mWifiConnectBroadcastReceiver;
    private IntentFilter mWifiConnectIntentFilter;
    private WifiAutoConnectManager mWifiAutoConnectManager;
    
	    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_wifi_test);
		
//		 preferences = getSharedPreferences(WifiTimerService.WIFI_SETTING_KEY, MODE_ENABLE_WRITE_AHEAD_LOGGING);
//		 editor = preferences.edit();
//		 ssid = preferences.getString(WifiTimerService.WIFI_NAME_KEY,"");
//		 password = preferences.getString(WifiTimerService.WIFI_PWD_KEY,"");
	     
		
        //初始化wifi工具
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mWifiAutoConnectManager = WifiAutoConnectManager.newInstance(wifiManager);

        
		//当前连接的wifi
		currentSsidString = WifiAutoConnectManager.getSSID();
		
		
		shared = new SharedPrefUtils(WifiTestActivity.this);
		if(currentSsidString!=null){
			WifiBaseInfo wifiBaseInfo =  shared.getData(currentSsidString, "");
			if(wifiBaseInfo!=null){
				ssid = wifiBaseInfo.getName();
				password = wifiBaseInfo.getPwd();
				
				currentPassword = password;
			}
		}
		
		
		
		Intent intent = new Intent(WifiTestActivity.this, WifiTimerService.class);
		  //使用Intent传值
	    intent.putExtra("key","value");
	    startService(intent);
	    
	    
		intiView();
	
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            // 获取wifi连接需要定位权限,没有获取权限
//            ActivityCompat.requestPermissions(this, new String[]{
//                    Manifest.permission.ACCESS_COARSE_LOCATION,
//            }, WIFI_SCAN_PERMISSION_CODE);
//            return;
//        }
        
        
        //设置监听wifi状态变化广播
        initWifiSate();
	}

	

	@Override
	public void onClick(View arg0) {
		switch (arg0.getId()) {
        case R.id.search_wifi:
            if (mWorkAsyncTask != null) {
                mWorkAsyncTask.cancel(true);
                mWorkAsyncTask = null;
            }
            mWorkAsyncTask = new WorkAsyncTask();
            mWorkAsyncTask.execute();
            break;
        case R.id.connect_wifi:

            if (ssid.equals(WifiAutoConnectManager.getSSID())) {
                return;
            }
            String pwdString = et_pwd.getText().toString().trim();
            Log.i("pwdString", "-----pwdString:"+pwdString);
            Log.i("ssid", "--------ssid:"+ssid);
            if(TextUtils.isEmpty(ssid)){
            	Toast.makeText(WifiTestActivity.this, "请先选择一个wifi", Toast.LENGTH_SHORT).show();
            	return;
            }
            
            if(TextUtils.isEmpty(pwdString)){
            	Toast.makeText(WifiTestActivity.this, "请先填写wifi密码", Toast.LENGTH_SHORT).show();
            	return;
            }
            
            password = pwdString;
            
            saveWifiInfo(ssid, password);
            
            if (mConnectAsyncTask != null) {
                mConnectAsyncTask.cancel(true);
                mConnectAsyncTask = null;
            }
            mConnectAsyncTask = new ConnectAsyncTask(ssid, password, type);
            mConnectAsyncTask.execute();
            break;
        default:
            break;
		}
		
	}
	
	@Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mWifiSearchBroadcastReceiver, mWifiSearchIntentFilter);
        registerReceiver(mWifiConnectBroadcastReceiver, mWifiConnectIntentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mWifiSearchBroadcastReceiver);
        unregisterReceiver(mWifiConnectBroadcastReceiver);
    }
    
 
    
    private boolean saveWifiInfo(String wifi,String pwd){
    	
//        editor.putString(WifiTimerService.WIFI_NAME_KEY,wifi);
//        editor.putString(WifiTimerService.WIFI_PWD_KEY,pwd);
//        return editor.commit();
    	return shared.saveData(wifi, pwd);
    }
    
 
    
    private void initWifiSate() {
    	
        //wifi 搜索结果接收广播
        mWifiSearchBroadcastReceiver = new BroadcastReceiver() {
        	
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {// 扫描结果改表
                    mScanResultList = WifiAutoConnectManager.getScanResults();
                    if (mWifiListAdapter != null) {
                    	
                        mWifiListAdapter.setmWifiList(mScanResultList);
                        
                        mWifiListAdapter.notifyDataSetChanged();
                    }
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
                        Toast.makeText(WifiTestActivity.this, "没有wifi", Toast.LENGTH_SHORT).show();
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
            progressbar.setVisibility(View.GONE);
            isLinked = true;
            mWifiState.setText("wifi state:连接成功");
            mWifiName.setText("wifi name:" + WifiAutoConnectManager.getSSID());
            mIP.setText("ip address:" + WifiAutoConnectManager.getIpAddress());
            mGateway.setText("gateway:" + WifiAutoConnectManager.getGateway());
            mMac.setText("mac:" + WifiAutoConnectManager.getMacAddress());
            gateway = WifiAutoConnectManager.getGateway();
            mac = WifiAutoConnectManager.getMacAddress();
        } else if (state == NetworkInfo.DetailedState.CONNECTING) {
            isLinked = false;
            mWifiState.setText("wifi state:连接中...");
            mWifiName.setText("wifi name:" + WifiAutoConnectManager.getSSID());
            mIP.setText("ip address");
            mGateway.setText("gateway");
        } else if (state == NetworkInfo.DetailedState.DISCONNECTED) {
            isLinked = false;
            mWifiState.setText("wifi state:断开连接");
            mWifiName.setText("wifi name");
            mIP.setText("ip address");
            mGateway.setText("gateway");
        } else if (state == NetworkInfo.DetailedState.DISCONNECTING) {
            isLinked = false;
            mWifiState.setText("wifi state:断开连接中...");
        } else if (state == NetworkInfo.DetailedState.FAILED) {
            isLinked = false;
            mWifiState.setText("wifi state:连接失败");
        } else if (state == NetworkInfo.DetailedState.IDLE) {

        } else if (state == NetworkInfo.DetailedState.OBTAINING_IPADDR) {

        } else if (state == NetworkInfo.DetailedState.SCANNING) {

        } else if (state == NetworkInfo.DetailedState.SUSPENDED) {

        }
    }

    private void intiView() {
    	
        progressbar = (FrameLayout) findViewById(R.id.progressbar);
        mWifiState = (TextView) findViewById(R.id.wifi_state);
        mWifiName = (TextView) findViewById(R.id.wifi_name);
        mMac = (TextView) findViewById(R.id.wifi_mac);
        mIP = (TextView) findViewById(R.id.ip_address);
        mGateway = (TextView) findViewById(R.id.ip_gateway);
        mListWifi = (ListView) findViewById(R.id.list_wifi);
        mBtnSearch = (Button) findViewById(R.id.search_wifi);
        mBtnConnect = (Button) findViewById(R.id.connect_wifi);
        
        et_pwd = (EditText) findViewById(R.id.et_pwd);
        
       
        

        mBtnSearch.setOnClickListener(this);
        mBtnConnect.setOnClickListener(this);

        mListWifi.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                mWifiListAdapter.setSelectItem(i);
                
                mWifiListAdapter.notifyDataSetChanged();
                
                ScanResult scanResult = mScanResultList.get(i);
                ssid = scanResult.SSID;
                type = WifiAutoConnectManager.getCipherType(ssid);
            }
        });
    }
    
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case WIFI_SCAN_PERMISSION_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                } else {
                    // 不允许
                    Toast.makeText(this, "开启权限失败", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }

    /**
     * 获取wifi列表
     */
    private class WorkAsyncTask extends AsyncTask<Void, Void, List<ScanResult>> {
        private List<ScanResult> mScanResult = new ArrayList<>();

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressbar.setVisibility(View.VISIBLE);
        }

        @Override
        protected List<ScanResult> doInBackground(Void... params) {
            if (WifiAutoConnectManager.startStan()) {
                mScanResult = WifiAutoConnectManager.getScanResults();
            }
            List<ScanResult> filterScanResultList = new ArrayList<>();
            if (mScanResult != null) {
                for (ScanResult wifi : mScanResult) {
                    filterScanResultList.add(wifi);
                    Log.e("wifidemo", "doInBackground: wifi:" + wifi);
                }
            }
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return filterScanResultList;
        }

        @Override
        protected void onPostExecute(final List<ScanResult> result) {
            super.onPostExecute(result);
            progressbar.setVisibility(View.GONE);
            mScanResultList = result;
            mWifiListAdapter = new WifiListAdapter(result, LayoutInflater.from(WifiTestActivity.this));
            
            mListWifi.setAdapter(mWifiListAdapter);

        }
    }

    /**
     * wifi列表适配器
     */
    class WifiListAdapter extends BaseAdapter {

        private List<ScanResult> mWifiList;
        private LayoutInflater mLayoutInflater;

        public WifiListAdapter(List<ScanResult> wifiList, LayoutInflater layoutInflater) {
            this.mWifiList = wifiList;
            this.mLayoutInflater = layoutInflater;
        }

        @Override
        public int getCount() {
            return mWifiList.size();
        }

        @Override
        public Object getItem(int position) {
            return mWifiList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            if (convertView == null) {
                convertView = mLayoutInflater.inflate(R.layout.fragment_wifi_list_item, null);
            }
            ScanResult sr = mWifiList.get(position);
            convertView.setTag(sr);
            TextView textView = (TextView) convertView.findViewById(R.id.wifi_item_name);
            int numLevel = WifiAutoConnectManager.getSignalNumsLevel(sr.level, 5);
            String password = sr.capabilities;
            String text = "加密方式:";
            if (password.contains("WPA") || password.contains("wpa")) {
                password = "WPA";
            } else if (password.contains("WEP") || password.contains("wep")) {
                password = "WEP";
            } else {
                text = "";
                password = "";
            }
            textView.setText(sr.SSID + " " + text + password + "  信号强度：" + numLevel);
            convertView.setBackgroundColor(Color.WHITE);
            if (position == selectItem) {
                convertView.setBackgroundColor(Color.GRAY);
            }
            return convertView;
        }

        public void setSelectItem(int selectItem) {
            this.selectItem = selectItem;
        }

        private int selectItem = -1;

        public void setmWifiList(List<ScanResult> mWifiList) {
            this.mWifiList = mWifiList;
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
            progressbar.setVisibility(View.VISIBLE);
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
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
                Log.d("wifidemo", ssid + "密码："+password);
                boolean result = mWifiAutoConnectManager.wifiManager.enableNetwork(tempConfig.networkId, true);
                
                if (!isLinked && type != WifiAutoConnectManager.WifiCipherType.WIFICIPHER_NOPASS) {
                    
                	Log.d("wifidemo", "---------isLinked="+isLinked);
                	try {
                        Thread.sleep(5000);//超过5s提示失败
                        if (!isLinked) {
                            Log.d("wifidemo", ssid + "连接失败！");
                            mWifiAutoConnectManager.wifiManager.disableNetwork(tempConfig.networkId);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progressbar.setVisibility(View.GONE);
                                    
                                    Log.d("wifidemo",  "----------------"+ssid+"重新连接中.....");
                                    Log.d("wifidemo", ssid + "配置过22222！");
                                    Log.d("wifidemo", ssid + "密2222码："+password);
                                    //如果连接失败  删除原来的networkId 重新假如
    	                            mWifiAutoConnectManager.wifiManager.removeNetwork(tempConfig.networkId);
    	                            WifiConfiguration wifiConfig = mWifiAutoConnectManager.createWifiInfo(ssid, password, type);
    	                            int netID = mWifiAutoConnectManager.wifiManager.addNetwork(wifiConfig);
                                    boolean enabled = mWifiAutoConnectManager.wifiManager.enableNetwork(netID, true);
                                    Log.d("wifidemo", "enableNetwork status enable=" + enabled);
//                                   
//                                    boolean isNetwork = NetWorkUtils.isNetWork();
//                                    
//                                    if(isNetwork==false){
//                                    	Log.d("wifidemo",  "----------------"+ssid+"第二次重新连接中.....");
//                                    	
//                                    	mWifiAutoConnectManager.wifiManager.removeNetwork(tempConfig.networkId);
//        	                            wifiConfig = mWifiAutoConnectManager.createWifiInfo(ssid, password, type);
//        	                            netID = mWifiAutoConnectManager.wifiManager.addNetwork(wifiConfig);
//                                        enabled = mWifiAutoConnectManager.wifiManager.enableNetwork(netID, true);
//                                        Log.d("wifidemo", "enableNetwork status enable=" + enabled);
//                                    }
                                    	
                                    
                                    
                                    
//                                    Toast.makeText(getApplicationContext(), "连接失败!请在系统里删除wifi连接，重新连接。", Toast.LENGTH_SHORT).show();
//                                    new AlertDialog.Builder(WifiTestActivity.this)
//                                            .setTitle("连接失败！")
//                                            .setMessage("请在系统里删除wifi连接，重新连接。")
//                                            .setNegativeButton("取消", new DialogInterface.OnClickListener() {
//                                                @Override
//                                                public void onClick(DialogInterface dialog, int which) {
//                                                    dialog.dismiss();
//                                                }
//                                            })
//                                            .setPositiveButton("好的", new DialogInterface.OnClickListener() {
//                                                public void onClick(DialogInterface dialog, int which) {
//                                                    Intent intent = new Intent();
//                                                    intent.setAction("android.net.wifi.PICK_WIFI_NETWORK");
//                                                    startActivity(intent);
//                                                }
//                                            }).show();
//                                    
                                }
                            });
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
                return result;
            } else {
                Log.d("wifidemo", ssid + "没有配置过！");
                Log.d("wifidemo", ssid + "密码："+password);
                
                if (type != WifiAutoConnectManager.WifiCipherType.WIFICIPHER_NOPASS) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            
                            saveWifiInfo(ssid, password);
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                	
                                	Log.d("wifidemo", "runrunrunrunrunrunrun");
                                    WifiConfiguration wifiConfig = mWifiAutoConnectManager.createWifiInfo(ssid, password,
                                            type);
                                    if (wifiConfig == null) {
                                        Log.d("wifidemo", "wifiConfig is null!");
                                        return;
                                    }
                                    
                                    Log.d("wifidemo", "----没有配置过---ssid:"+wifiConfig.SSID);

                                    int netID = mWifiAutoConnectManager.wifiManager.addNetwork(wifiConfig);
                                    boolean enabled = mWifiAutoConnectManager.wifiManager.enableNetwork(netID, true);
                                    Log.d("wifidemo", "enableNetwork status enable=" + enabled);
//                                    Log.d("wifidemo", "enableNetwork connected=" + mWifiAutoConnectManager.wifiManager.reconnect());
//                                    mWifiAutoConnectManager.wifiManager.reconnect();
                                    
                                    
                                    
                                    
                                    if(enabled==false){
                                    
                                    	Log.d("wifidemo", "333333333=");
                                    	mWifiAutoConnectManager.wifiManager.removeNetwork(netID);
                                        wifiConfig = mWifiAutoConnectManager.createWifiInfo(ssid, password, type);
                                        netID = mWifiAutoConnectManager.wifiManager.addNetwork(wifiConfig);
                                        enabled = mWifiAutoConnectManager.wifiManager.enableNetwork(netID, true);
                                        Log.d("wifidemo", "33333333,enabled="+enabled);
                                    }
                                    
                                }
                            }).start();
                            
                            
//                          final EditText inputServer = new EditText(WifiTestActivity.this);
//                            new AlertDialog.Builder(WifiTestActivity.this)
//                                    .setTitle("请输入密码")
//                                    .setView(inputServer)
//                                    .setNegativeButton("取消", new DialogInterface.OnClickListener() {
//                                        @Override
//                                        public void onClick(DialogInterface dialog, int which) {
//                                            dialog.dismiss();
//                                        }
//                                    })
//                                    .setPositiveButton("连接", new DialogInterface.OnClickListener() {
//
//                                        public void onClick(DialogInterface dialog, int which) {
//                                            password = inputServer.getText().toString();
//                                            
//                                            saveWifiInfo(ssid, password);
//                                            new Thread(new Runnable() {
//                                                @Override
//                                                public void run() {
//                                                    WifiConfiguration wifiConfig = mWifiAutoConnectManager.createWifiInfo(ssid, password,
//                                                            type);
//                                                    if (wifiConfig == null) {
//                                                        Log.d("wifidemo", "wifiConfig is null!");
//                                                        return;
//                                                    }
//                                                    Log.d("wifidemo", wifiConfig.SSID);
//
//                                                    int netID = mWifiAutoConnectManager.wifiManager.addNetwork(wifiConfig);
//                                                    boolean enabled = mWifiAutoConnectManager.wifiManager.enableNetwork(netID, true);
//                                                    Log.d("wifidemo", "enableNetwork status enable=" + enabled);
////                                                    Log.d("wifidemo", "enableNetwork connected=" + mWifiAutoConnectManager.wifiManager.reconnect());
////                                                    mWifiAutoConnectManager.wifiManager.reconnect();
//                                                }
//                                            }).start();
//                                        }
//                                    }).show();
                        }
                    });
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
//                    Log.d("wifidemo", "enableNetwork connected=" + mWifiAutoConnectManager.wifiManager.reconnect());
//                    return mWifiAutoConnectManager.wifiManager.reconnect();
                    return enabled;
                }
                return false;


            }
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            mConnectAsyncTask = null;
        }
    }


    
	
}
