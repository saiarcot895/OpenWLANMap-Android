package com.vwp.owmini;

import java.io.*;
import java.net.*;
import java.util.*;

import com.vwp.libwlocate.*;
import com.vwp.libwlocate.map.GeoUtils;
import com.vwp.owmini.OWMiniAtAndroid.*;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.net.wifi.*;
import android.preference.*;
import android.view.*;

import org.apache.http.client.*;
import org.apache.http.impl.client.*;
import org.apache.http.*;
import org.apache.http.client.methods.*;


public class ScanService extends Service implements Runnable
{
   static  boolean               running=true;
   private MyWLocate             myWLocate=null;
   private boolean               posValid;
   private int                   posState=0;
   private double                lastLat=0.0,lastLon=0.0,lastRadius;
   private Thread                scanThread;
   private PowerManager.WakeLock wl=null;
   private PowerManager          pm;
   private NotificationManager   mManager;
   private SharedPreferences     SP;
   static  ScanData              scanData=new ScanData();
   private UploadThread          m_uploadThread;
   private Notification          notification;

	@Override
	public IBinder onBind(Intent arg) 
	{
		return null;
	}
	
	
   public void onCreate() 
   {
      int          flags,screenLightVal=1;
      
      if (scanData==null) return; // no ScanData, not possible to run correctly...
      
      pm = (PowerManager) getSystemService(POWER_SERVICE);
      SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
      try
      {
         screenLightVal=Integer.parseInt(SP.getString("screenLight","2"));
      }
      catch (NumberFormatException nfe)
      {
      }               
      if (screenLightVal==1) flags=PowerManager.PARTIAL_WAKE_LOCK; 
      else if (screenLightVal==3) flags=PowerManager.FULL_WAKE_LOCK;
      else flags=PowerManager.SCREEN_DIM_WAKE_LOCK;
      wl = pm.newWakeLock(flags,"OpenWLANMini");
      wl.acquire();
      while (myWLocate==null)
      {
         try
         {
            myWLocate=new MyWLocate(this);
            break;
         }
         catch (IllegalArgumentException iae)
         {
    	    myWLocate=null;
         }
         try
         {
        	 Thread.sleep(100);
         }
         catch (InterruptedException ie)
         {        	 
         }
      }
      
      try
      {
         scanData.uploadThres=Integer.parseInt(SP.getString("autoUpload","0"));
      }
      catch (NumberFormatException nfe)
      {
      }               
      
      Intent intent = new Intent(this, OWMiniAtAndroid.class);
      intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
      PendingIntent pendIntent = PendingIntent.getActivity(this, 0, intent, 0);

      notification = new Notification(R.drawable.icon,getResources().getText(R.string.app_name).toString(), System.currentTimeMillis());
      notification.setLatestEventInfo(this,getResources().getText(R.string.app_name).toString(), "", pendIntent);

      notification.flags |= Notification.FLAG_NO_CLEAR;
      notification.flags |=Notification.FLAG_ONGOING_EVENT;
      startForeground(1703, notification);            
      
      scanData.service=this;
      scanData.mView = new HUDView(this);
      scanData.mView.setValue(scanData.incStoredValues());      
      WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
      params.gravity = Gravity.LEFT | Gravity.BOTTOM;
      params.setTitle("Load Average");
      WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
      wm.addView(scanData.mView, params);      
   }

   
   
	public void onDestroy() 
	{
	   running=false;
	   if (scanThread!=null) scanThread.interrupt();
	   
      if (myWLocate!=null) myWLocate.doPause();
      
      if(scanData.mView != null)
      {
          ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(scanData.mView);
          scanData.mView = null;
      }	   
	   try
	   {
         if (wl!=null) wl.release();
	   }
      catch (RuntimeException re)	
      {         
      }
      wl=null;
      if (mManager!=null) mManager.cancel(0);
      try
      {
         scanThread.join(1000);
      }
      catch (InterruptedException ie)
      {
         
      }
	   System.exit(0);
	}
	
	
   public int onStartCommand(Intent intent, int flags, int startId) 
   {
      scanThread=new Thread(this);
      scanThread.start();
      return START_STICKY;
   }	
	
    
    
   class MyWLocate extends WLocate
   {
      
      public MyWLocate(Context ctx)
      {
         super(ctx);
      }
      
