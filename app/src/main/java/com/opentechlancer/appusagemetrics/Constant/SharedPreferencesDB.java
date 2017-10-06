package com.opentechlancer.appusagemetrics.Constant;

import android.content.Context;
import android.content.SharedPreferences;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class SharedPreferencesDB {
	private SharedPreferences dataStorage;
	private static SharedPreferencesDB instance;

	public static SharedPreferencesDB getInstance(Context context) {
		if (instance == null)
			instance = new SharedPreferencesDB(context);
		return instance;
	}

	public SharedPreferencesDB(Context context) {
		dataStorage = context.getSharedPreferences("pref",
				Context.MODE_PRIVATE);
	}

	public String getPreferenceValue(String key) {
		String value = "";
		try {
			value = dataStorage.getString(key, "");
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return value;
	}
	
	public String getPreferenceValue(String key, String defaultValue) {
		String value = "";
		try {
			value = dataStorage.getString(key, defaultValue);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return value;
	}

	public int getPreferenceIntValue(String key, int defValue) {
		int value = defValue;
		try {
			value = dataStorage.getInt(key, defValue);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return value;
	}

	public boolean getPreferenceBooleanValue(String key) {
		boolean value = false;
		try {
			value = dataStorage.getBoolean(key, false);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return value;
	}
	
	public boolean getPreferenceBooleanValue(String key, boolean isDefault) {
		boolean value = false;
		try {
			value = dataStorage.getBoolean(key, isDefault);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return value;
	}

	public void setPreferenceValue(String key, String value) {
		try {
			SharedPreferences.Editor editor = dataStorage.edit();
			editor.putString(key, value);
			editor.commit();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public void setPreferenceIntValue(String key, int value) {
		try {
			SharedPreferences.Editor editor = dataStorage.edit();
			editor.putInt(key, value);
			editor.commit();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public void setPreferenceBooleanValue(String key, boolean value) {
		try {
			SharedPreferences.Editor editor = dataStorage.edit();
			editor.putBoolean(key, value);
			editor.commit();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public void setPreferenceListValue(String key, List<String> keys) {
		try {
			Set<String> ips = new HashSet<>();
			ips.addAll(keys);

			SharedPreferences.Editor editor = dataStorage.edit();
			editor.putStringSet(key, ips);
			editor.apply();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public List<String> getPreferenceListValue(String key) {
		Set<String> ips = dataStorage.getStringSet(key, new HashSet<String>());
		List<String> ipList = new ArrayList<>();
		ipList.addAll(ips);

		return ipList;
	}

	public void addIpAddr(String add) {
		List<String> ips = getPreferenceListValue("ips");
		int i;

		for(i = 0; i < ips.size(); ++i) {
			String[] temp = ips.get(i).split(":");
			if(add.contains(temp[0])) {
				ips.add(i, add);
				break;
			}
		}

		if(ips.size() == i)
			ips.add(add);

		setPreferenceListValue("ips", ips);
	}
}