package com.vwp.owmap;

import java.util.*;
import java.util.concurrent.locks.*;

import android.content.*;
import android.location.*;
import android.net.wifi.*;
import android.widget.TextView;

public class ScanData 
{
           Lock              lock=new ReentrantLock();
           Vector<WMapEntry> wmapList=new Vector<WMapEntry>();
           OWMapAtAndroid    ctx;
   private int               flags=OWMapAtAndroid.FLAG_NO_NET_ACCESS,storedValues;
   private int               freifunkWLANs=0;
           boolean           isActive=true,scanningEnabled=true,hudCounter=false,appVisible=false,storeTele=false;
           int               viewMode=OWMapAtAndroid.VIEW_MODE_MAIN,threadMode=OWMapAtAndroid.THREAD_MODE_SCAN,
                             uploadedCount=0,uploadedRank=0,uploadThres=0;
           WifiManager       wifiManager;
   private double            lat,lon;
   private float             cog;
           TextView          bigCntTextHud;
           String            ownBSSID;
           HUDView           mView;
           Thread            watchThread=null;
           TelemetryData     telemetryData=new TelemetryData();
           ScanService       service=null;

   
   ScanData()
   {      
   }
   
   void init(OWMapAtAndroid ctx)
   {
      this.ctx=ctx;
      wifiManager= (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);      
      wifiManager.setWifiEnabled(true);
      
      LocationManager location= (LocationManager)ctx.getSystemService(Context.LOCATION_SERVICE);
      if(!location.isProviderEnabled(LocationManager.GPS_PROVIDER ))
       ctx.simpleAlert(ctx.getResources().getString(R.string.gpsdisabled_warn),null,OWMapAtAndroid.ALERT_GPSWARN);
   }
   
   
   
   void setFlags(int flags)
   {
	   lock.lock();
	   this.flags=flags;
	   lock.unlock();
	   
      if ((flags & OWMapAtAndroid.FLAG_NO_NET_ACCESS)!=0)
	    wifiManager.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY,"OpenWLANMap");
      else
       wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL,"OpenWLANMap");
   }

   
   void setLatLonCog(double lat,double lon,float cog)
   {
      lock.lock();
      this.lat=lat;
      this.lon=lon;
      this.cog=cog;
      lock.unlock();
   }
   
   
   
   double getLat()
   {
      double d;
      
      lock.lock();
      d=lat;
      lock.unlock();
      return d;
   }

   
   double getLon()
   {
      double d;
      
      lock.lock();
      d=lon;
      lock.unlock();
      return d;
   }

   
   float getCog()
   {
      float f;
      
      lock.lock();
      f=cog;
      lock.unlock();
      return f;
   }

      
   int getFlags()
   {
	   int val;
	   
	   lock.lock();
	   val=flags;
	   lock.unlock();
	   return val;
   }

   void setStoredValues(int storedValues)
   {
	   this.storedValues=storedValues;
   }

   int incStoredValues()
   {
	   storedValues++;

	   OWMapAtAndroid.sendMessage(OWMapAtAndroid.ScannerHandler.MSG_UPD_AP_COUNT,storedValues,freifunkWLANs,null);
	   return storedValues;
   }

   
   
   int getStoredValues()
   {
      return storedValues;
   }

      
   
   void setFreifunkWLANs(int freifunkWLANs)
   {
      this.freifunkWLANs=freifunkWLANs;
   }

   int incFreifunkWLANs()
   {
      freifunkWLANs++;
      return freifunkWLANs;
   }

   
   int getFreifunkWLANs()
   {
      return freifunkWLANs;
   }


}