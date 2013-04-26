package com.vwp.owmap;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;

import com.vwp.libwlocate.*;
import com.vwp.owmap.OWMapAtAndroid.ScannerHandler;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.net.wifi.*;
import android.net.*;
import android.net.wifi.WifiConfiguration.*;
import android.preference.*;
import android.view.*;
import android.hardware.*;


public class ScanService extends Service implements Runnable, SensorEventListener
{
   static  boolean               running=true;
   private MyWLocate             myWLocate;
   private boolean               posValid;
   private int                   posState=0,saveCnt=1000000;
   private double                lastLat=0.0,lastLon=0.0,lastRadius;
   private Thread                scanThread;
   private PowerManager.WakeLock wl=null;
   private PowerManager          pm;
   private NotificationManager   mManager;
   private ConnectivityManager   connManager;
   private SharedPreferences     SP;
   static  ScanData              scanData=new ScanData();
   private SensorManager         sensorManager;
   private long                  lastTelemetryTime;
   private String                telemetryDir; 
   private float                 m_lastSpeed;
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
      Sensor       mSensor;
      List<Sensor> sensors;
      
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
      wl = pm.newWakeLock(flags,"OpenWLANMap");
      wl.acquire();
      myWLocate=new MyWLocate(this);
      
      try
      {
         scanData.uploadThres=Integer.parseInt(SP.getString("autoUpload","0"));
      }
      catch (NumberFormatException nfe)
      {
      }               
      
      Intent intent = new Intent(this, OWMapAtAndroid.class);
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
      
      sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
      
      sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),SensorManager.SENSOR_DELAY_GAME);
      sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),SensorManager.SENSOR_DELAY_GAME);
      sensors = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);
      mSensor = (Sensor) sensors.get(0);
      scanData.telemetryData.setAccelMax(mSensor.getMaximumRange());
      telemetryDir=Environment.getExternalStorageDirectory().getPath()+"/telemetry/";
      File dir=new File(telemetryDir);
      dir.mkdir();
      
      connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
   }

   
   
	public void onDestroy() 
	{
	   running=false;
	   if (scanThread!=null) scanThread.interrupt();
	   
      if (myWLocate!=null) myWLocate.doPause();
      
      sensorManager.unregisterListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
      
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
	
	
	private void saveData(TelemetryData data)
	{
	   PrintWriter out;
	   
	   if (saveCnt>25000)
	   {
	      File logFile;
	      int  i;
	      
	      logFile=new File(telemetryDir+99+".log");
	      logFile.delete();
	      
	      for (i=99; i>0; i--)
	      {
	         logFile=new File(telemetryDir+(i-1)+".log");
	         logFile.renameTo(new File(telemetryDir+i+".log"));
	      }
	      saveCnt=0;
	   }

	   try
	   {
	      String           text="";
	      SimpleDateFormat dateFormat=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.GERMAN);
	      
	      saveCnt++;
         out=new PrintWriter(new FileOutputStream(telemetryDir+"0.log",true));
         text=dateFormat.format(new Date(System.currentTimeMillis()))+"\t";
         if (posValid) text=text+lastLat+"\t"+lastLon+"\t"+m_lastSpeed+"\t";
         else text=text+"-\t-\t-\t";
         text=text+data.accelX+"\t"+data.accelY+"\t"+data.accelZ+"\t"+data.orientX+"\t"+data.orientY+"\t"+data.orientZ+"\n\n";         
         out.print(text);
         out.close();
	   }
	   catch (IOException ioe)
	   {
	   }
	}

	
	public void onSensorChanged(SensorEvent event) 
	{
	   synchronized (this) 
	   {
	      switch (event.sensor.getType())
	      {
	         case Sensor.TYPE_ACCELEROMETER:
	            scanData.telemetryData.addAccel(event.values[0],event.values[1],event.values[2]);
               break;	 
            case Sensor.TYPE_ORIENTATION:
               scanData.telemetryData.addOrient(event.values[0],event.values[1],event.values[2]);
               break;    
	      }
         if (lastTelemetryTime==0) lastTelemetryTime=System.currentTimeMillis();
         else if (System.currentTimeMillis()-lastTelemetryTime>750)
         {
            TelemetryData submitData=new TelemetryData();
   
            submitData.set(scanData.telemetryData);
            if (scanData.storeTele) saveData(submitData);
            scanData.telemetryData.reset();
            OWMapAtAndroid.sendMessage(OWMapAtAndroid.ScannerHandler.MSG_TELEMETRY,0,0,submitData);
            lastTelemetryTime=System.currentTimeMillis();
         }
	   }
   }	

	
	public void onAccuracyChanged(Sensor sensor, int accuracy) 
	{   
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
      
      protected void wloc_return_position(int ret,double lat,double lon,float radius,short ccode,float cog)
      {
         posValid=false;
         if (ret==WLocate.WLOC_OK)
         {
            if (radius<OWMapAtAndroid.MAX_RADIUS)
            {
//               stopGoogleLocation();
               posValid=true;
               lastLat=lat;
               lastLon=lon;
               ScanService.scanData.setLatLonCog(lat,lon,cog);
               posState=2;         
               scanThread.interrupt();
            }
/*            else if ((scanData.getFlags() & OWMapAtAndroid.FLAG_NO_NET_ACCESS)==0)
            {
               startGoogleLocation();               
               if (System.currentTimeMillis()-lastLocationMillis<1000)
               {
                  lastRadius=m_radius;
                  lastLat=m_lat;
                  lastLon=m_lon;
                  posState=100;
                  scanThread.interrupt();
               }
            }*/
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
   
   private boolean isFreifunkWLAN(ScanResult result)
   {
      if (!result.SSID.toLowerCase().contains("freifunk")) return false;
      return isOpenWLAN(result);
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
         out.writeInt(0); // Open WLANs, no longer used
         out.writeInt(ScanService.scanData.getFreifunkWLANs());
         out.writeFloat(ScanService.scanData.telemetryData.corrAccelX);
         out.writeFloat(ScanService.scanData.telemetryData.corrAccelY);
         out.writeFloat(ScanService.scanData.telemetryData.corrAccelZ);
         out.writeFloat(0);
         out.writeFloat(ScanService.scanData.telemetryData.corrOrientY);
         out.writeFloat(ScanService.scanData.telemetryData.corrOrientZ);
         out.close();
      }
      catch (IOException ioe)
      {
         ioe.printStackTrace();
      }      
   }   
      
   
   public void run()
   {
      int              i,j,storedValues,sleepTime=3000,timeoutCtr=0,lastFlags=-1,trackCnt=0,lastLocMethod=-5;
      String           bssid;
      WMapEntry        currEntry;
      DataOutputStream out;
      FileInputStream  in;
      
      while (running)
      {
         try
         {
            if (ScanService.scanData.threadMode==OWMapAtAndroid.THREAD_MODE_UPLOAD) 
            {
               if ((m_uploadThread!=null) && (m_uploadThread.isUploading()))
                OWMapAtAndroid.sendMessage(ScannerHandler.MSG_SIMPLE_ALERT,0,0,getResources().getText(R.string.upload_in_progress));
               else m_uploadThread=new UploadThread(scanData,this,SP,false,notification,null);
               ScanService.scanData.threadMode=OWMapAtAndroid.THREAD_MODE_SCAN;
            }
            else
            {                       
               if ((posState==0) && (scanData!=null) && (scanData.scanningEnabled))
               {
                  posState=1;
                  timeoutCtr=0;
                  if (scanData.getFlags()!=lastFlags)
                  {
                     if ((scanData.getFlags() & OWMapAtAndroid.FLAG_NO_NET_ACCESS)==0)
                      scanData.wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL,"OpenWLANMap");
                     else
                      scanData.wifiManager.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY,"OpenWLANMap");
                     lastFlags=scanData.getFlags();        
                  }
                  if ((scanData.getFlags() & OWMapAtAndroid.FLAG_NO_NET_ACCESS)==0)
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
                     trackCnt+=1500;
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
                     trackCnt+=2500;
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
                  loc_info    locationInfo; //bussi
                  NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                  
                  locationInfo=myWLocate.last_location_info();
                  if (lastLocMethod!=locationInfo.lastLocMethod)
                  {
                     scanData.mView.setMode(locationInfo.lastLocMethod);
                     scanData.mView.postInvalidate();
                     lastLocMethod=locationInfo.lastLocMethod;
                  }
                  if (posState==100) locationInfo.lastLocMethod=-1;
                  OWMapAtAndroid.sendMessage(OWMapAtAndroid.ScannerHandler.MSG_UPD_LOC_STATE,(int)(lastRadius*1000),locationInfo.lastLocMethod,locationInfo);
   
                  if (SP.getBoolean("autoConnect",false))
                  {
                     if (!mWifi.isConnected()) 
                     {
                        for (i=0; i<locationInfo.wifiScanResult.size(); i++)
                        {
                           ScanResult result;
                        
                           result=locationInfo.wifiScanResult.get(i);
                           result.capabilities=result.capabilities.toUpperCase();
                           if (isFreifunkWLAN(result))
                           {
                              // auto-connect to this open network
                            
                              WifiConfiguration wifiConfig = new WifiConfiguration(); 
                              wifiConfig.BSSID =result.BSSID; 
                              wifiConfig.priority = 1; 
                              wifiConfig.allowedKeyManagement.set(KeyMgmt.NONE); 
                              wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP); 
                              wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN); 
                              wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE); 
                              wifiConfig.status=WifiConfiguration.Status.ENABLED; 
      
                              int netId = scanData.wifiManager.addNetwork(wifiConfig); 
                              scanData.wifiManager.enableNetwork(netId, true);
                           }
                        }
                     }
                  }
                  if ((posValid) && (locationInfo.wifiScanResult!=null) && (locationInfo.wifiScanResult.size()>0))
                  {
                     boolean   foundExisting;
                     
                     for (i=0; i<locationInfo.wifiScanResult.size(); i++)
                     {
                        ScanResult result;

                        result=locationInfo.wifiScanResult.get(i);                     
                        bssid=result.BSSID.replace(":","").replace(".","").toUpperCase();
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
                           storedValues=scanData.incStoredValues();
                           scanData.mView.setValue(storedValues);
                           scanData.mView.postInvalidate();                                                   
                           currEntry=new WMapEntry(bssid,result.SSID,lastLat,lastLon,storedValues);
                           if (result.SSID.endsWith("_nomap")) currEntry.flags|=WMapEntry.FLAG_IS_NOMAP;
                           else if (isFreifunkWLAN(result)) currEntry.flags|=(WMapEntry.FLAG_IS_FREIFUNK|WMapEntry.FLAG_IS_OPEN);                                          
                           else if (isOpenWLAN(result)) currEntry.flags|=WMapEntry.FLAG_IS_OPEN;                                          
                           if ((currEntry.flags & WMapEntry.FLAG_IS_FREIFUNK)!=0) scanData.incFreifunkWLANs();
                           scanData.wmapList.add(currEntry);
                           if ((scanData.uploadThres>0) && (storedValues>scanData.uploadThres))
                           {
                              if ((m_uploadThread==null) || (!m_uploadThread.isUploading()))
                              {
                                 if (mWifi.isConnected()) 
                                 {                         
                                    m_uploadThread=new UploadThread(scanData,this,SP,true,notification,mWifi);
                                 }
                              }                              
                           }                           
                        }
                        result.capabilities=result.capabilities.toUpperCase();
                        scanData.lock.unlock();
                     }
                  }
                  scanData.lock.lock();
                  for (j=0; j<scanData.wmapList.size(); j++)
                  {
                     currEntry=scanData.wmapList.elementAt(j);
                     if ((currEntry.lastUpdate+OWMapAtAndroid.RECV_TIMEOUT<System.currentTimeMillis()) && ((currEntry.flags & WMapEntry.FLAG_IS_VISIBLE)==0))
                     {
                        scanData.wmapList.remove(j);
                        if (currEntry.posIsValid())
                        {
                           int padBytes=0,k; //  huggeidimörps
                           
                           try                           
                           {                                                      
                              in=scanData.ctx.openFileInput(OWMapAtAndroid.WSCAN_FILE);                              
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
                              out=new DataOutputStream(scanData.ctx.openFileOutput(OWMapAtAndroid.WSCAN_FILE,Context.MODE_PRIVATE|Context.MODE_APPEND));
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
                                 in=scanData.ctx.openFileInput(OWMapAtAndroid.WFREI_FILE);                              
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
                                 out=new DataOutputStream(scanData.ctx.openFileOutput(OWMapAtAndroid.WFREI_FILE,Context.MODE_PRIVATE|Context.MODE_APPEND));
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
                  m_lastSpeed=locationInfo.lastSpeed;
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
                     trackCnt+=sleepTime;
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
         if ((trackCnt>500000) && (lastLat!=0) && (lastLon!=0)) 
         {
            if (SP.getBoolean("track", false))
             uploadPosition();
            trackCnt=0;
         }
      }
      onDestroy(); // remove all resources (in case the thread was stopped due to some other reason
   }

   protected synchronized void uploadPosition()
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
      
}