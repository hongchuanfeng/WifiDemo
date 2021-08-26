package com.example.helloword.utils;

public class WifiBaseInfo {
	
	private String name;
	
	private String pwd;
	
	

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPwd() {
		return pwd;
	}

	public void setPwd(String pwd) {
		this.pwd = pwd;
	}
	
	

	public WifiBaseInfo(String name, String pwd) {
		super();
		this.name = name;
		this.pwd = pwd;
	}

	public WifiBaseInfo() {
		super();
		
	}

	@Override
	public String toString() {
		return "WifiBaseInfo [name=" + name + ", pwd=" + pwd + "]";
	}
	
	
	

}
