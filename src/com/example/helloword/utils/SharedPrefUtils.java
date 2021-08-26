package com.example.helloword.utils;

import java.util.ArrayList;
import java.util.List;

import com.example.helloword.service.WifiTimerService;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;


public class SharedPrefUtils {
	
	private static String TAG = SharedPrefUtils.class.getName();
	
	public static final String WIFI_NAME_KEY="WIFI_KEY";
	public static final String WIFI_NAME_PWD_KEY="WIFI_NAME_PWD_KEY";
	
	private Context context;
	private SharedPreferences preferences;
	private SharedPreferences.Editor editor;
	
	public SharedPrefUtils(Context context){
		this.context = context;
		preferences= this.context.getSharedPreferences(WIFI_NAME_KEY, this.context.MODE_PRIVATE);
		editor = preferences.edit();
	}
	
	
	public WifiBaseInfo getData(String name,String pwd){
		Log.i(TAG, "--------WifiBaseInfo getData[name="+name+",pwd="+pwd+"]----");
		String tempWifi = "";
		String[] array = getData();
		if(array==null) return null;
		for (int i = 0; i < array.length; i++) {
			String string = array[i];
			if(string!=null && string.contains(name)){
				tempWifi = string;
				break;
			}
		}
		
		if(tempWifi == "" || !tempWifi.contains("$")){
			return null;
		}
		String[] wifiArr = tempWifi.split("\\$");
		if(wifiArr==null || wifiArr.length<2) {
			return null;
		}
		WifiBaseInfo wifi = new WifiBaseInfo(wifiArr[0],wifiArr[1]);
		if(wifi!=null){
			Log.i(TAG, "----------WifiBaseInfo:"+wifi);
		}
		
		return wifi;
	}
	
	public String[] getData(){
		Log.i(TAG, "----------String[] getData()----------");
		String nameAndPwd = preferences.getString(WIFI_NAME_PWD_KEY,"");
		if(nameAndPwd==""){
			return null;
		}
		String[] array = nameAndPwd.split(",");
		if(array!=null){
			Log.i(TAG, "----------String[]:"+array);
		}
		
		return array;
	}
	
	public List<WifiBaseInfo> getListData(){
		Log.i(TAG, "----------getListData-----");
		List<WifiBaseInfo> list = new ArrayList<WifiBaseInfo>();
		String nameAndPwd = preferences.getString(WIFI_NAME_PWD_KEY,"");
		Log.i(TAG, "----------nameAndPwd="+nameAndPwd);
		if(nameAndPwd==""){
			return null;
		}
		String[] array = nameAndPwd.split(",");
		if(array==null) return null;
		for (int i = 0; i < array.length; i++) {
			String string = array[i];
			if(string==null) continue;
			
			if(!string.contains("$")){
				continue;
			}
			Log.i(TAG, "----------wifi Data:"+string);
			String[] wifiArr = string.split("\\$");
			Log.i(TAG, "----------wifi Data:wifiArr[0]="+wifiArr[0]);
			Log.i(TAG, "----------wifi Data:wifiArr[1]="+wifiArr[1]);
			WifiBaseInfo wifi = new WifiBaseInfo(wifiArr[0],wifiArr[1]);
			list.add(wifi);
		}
		return list;
	}
	
	
	public boolean saveData(String name,String pwd){
		Log.i(TAG, "----------saveData-----");
		String nameAndPwd = preferences.getString(WIFI_NAME_PWD_KEY,"");
		Log.i(TAG, "----------nameAndPwd="+nameAndPwd);
		String saveData = "";
		if(nameAndPwd==""){
			 saveData = name+"$"+pwd;
		}else if(nameAndPwd.contains(name)){
			Log.i(TAG, "----------nameAndPwd.contains(name)----------------");
			String[] array = nameAndPwd.split(",");
			
			String [] tempArray = new String[array.length];
			for (int i = 0; i < array.length; i++) {
				String string = array[i];
				if(string!=null &&  string.contains(name)){
					int index = string.indexOf("$");
					string = string.substring(0,index)+"$"+pwd;
				}
				tempArray[i]= string;
			}
			
			saveData = join(tempArray,",");
		
		}else {
			saveData = nameAndPwd+","+name+"$"+pwd;
		}
		Log.i(TAG, "----------saveData="+saveData);
		editor.putString(WIFI_NAME_PWD_KEY,saveData);
		boolean isOk = editor.commit();
		Log.i(TAG, "----------isOk="+isOk);
	    return isOk;
	}
	
	private String join(String[] array,String ch){
		Log.i(TAG, "---------length:"+array.length);
		StringBuffer sb = new StringBuffer();
		for(int i = 0; i < array.length; i++) {
		    if(i==array.length-1){
		    	sb.append(array[i]);
		    }else{
		    	sb.append(array[i]+ch);
		    }
		}
		Log.i(TAG, "---------join:"+sb.toString());
		return sb.toString();
	}

}
