package com.vwp.owmini;

import java.util.*;
import java.util.concurrent.locks.*;

import android.content.*;
import android.location.*;
import android.net.wifi.*;

public class ScanData {
    private Lock lock = new ReentrantLock();
    private Vector<WMapEntry> wmapList = new Vector<WMapEntry>();
    private OWMiniAtAndroid ctx;
    private int flags = OWMiniAtAndroid.FLAG_NO_NET_ACCESS, storedValues;
    private int freeHotspotWLANs = 0;
    private boolean isActive = true;
    private boolean scanningEnabled = true;
    private boolean hudCounter = false;
    private boolean appVisible = false;
    private int viewMode = OWMiniAtAndroid.VIEW_MODE_MAIN;
    private int threadMode = OWMiniAtAndroid.THREAD_MODE_SCAN;
    private int uploadedCount = 0;
    private int uploadedRank = 0;
    private int uploadThres = 0;
    private WifiManager wifiManager;
    private double lat, lon;
    private String ownBSSID;
    private HUDView mView;
    private Thread watchThread = null;
    private ScanService service = null;


    void init(OWMiniAtAndroid ctx) {
        this.ctx = ctx;
        wifiManager = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(true);

        LocationManager location = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        if (!location.isProviderEnabled(LocationManager.GPS_PROVIDER))
            ctx.simpleAlert(ctx.getResources().getString(R.string.gpsdisabled_warn), null, OWMiniAtAndroid.ALERT_GPSWARN);
    }


    void setFlags(int flags) {
        lock.lock();
        this.flags = flags;
        lock.unlock();

        if ((flags & OWMiniAtAndroid.FLAG_NO_NET_ACCESS) != 0)
            wifiManager.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY, "OpenWLANMapMini");
        else
            wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "OpenWLANMapMini");
    }


    void setLatLon(double lat, double lon) {
        lock.lock();
        this.lat = lat;
        this.lon = lon;
        lock.unlock();
    }


    double getLat() {
        double d;

        lock.lock();
        d = lat;
        lock.unlock();
        return d;
    }


    double getLon() {
        double d;

        lock.lock();
        d = lon;
        lock.unlock();
        return d;
    }


    int getFlags() {
        int val;

        lock.lock();
        val = flags;
        lock.unlock();
        return val;
    }

    void setStoredValues(int storedValues) {
        this.storedValues = storedValues;
    }

    int incStoredValues() {
        storedValues++;

        OWMiniAtAndroid.sendMessage(OWMiniAtAndroid.ScannerHandler.MSG_UPD_AP_COUNT, storedValues, 0, null);
        return storedValues;
    }


    int getStoredValues() {
        return storedValues;
    }


    void setFreeHotspotWLANs(int freeHotspotWLANs) {
        this.freeHotspotWLANs = freeHotspotWLANs;
    }

    int incFreeHotspotWLANs() {
        freeHotspotWLANs++;
        return freeHotspotWLANs;
    }


    int getFreeHotspotWLANs() {
        return freeHotspotWLANs;
    }


    public Lock getLock() {
        return lock;
    }

    public Vector<WMapEntry> getWmapList() {
        return wmapList;
    }

    public OWMiniAtAndroid getCtx() {
        return ctx;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    public boolean isScanningEnabled() {
        return scanningEnabled;
    }

    public void setScanningEnabled(boolean scanningEnabled) {
        this.scanningEnabled = scanningEnabled;
    }

    public boolean isHudCounter() {
        return hudCounter;
    }

    public void setHudCounter(boolean hudCounter) {
        this.hudCounter = hudCounter;
    }

    public boolean isAppVisible() {
        return appVisible;
    }

    public void setAppVisible(boolean appVisible) {
        this.appVisible = appVisible;
    }

    public int getViewMode() {
        return viewMode;
    }

    public void setViewMode(int viewMode) {
        this.viewMode = viewMode;
    }

    public int getThreadMode() {
        return threadMode;
    }

    public void setThreadMode(int threadMode) {
        this.threadMode = threadMode;
    }

    public int getUploadedCount() {
        return uploadedCount;
    }

    public void setUploadedCount(int uploadedCount) {
        this.uploadedCount = uploadedCount;
    }

    public int getUploadedRank() {
        return uploadedRank;
    }

    public void setUploadedRank(int uploadedRank) {
        this.uploadedRank = uploadedRank;
    }

    public int getUploadThres() {
        return uploadThres;
    }

    public void setUploadThres(int uploadThres) {
        this.uploadThres = uploadThres;
    }

    public WifiManager getWifiManager() {
        return wifiManager;
    }

    public String getOwnBSSID() {
        return ownBSSID;
    }

    public void setOwnBSSID(String ownBSSID) {
        this.ownBSSID = ownBSSID;
    }

    public HUDView getmView() {
        return mView;
    }

    public void setmView(HUDView mView) {
        this.mView = mView;
    }

    public Thread getWatchThread() {
        return watchThread;
    }

    public void setWatchThread(Thread watchThread) {
        this.watchThread = watchThread;
    }

    public ScanService getService() {
        return service;
    }

    public void setService(ScanService service) {
        this.service = service;
    }
}
