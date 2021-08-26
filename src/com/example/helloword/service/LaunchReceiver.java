package com.example.helloword.service;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class LaunchReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context arg0, Intent arg1) {
		// TODO Auto-generated method stub
		Intent tIntent = new Intent(arg0,WifiTimerService.class);  
        //启动指定Service  
		arg0.startService(tIntent);  
	}

}