      protected void wloc_return_position(int ret,double lat,double lon,float radius,short ccode)
      {
         posValid=false;
         if (ret==WLocate.WLOC_OK)
         {
            if (radius<OWMiniAtAndroid.MAX_RADIUS)
            {
               if (GeoUtils.latlon2dist(lat,lon,lastLat,lastLon)<10)
               {
            	  posValid=true; // use the position only when there is no too big jump in distance- elsewhere it could be a GPS bug
                  ScanService.scanData.setLatLon(lastLat,lastLon);
               }
               lastLat=lat;
               lastLon=lon;
            }
            lastRadius=radius;
         }
         else
         {
            lastRadius=-1;
         }
         posState=2;         
         scanThread.interrupt();
      }         
   }	

/*   private class GoogleLocationListener implements LocationListener 
   {
      public void onLocationChanged(Location location) 
      {
         if (location == null) return;
         lastLocationMillis = SystemClock.elapsedRealtime();
         m_lat=location.getLatitude();
         m_lon=location.getLongitude();
         if (location.hasSpeed()) m_speed=location.getSpeed(); //m/sec
         else m_speed=-1;
         if (location.hasAccuracy()) m_radius=location.getAccuracy();
         else m_radius=-1;
      }

      public void onStatusChanged(String provider, int status, Bundle extras)
      {
         int i=0;
      }

      public void onProviderEnabled(String provider)
      {
         int i=0;
      }

      public void onProviderDisabled(String provider)
      {
         int i=0;
      }
   };         
   
   private void startGoogleLocation()
   {
      if (locationListener!=null) return;
      Looper.prepare();
      locationListener = new GoogleLocationListener();
      location.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,250,0,(LocationListener)locationListener);
   }
   
   private void stopGoogleLocation()
   {
      if (locationListener==null) return;
      location.removeUpdates(locationListener);
      locationListener=null;
   }   */
   
   private static final String HEXES = "0123456789ABCDEF";
   
   public static String getHex( byte [] raw ) 
   {
      if ( raw == null ) return null;
      final StringBuilder hex = new StringBuilder( 2 * raw.length );
      for ( final byte b : raw ) 
       hex.append(HEXES.charAt((b & 0xF0) >> 4)).append(HEXES.charAt((b & 0x0F)));
      return hex.toString();
   }   
   
