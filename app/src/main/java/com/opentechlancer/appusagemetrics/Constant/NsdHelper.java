package com.opentechlancer.appusagemetrics.Constant;

/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context;
import android.content.Intent;
import android.net.nsd.NsdServiceInfo;
import android.net.nsd.NsdManager;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.opentechlancer.appusagemetrics.common.App;

import java.io.IOException;

import static android.provider.Settings.Secure.ANDROID_ID;

public class NsdHelper {

    public static final String BROADCAST_TAG = "NSDBroadcast";
    public static final String KEY_SERVICE_INFO = "serviceinfo";

    Context mContext;

    private LocalBroadcastManager broadcaster;

    NsdManager mNsdManager;
    NsdManager.ResolveListener mResolveListener;
    NsdManager.DiscoveryListener mDiscoveryListener;
    NsdManager.RegistrationListener mRegistrationListener;

    public static final String SERVICE_TYPE = "_master._tcp";

    // There is an additional dot at the end of service name most probably by os, this is to
    // rectify that problem
    public static final String SERVICE_TYPE_PLUS_DOT = SERVICE_TYPE + ".";

    public static final String TAG = "NSDHelperAppUsage: ";

    private String DEFAULT_SERVICE_NAME = "Masr_" + Settings.Secure.
            getString(App.ctx.getContentResolver(),
            ANDROID_ID).substring(0, 3);

    public String mServiceName = DEFAULT_SERVICE_NAME;

    NsdServiceInfo mService;

    public NsdHelper(Context context) {
        mContext = context;
        mNsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        broadcaster = LocalBroadcastManager.getInstance(mContext);
    }

    public void initializeNsd() {
        initializeResolveListener();
        discoverServices();
        //mNsdManager.init(mContext.getMainLooper(), this);
    }

    public void initializeDiscoveryListener() {
        mDiscoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String regType) {
                Log.e(TAG, "Service discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                Log.e(TAG, "Service discovery success" + service);
                String serviceType = service.getServiceType();
                // For some reason the service type received has an extra dot with it, hence
                // handling that case
                boolean isOurService = serviceType.equals(SERVICE_TYPE) || serviceType.equals
                        (SERVICE_TYPE_PLUS_DOT);
                Log.e(TAG, "Service discovery success: " + service.getServiceName());

                if (!isOurService) {
                    Log.e(TAG, "Unknown Service Type: " + service.getServiceType());
                } else if (service.getServiceName().equals(mServiceName)) {
                    Log.e(TAG, "Same machine: " + mServiceName);
                } else if (service.getServiceName().contains(mServiceName.
                        split("_")[0])) {
                    Log.e(TAG, "different machines. (" + service.getServiceName() + "-" +
                            mServiceName + ")");
                    mNsdManager.resolveService(service, mResolveListener);
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                Log.e(TAG, "service lost" + service.getServiceName());
                if (mService == service) {
                    mService = null;
                }
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.e(TAG, "Discovery stopped: " + serviceType);
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
            }
        };
    }

    public void initializeResolveListener() {
        mResolveListener = new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Resolve failed" + errorCode);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.e(TAG, "Resolve Succeeded. " + serviceInfo);
                if (serviceInfo.getServiceName().equals(mServiceName)) {
                    Log.e(TAG, "Same IP.");
                    return;
                }
                mService = serviceInfo;
                String masterUrl = String.format("http://%s:%s", serviceInfo.
                        getHost().getHostAddress(), serviceInfo.getPort());
                Log.e("masterur", masterUrl);

                SharedPreferencesDB.getInstance(mContext).addIpAddr(masterUrl);
            }
        };
    }

    public void initializeRegistrationListener() {
        mRegistrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
                mServiceName = NsdServiceInfo.getServiceName();
                Log.e(TAG, "Service registered: " + NsdServiceInfo);
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo arg0, int arg1) {
                Log.e(TAG, "Service registration failed: " + arg1);
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo arg0) {
                Log.e(TAG, "Service unregistered: " + arg0.getServiceName());
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Service unregistration failed: " + errorCode);
            }
        };
    }

    public void registerService(int port) throws IOException {
        tearDown();  // Cancel any previous registration request
        initializeRegistrationListener();

        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setPort(port);
        serviceInfo.setServiceName(mServiceName);
        serviceInfo.setServiceType(SERVICE_TYPE);

        Log.e(TAG, Build.MANUFACTURER + " registering service: " + port);
        mNsdManager.registerService(
                serviceInfo, NsdManager.PROTOCOL_DNS_SD, mRegistrationListener);
    }

    public void discoverServices() {
        stopDiscovery();  // Cancel any existing discovery request
        initializeDiscoveryListener();
        mNsdManager.discoverServices(
                SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    public void stopDiscovery() {
        if (mDiscoveryListener != null) {
            try {
                mNsdManager.stopServiceDiscovery(mDiscoveryListener);
            } finally {
            }
            mDiscoveryListener = null;
        }
    }

    public NsdServiceInfo getChosenServiceInfo() {
        return mService;
    }

    public void tearDown() {
        if (mRegistrationListener != null) {
            try {
                mNsdManager.unregisterService(mRegistrationListener);
            } finally {
            }
            mRegistrationListener = null;
        }
    }
}