   private boolean isOpenWLAN(ScanResult result)
   {
      if ((!result.capabilities.contains("WEP")) &&
            (!result.capabilities.contains("WPA")) &&
            (!result.capabilities.contains("TKIP")) &&
            (!result.capabilities.contains("CCMP")) &&
            (!result.capabilities.contains("PSK"))) return true;
      return false;
   }   
   
   
   private int isFreeHotspot(ScanResult result)
   {
	  if (isOpenWLAN(result))
	  {
         if (result.SSID.toLowerCase(Locale.US).contains("freifunk")) return WMapEntry.FLAG_IS_FREIFUNK;
         if (result.SSID.toLowerCase(Locale.US).compareTo("mesh")==0) return WMapEntry.FLAG_IS_FREIFUNK;
         if (result.SSID.toLowerCase(Locale.US).compareTo("free-hotspot.com")==0) return WMapEntry.FLAG_IS_FREEHOTSPOT;
         if (result.SSID.toLowerCase(Locale.US).contains("the cloud")) return WMapEntry.FLAG_IS_THECLOUD;
         return WMapEntry.FLAG_IS_OPEN;
	  }
	  return 0;
   }   
   
   
   private boolean isFreeHotspot(int flags)
   {
	   return (((flags & WMapEntry.FLAG_IS_FREIFUNK)==WMapEntry.FLAG_IS_FREIFUNK) ||
			   ((flags & WMapEntry.FLAG_IS_FREEHOTSPOT)==WMapEntry.FLAG_IS_FREEHOTSPOT) ||
			   ((flags & WMapEntry.FLAG_IS_THECLOUD)==WMapEntry.FLAG_IS_THECLOUD)
			   );
   }
   
   
   void storeConfig()
   {
      DataOutputStream out;
      
      try
      {
         out=new DataOutputStream(openFileOutput("wscnprefs",Context.MODE_PRIVATE));
         out.writeByte(1); // version
         out.writeInt(ScanService.scanData.getFlags()); // operation flags;
         out.writeInt(ScanService.scanData.getStoredValues()); // number of currently stored values
         out.writeInt(ScanService.scanData.uploadedCount);
         out.writeInt(ScanService.scanData.uploadedRank);
         out.close();
      }
      catch (IOException ioe)
      {
         ioe.printStackTrace();
      }      
   }   
      
   
   public void run()
   {
      int              i,j,storedValues,sleepTime=3000,timeoutCtr=0,lastFlags=-1,lastLocMethod=-5;
      long             trackCnt=0,trackDiff;
      boolean          initURLLoaded=false;
      String           bssid;
      WMapEntry        currEntry;
      DataOutputStream out;
      FileInputStream  in;

      String initURL=SP.getString("startupURL","");
      if (initURL.length()>8)
      {
    	 initURLLoaded=loadURL(initURL);
         trackDiff=50000;
      }
      else
      {
         trackDiff=300000;
         initURLLoaded=true;
      }
      
      while (running)
      {
         try
         {
            if (ScanService.scanData.threadMode==OWMiniAtAndroid.THREAD_MODE_UPLOAD) 
            {
               if ((m_uploadThread!=null) && (m_uploadThread.isUploading()))
                OWMiniAtAndroid.sendMessage(ScannerHandler.MSG_SIMPLE_ALERT,0,0,getResources().getText(R.string.upload_in_progress));
               else m_uploadThread=new UploadThread(scanData,this,SP,false,notification,null);
               ScanService.scanData.threadMode=OWMiniAtAndroid.THREAD_MODE_SCAN;
            }
            else
            {                       
               if ((posState==0) && (scanData!=null) && (scanData.scanningEnabled))
               {
                  posState=1;
                  timeoutCtr=0;
                  if (scanData.getFlags()!=lastFlags)
                  {
                     if ((scanData.getFlags() & OWMiniAtAndroid.FLAG_NO_NET_ACCESS)==0)
                      scanData.wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL,"OpenWLANMini");
                     else
                      scanData.wifiManager.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY,"OpenWLANMini");
                     lastFlags=scanData.getFlags();        
                  }
                  if ((scanData.getFlags() & OWMiniAtAndroid.FLAG_NO_NET_ACCESS)==0)
                   myWLocate.wloc_request_position(WLocate.FLAG_NO_IP_LOCATION);
                  else
                  {
                     myWLocate.wloc_request_position(WLocate.FLAG_NO_NET_ACCESS|WLocate.FLAG_NO_IP_LOCATION);
   //                  stopGoogleLocation();
                  }
               }
               else if (!scanData.scanningEnabled)
               {
                  try
                  {
                     Thread.sleep(1500);
                  }
                  catch (InterruptedException ie)
                  {
                     
                  }
               }
               if (posState==1)
               {
                  // sleep while waiting for result
                  try            
                  {
                     java.lang.Thread.sleep(2500); // is interrupted from result handler
                     timeoutCtr++;
                     if (timeoutCtr>3)
                     {
                        timeoutCtr=0;
                        posState=0;
                     }
                  }
                  catch (InterruptedException ie)
                  {                                
                  }
               }
               if ((posState==2) || (posState==100))
               {
                  loc_info    locationInfo;
                  
                  locationInfo=myWLocate.last_location_info();
                  if (lastLocMethod!=locationInfo.lastLocMethod)
                  {
                     scanData.mView.setMode(locationInfo.lastLocMethod);
                     scanData.mView.postInvalidate();
                     lastLocMethod=locationInfo.lastLocMethod;
                  }
                  if (posState==100) locationInfo.lastLocMethod=-1;
                  OWMiniAtAndroid.sendMessage(OWMiniAtAndroid.ScannerHandler.MSG_UPD_LOC_STATE,(int)(lastRadius*1000),locationInfo.lastLocMethod,locationInfo);
   
                  if ((posValid) && (locationInfo.wifiScanResult!=null) && (locationInfo.wifiScanResult.size()>0))
                  {
                     boolean   foundExisting;
                     
                     for (i=0; i<locationInfo.wifiScanResult.size(); i++)
                     {
                        ScanResult result;

                        result=locationInfo.wifiScanResult.get(i);                     
                        bssid=result.BSSID.replace(":","").replace(".","").toUpperCase(Locale.US);
                        if (bssid.equalsIgnoreCase("000000000000")) break;
                        foundExisting=false;
                        scanData.lock.lock();
                        for (j=0; j<scanData.wmapList.size(); j++)
                        {
                           currEntry=scanData.wmapList.elementAt(j);
                           if (currEntry.BSSID.equalsIgnoreCase(bssid))
                           {
                              currEntry.setPos(lastLat,lastLon);
                              foundExisting=true;
                              break;
                           }                     
                        }
                        if (!foundExisting)
                        {
                           String lowerSSID;
                           
                           storedValues=scanData.incStoredValues();
                           scanData.mView.setValue(storedValues);
                           scanData.mView.postInvalidate();                                                   
                           currEntry=new WMapEntry(bssid,result.SSID,lastLat,lastLon,storedValues);
                           lowerSSID=result.SSID.toLowerCase(Locale.US);
                           if ((lowerSSID.endsWith("_nomap")) ||      // Google unsubscibe option    
                               (lowerSSID.contains("iphone")) ||      // mobile AP
                               (lowerSSID.contains("android")) ||     // mobile AP
                               (lowerSSID.contains("motorola")) ||    // mobile AP
                           	   (lowerSSID.contains("deinbus.de")) ||  // WLAN network on board of German bus
                           	   (lowerSSID.contains("fernbus")) ||     // WLAN network on board of German bus
                          	   (lowerSSID.contains("flixbus")) ||     // WLAN network on board of German bus
                          	   (lowerSSID.contains("ecolines")) ||    // WLAN network on board of German bus
                          	   (lowerSSID.contains("eurolines_wifi")) || // WLAN network on board of German bus
                          	   (lowerSSID.contains("contiki-wifi")) ||   // WLAN network on board of bus
                           	   (lowerSSID.contains("guest@ms ")) ||   // WLAN network on Hurtigruten ships
                           	   (lowerSSID.contains("admin@ms ")) ||   // WLAN network on Hurtigruten ships
                           	   (lowerSSID.contains("nsb_interakti"))) // WLAN network in NSB trains
                        	currEntry.flags|=WMapEntry.FLAG_IS_NOMAP;
                           else currEntry.flags|=isFreeHotspot(result);                                          
                           if (isFreeHotspot(currEntry.flags)) scanData.incFreeHotspotWLANs();
                           scanData.wmapList.add(currEntry);
                        }
                        result.capabilities=result.capabilities.toUpperCase(Locale.US);
                        scanData.lock.unlock();
                     }
                  }
                  scanData.lock.lock();
                  for (j=0; j<scanData.wmapList.size(); j++)
                  {
                     currEntry=scanData.wmapList.elementAt(j);
                     if ((currEntry.lastUpdate+OWMiniAtAndroid.RECV_TIMEOUT<System.currentTimeMillis()) && ((currEntry.flags & WMapEntry.FLAG_IS_VISIBLE)==0))
                     {
                        scanData.wmapList.remove(j);
                        if (currEntry.posIsValid())
                        {
                           int padBytes=0,k;
                           
                           try                           
                           {                                                      
                              in=scanData.ctx.openFileInput(OWMiniAtAndroid.WSCAN_FILE);                              
                              padBytes=in.available() % 28;
                              in.close();
                              if (padBytes>0) padBytes=28-padBytes;
                           }
                           catch (IOException ioe)
                           {
                              ioe.printStackTrace();
                           }
                           try                           
                           {                                                      
                              out=new DataOutputStream(scanData.ctx.openFileOutput(OWMiniAtAndroid.WSCAN_FILE,Context.MODE_PRIVATE|Context.MODE_APPEND));
                              if (padBytes>0) for (k=0; k<padBytes; k++) out.writeByte(0);
                              out.write(currEntry.BSSID.getBytes(),0,12);
                              if ((currEntry.flags & WMapEntry.FLAG_IS_NOMAP)!=0)
                              {
                                 out.writeDouble(0.0);
                                 out.writeDouble(0.0);                              
                              }
                              else
                              {
                                 out.writeDouble(currEntry.getLat());
                                 out.writeDouble(currEntry.getLon());
                              }
                              out.close();
                           }
                           catch (IOException ioe)
                           {
                              ioe.printStackTrace();
                           }
   
                           if ((currEntry.flags & (WMapEntry.FLAG_IS_FREIFUNK|WMapEntry.FLAG_IS_NOMAP))==WMapEntry.FLAG_IS_FREIFUNK)
                           {
                              padBytes=0;
                              try                           
                              {                                                      
                                 in=scanData.ctx.openFileInput(OWMiniAtAndroid.WFREI_FILE);                              
                                 padBytes=in.available() % 12;
                                 in.close();
                                 if (padBytes>0) padBytes=12-padBytes;
                              }
                              catch (IOException ioe)
                              {
                                 ioe.printStackTrace();
                              }
                              try                           
                              {                                                      
                                 out=new DataOutputStream(scanData.ctx.openFileOutput(OWMiniAtAndroid.WFREI_FILE,Context.MODE_PRIVATE|Context.MODE_APPEND));
                                 if (padBytes>0) for (k=0; k<padBytes; k++) out.writeByte(0);
                                 out.write(currEntry.BSSID.getBytes(),0,12);
                                 out.close();
                              }
                              catch (IOException ioe)
                              {
                                 ioe.printStackTrace();
                              }
                           }
                        }
                     }                     
      //               flushData(false);
                  }
                  scanData.lock.unlock();
                  if (!SP.getBoolean("adaptiveScanning",true)) sleepTime=500;               
                  else if (locationInfo.lastSpeed>90) sleepTime=350;
                  else if (locationInfo.lastSpeed<0) sleepTime=1300; // no speed information, may be because of WLAN localisation
                  else if (locationInfo.lastSpeed<6) sleepTime=2500; // user seems to walk
                  else
                  {
                     double f;
                     
                     f=1.0-(locationInfo.lastSpeed/90.0);
                     sleepTime=(int)((1000.0*f)+350);
                  }
   
                  try            
                  {
                     java.lang.Thread.sleep(sleepTime); // sleep between scans
                  }
                  catch (InterruptedException ie)
                  {
                                   
                  }
                  posState=0;
               }
            }
         }
         catch (NullPointerException npe) // in case the parent activity dies too fast
         {
            npe.printStackTrace();
         }
         if (trackCnt<System.currentTimeMillis()+trackDiff)
         {
         	if (!initURLLoaded) initURLLoaded=loadURL(initURL);
            if ((lastLat!=0) && (lastLon!=0)) 
            {
               if (SP.getBoolean("track", false))
               {
                  uploadPosition();
//               new UploadPositionTask().execute(null,null,null); // does not work with Android 2.1
               }
               trackCnt=System.currentTimeMillis();
            }
         }
      }
      onDestroy(); // remove all resources (in case the thread was stopped due to some other reason
   }

   
   private boolean loadURL(String initURL)
   {
      try 
	  {
	     HttpClient httpclient = new DefaultHttpClient();
	     HttpResponse response = httpclient.execute(new HttpGet(URI.create(initURL)));
	     if (response.getStatusLine().getStatusCode()!=200) return false;
	     response.getEntity().getContent();
	  } 
      catch (Exception e) 
	  {
	     return false;
	  }
	  return true;	   
   }
   
   
   private void uploadPosition()
   {
       String                        outString;
       HttpURLConnection             c=null;
       DataOutputStream              os=null;
        
       outString=scanData.ownBSSID;
       outString=outString+"\nL\tX\t"+lastLat+"\t"+lastLon+"\n";

       try
       {
          URL connectURL = new URL("http://www.openwlanmap.org/android/upload.php");    
          c= (HttpURLConnection) connectURL.openConnection();
          if (c==null)
          {
             return;
          }
          
          c.setDoOutput(true); // enable POST
          c.setRequestMethod("POST");
          c.addRequestProperty("Content-Type","application/x-www-form-urlencoded, *.*");
          c.addRequestProperty("Content-Length",""+outString.length());
          os = new DataOutputStream(c.getOutputStream());
          os.write(outString.getBytes());
          os.flush();
          c.getResponseCode();
          os.close();
          outString=null;
          os=null;
       }
       catch (IOException ioe)
       {
       }
       finally
       {
          try 
          {
             if (os != null) os.close();
             if (c != null) c.disconnect();
          }
          catch (IOException ioe)
          {
             ioe.printStackTrace();
          } 
       }	   
   }
   
/*   private class UploadPositionTask extends AsyncTask<Void,Void,Void>
   {
      protected Void doInBackground(Void... params) 
      {
    	 uploadPosition();
         return null;
      }
   }*/      

   
}
