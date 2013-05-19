package com.vwp.owmap;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.app.*; 
import android.content.*;
import android.net.Uri;
import android.net.wifi.*;
import android.os.*;
import android.view.*;
import android.view.View.*;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import android.preference.*;
import android.provider.Settings;

import com.vwp.libwlocate.*;
import com.vwp.libwlocate.map.*;


public class OWMapAtAndroid extends Activity implements OnClickListener, OnItemClickListener, Runnable //implements SensorEventListener
{
   public static final int MAX_RADIUS=90;
   public static final int RECV_TIMEOUT=75000;
   
   public static final int FLAG_NO_NET_ACCESS=0x0001;
   
   static final int VIEW_MODE_MAIN=1;
   static final int VIEW_MODE_MAP=2;
   static final int VIEW_MODE_FF_LIST=3;
   
   static final int THREAD_MODE_SCAN  =1;
   static final int THREAD_MODE_UPLOAD=2;
   static final int THREAD_MODE_MAP   =3;
	   
   public static final String MAP_FILE     ="lastmap.png";
   public static final String WSCAN_FILE   ="wscndata";
   public static final String WFREI_FILE   ="wfreidata";   
   public static final String MAP_DATA_FILE="mapdata.png";
   public static final String MAP_MAX_FILE ="maxdata.png";
      
   public static final int ALERT_OK      =0x0001;
   public static final int ALERT_NO_EXIT =0x0002;
   public static final int ALERT_SHOW_MAP=0x0003;
   public static final int ALERT_GPSWARN =0x0004;
   
//   private SensorManager          mSensorManager=null;
//   private Sensor                 mAccelerometer=null;
//   private EditText               accXField,accYField,accZField,accuracyField;
           CheckBox              noNetAccCB;
   private OWMapAtAndroid        ctx;
   static  boolean               showMap=false,showTele=false,doTrack=true,hasPosLock=false;
   static  byte                  showSLimit=0;
   private TextView              rankText;
   private TableRow              mapTableRow;
           ScannerHandler        scannerHandler=null;
   private PowerManager          pm=null;
   private PowerManager.WakeLock wl=null;
   private Vector<WMapSlimEntry> freifunkList;
   private ListView              ffLv;

   private static int            textSizeVal=1;
   
   void simpleAlert(String text,String title,int mode)
   {
      AlertDialog ad=null;
      
      try
      {
         ad = new AlertDialog.Builder(ctx).create();
         if (mode!=ALERT_OK)
         {
            ad.setCancelable(false);  
            ad.setCanceledOnTouchOutside(false);
         }
         if (text!=null) ad.setMessage(text);
         else ad.setMessage("missing ressource!");
         if (title!=null) ad.setTitle(title);
         if (mode==ALERT_OK)
         {
            ad.setButton(ctx.getResources().getText(R.string.ok), new DialogInterface.OnClickListener() {  
                @Override  
                public void onClick(DialogInterface dialog, int which) {  
                    dialog.dismiss();
                }  
            });  
         }
         else if (mode==ALERT_GPSWARN)
         {
            ad.setButton(ctx.getResources().getText(R.string.ok), new DialogInterface.OnClickListener() {  
                @Override  
                public void onClick(DialogInterface dialog, int which) 
                {  
                    dialog.dismiss();
                    Intent myIntent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                    ctx.startActivity(myIntent);
                }  
            });  
         }
         else if (mode==ALERT_NO_EXIT)
         {
            ad.setButton(ctx.getResources().getText(R.string.exit), new DialogInterface.OnClickListener() {  
                @Override  
                public void onClick(DialogInterface dialog, int which) 
                {                   
                   ScanService.running=false;
                   stopService(new Intent(ctx,ScanService.class));
                   dialog.dismiss();
                   finish();
   //                 System.exit(0);
                }  
            });  
            ad.setButton2(ctx.getResources().getText(R.string.no), new DialogInterface.OnClickListener() {  
                @Override  
                public void onClick(DialogInterface dialog, int which) {  
                    dialog.dismiss();
                }  
            });  
         }
         else if (mode==ALERT_SHOW_MAP)
         {
            ad.setButton(ctx.getResources().getText(R.string.yes), new DialogInterface.OnClickListener() {  
                @Override  
                public void onClick(DialogInterface dialog, int which) 
                {  
                   OWMapAtAndroid.sendMessage(OWMapAtAndroid.ScannerHandler.MSG_OPEN_PRG_DLG,0,0,ctx.getResources().getText(R.string.loading_map).toString());                                   
                   scannerHandler.sendEmptyMessage(ScannerHandler.MSG_SHOW_MAP);
                   dialog.dismiss();
                }  
            });  
            ad.setButton2(ctx.getResources().getText(R.string.no), new DialogInterface.OnClickListener() {  
                @Override  
                public void onClick(DialogInterface dialog, int which) {  
                 dialog.dismiss();
                }  
            });  
         }
         ad.show();
      }
      catch (Exception e) // to avoid strange exceptions when app is in background
      {
         if (ad!=null) ad.dismiss();
      }
   }

   
   
   static class ScannerHandler extends Handler
   {
      public static final int MSG_ADD_ENTRY=1;
      public static final int MSG_REM_ENTRY=2;
      public static final int MSG_UPD_POS=3;
      public static final int MSG_OPEN_PRG_DLG=4;
      public static final int MSG_CLOSE_PRG_DLG=5;
      public static final int MSG_GET_FREIFUNK_POS_DL2=6;
      public static final int MSG_SHOW_MAP2=7;
      public static final int MSG_SHOW_MAP=8;
      public static final int MSG_DL_FAILURE=9;
      public static final int MSG_SIMPLE_ALERT=10;
      public static final int MSG_UPD_LOC_STATE=11;
      public static final int MSG_UPD_AP_COUNT=12;
      public static final int MSG_UPD_LIVE_MAP=14;
//      public static final int MSG_GET_FREIFUNK_POS=15;
      public static final int MSG_TELEMETRY=16;
      public static final int MSG_TOAST=17;
      public static final int MSG_GET_FREIFUNK_POS_DL=18;
      
      private Lock lock=new ReentrantLock();

      TableLayout    parentTable,mapTable;
      TextView       latTableText,lonTableText,locStateText,bigCntText,apCountText,bigOpenCntText;
      LiveMapView    liveMapView;
      ProgressDialog progDlg;
      MapView        mapView;
      boolean        bigCounter=false,lastShowMap=true,lastShowTele=true;//!showMap;
      OWMapAtAndroid       owmp;
      private TotalMap     mapOverlay;
              FrameLayout  rootLayout;    
              
            
      private void dbgRemEntry(Message msg)
      {
         WMapEntry entry; // lh54uvd

         lock.lock();
         entry=(WMapEntry)msg.obj;
         parentTable.removeView(entry.row);
         entry.flags&=~WMapEntry.FLAG_IS_VISIBLE;
         lock.unlock();         
      }
      
      private void dbgUpdLocState(Message msg)
      {
         if (msg.arg1<0) locStateText.setText(ScanService.scanData.ctx.getResources().getText(R.string.waitGPS)+" ");
         else
         {
            String   locText="";
            loc_info locationInfo;
            
            if ((msg.arg1>OWMapAtAndroid.MAX_RADIUS*1000) || (msg.arg2==loc_info.LOC_METHOD_NONE))
            {
               locText=ScanService.scanData.ctx.getResources().getText(R.string.waitGPS)+" ";
               hasPosLock=false;
            }
            else hasPosLock=true;
            locationInfo=(loc_info)msg.obj;
            if (locationInfo.lastLocMethod==loc_info.LOC_METHOD_GPS)
            {
               locText="GPS";
               if (bigCounter) bigCntText.setTextColor(0xFFD9D9FF);
            }
            else if (locationInfo.lastLocMethod==loc_info.LOC_METHOD_LIBWLOCATE)
            {
               locText="WLAN";
               if (bigCounter) bigCntText.setTextColor(0xFFFFFF64);
            }
            else if (locationInfo.lastLocMethod==-1)
            {
               locText="Mix";
               if (bigCounter) bigCntText.setTextColor(0xFFFF6464);
            }
            if ((msg.arg1>0) && (msg.arg2!=loc_info.LOC_METHOD_NONE)) locText=locText+" +/-"+(msg.arg1/1000.0)+" m";
            locStateText.setText(locText);
            if (bigCounter) bigCntText.invalidate();
         }         
      }

      
      
      private void dbgUpdApCount(Message msg)
      {
         apCountText.setText(ScanService.scanData.ctx.getResources().getText(R.string.ap_count).toString()+": "+msg.arg1); //pj   q6uikcde
         if (bigCounter)
         {
            bigCntText.setText(""+msg.arg1);
            bigOpenCntText.setText(""+msg.arg2);
         }
         else
         {
            bigCntText.setText("");
            bigOpenCntText.setText("");                  
         }         
         if (ScanService.scanData.bigCntTextHud!=null)
          ScanService.scanData.bigCntTextHud.setText(""+msg.arg1);
      }

      
      private void openPrgDlg(Message msg)
      {
         if (progDlg!=null) return;
         progDlg=new ProgressDialog(owmp);
         if (msg.arg1>0)
         {
            progDlg.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progDlg.setMax(msg.arg1);
         }
         progDlg.setTitle((String)msg.obj);
         progDlg.setCanceledOnTouchOutside(false);
         progDlg.setCancelable(false);
         progDlg.show();             
      }

      
      private class DownloadMapDataTask extends AsyncTask<Void,Void,Void>
      {
         protected Void doInBackground(Void... params) 
         {
            mapOverlay=new TotalMap(owmp,ScanService.scanData.ownBSSID);
            owmp.scannerHandler.sendEmptyMessage(ScannerHandler.MSG_SHOW_MAP2);            
        	return null;
         }
      }      

      private class DownloadFreifunkDataTask extends AsyncTask<Void,Void,Void>
      {
         protected Void doInBackground(Void... params) 
         {
       	    String            outString;
        	HttpURLConnection c=null;
        	DataOutputStream  os=null;
        	DataInputStream   is=null;      
        	      
        	outString=ScanService.scanData.getLat()+"\n"+ScanService.scanData.getLon()+"\n";
        	try
        	{
        	   URL connectURL = new URL("http://www.openwlanmap.org/android/freifunk.php");    
        	   c= (HttpURLConnection) connectURL.openConnection();
        	   if (c==null)
        	   {
        	      owmp.scannerHandler.sendEmptyMessage(OWMapAtAndroid.ScannerHandler.MSG_CLOSE_PRG_DLG);                            
                  return null;
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
	           is=new DataInputStream(c.getInputStream());
               outString=is.readLine();
	           owmp.freifunkList=new Vector<WMapSlimEntry>();
	           if (outString.equalsIgnoreCase("0"))
	           {                            
	              try
	              {
	                 while (is.available()>0)
	                 {
	                    WMapSlimEntry entry=new WMapSlimEntry(is.readLine(),is.readLine());
                        owmp.freifunkList.add(entry);
	                 }
	              }
	              catch (NumberFormatException nfe)
	              {
	               
	              }
               }
	           c.disconnect();
	           c=null;
	        }
            catch (IOException ioe)
	        {
	        }
	        finally
            {
	           try 
	           {
	              if (os != null) os.close();
	              if (is != null) is.close();
	              if (c != null) c.disconnect();
               }
	           catch (IOException ioe)
	           {
	              ioe.printStackTrace();
	           } 
            }      
  	        owmp.scannerHandler.sendEmptyMessage(OWMapAtAndroid.ScannerHandler.MSG_GET_FREIFUNK_POS_DL2);
  	        return null;
         }
      }

      
      public void handleMessage(Message msg) 
      {
         
         super.handleMessage(msg);
      
         if (!ScanService.scanData.isActive) return;
         switch (msg.what)
         {
            case MSG_TOAST:
               Toast.makeText(owmp,(String)msg.obj,Toast.LENGTH_SHORT).show();
               break;
            case MSG_TELEMETRY:
               if (showTele)
               {
                  liveMapView.setTelemetry((TelemetryData)msg.obj);
                  liveMapView.invalidate();
               }
               break;
            case MSG_GET_FREIFUNK_POS_DL:
            {
               msg.obj=owmp.getResources().getText(R.string.loading_data).toString();
               openPrgDlg(msg);
               new DownloadFreifunkDataTask().execute(null,null,null);
               break;
            }
            case MSG_GET_FREIFUNK_POS_DL2:
            {
      	       if ((owmp.freifunkList!=null) && (owmp.freifunkList.size()>0))
    	       {
    	          owmp.ffLv = new ListView(owmp);
                  ArrayAdapter<String> adapter = new ArrayAdapter<String>(owmp,R.layout.listviewitem,R.id.listViewItemText);
    	          for (int i=0; i<owmp.freifunkList.size(); i++)
    	          {
                     WMapSlimEntry entry=owmp.freifunkList.elementAt(i);
    	            
    	             String text=""+GeoUtils.latlon2dist(ScanService.scanData.getLat(),ScanService.scanData.getLon(),entry.lat,entry.lon);
    	             text=text.substring(0,8);
    	             text=text+" km";
    	             adapter.add(text);
    	          }
    	          owmp.ffLv.setAdapter(adapter);
    	          owmp.ffLv.setOnItemClickListener(owmp);
    	          owmp.scannerHandler.rootLayout.addView(owmp.ffLv);
    	          ScanService.scanData.viewMode=VIEW_MODE_FF_LIST;
    	       }
               OWMapAtAndroid.sendMessage(OWMapAtAndroid.ScannerHandler.MSG_CLOSE_PRG_DLG,0,0,null);                                    	           	 
               break;
            }
            case MSG_UPD_LIVE_MAP:
               if (showMap)
               {
                  liveMapView.updateViewTiles(ScanService.scanData.getLat(),ScanService.scanData.getLon());
                  if (!showTele) liveMapView.invalidate();
               }
               break;
            case MSG_ADD_ENTRY:
            {
               WMapEntry entry;

               lock.lock();
               entry=(WMapEntry)msg.obj;
               if ((entry.flags & WMapEntry.FLAG_UI_USED)==0) entry.createUIData(owmp);
               if ((entry.flags & WMapEntry.FLAG_IS_VISIBLE)==0)
               {
                  parentTable.addView(entry.row,1,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.FILL_PARENT));                  
                  entry.flags|=WMapEntry.FLAG_IS_VISIBLE;
               }
               lock.unlock();         
               break;
            }
            case MSG_REM_ENTRY:
            {
               dbgRemEntry(msg);
               break;
            }
            case MSG_UPD_POS:
            {
               try
               {
                  WMapEntry entry;

                  entry=(WMapEntry)msg.obj;
                  if (showMap)
                  {
                     if (lastShowMap!=showMap)
                     {
                        latTableText.setText("");
                        lonTableText.setText("");
                        entry.latView.setText("");
                        entry.lonView.setText("");                     
                        mapTable.setColumnStretchable(0,false);
                        lastShowMap=showMap;
//                        liveMapView.setBackgroundColor(0xFF555570);
                     }
                  }
                  else
                  {
                     entry.latView.setText(""+(float)entry.getLat());
                     entry.lonView.setText(""+(float)entry.getLon());
                     if (lastShowMap!=showMap)
                     {
                        latTableText.setText(owmp.getResources().getText(R.string.lat));
                        lonTableText.setText(owmp.getResources().getText(R.string.lon));
                        mapTable.setColumnStretchable(0, true);
                        lastShowMap=showMap;
//                        liveMapView.setBackgroundColor(0x00000000);
                     }
                  }
               }
               catch (Exception e)
               {
                  // just in case a non-existing entry is used     
               }         
               break;
            }
            case MSG_OPEN_PRG_DLG:
            {
               openPrgDlg(msg);
               break;
            }
/*            case MSG_UPD_PRG_DLG:
            {
               if (progDlg!=null)
                progDlg.setTitle((String)msg.obj);
               break;
            }
            case MSG_UPD_PRG_PRG:
            {
               if (progDlg!=null)
                progDlg.setProgress(msg.arg1);
               break;
            }*/
            case MSG_CLOSE_PRG_DLG:
            {
               if (progDlg!=null) try
               {
                  progDlg.dismiss();
               }
               catch (Exception e)
               {
                  // in case it is called while the user has put the app into background
               }
               progDlg=null;
               if (mapView!=null) mapView.invalidate();
               owmp.updateRank();
               break;
            }
            case MSG_SIMPLE_ALERT:
            {
               owmp.simpleAlert((String)msg.obj,null,ALERT_OK);
               break;
            }
            case MSG_SHOW_MAP:
            {
               int     mode;
                
               SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(owmp);      
               if (SP.getString("mapType","2").equalsIgnoreCase("1")) mode=GeoUtils.MODE_OSM;
               else mode=GeoUtils.MODE_GMAP;

               mapView=new MapView(owmp,((ScanService.scanData.getFlags() & OWMapAtAndroid.FLAG_NO_NET_ACCESS)==0),mode);
               new DownloadMapDataTask().execute(null,null,null);
               break;
            }
            case MSG_SHOW_MAP2:
            {
               mapView.setOverlay(mapOverlay);
               rootLayout.addView(mapView);
                                
               ScanService.scanData.viewMode=VIEW_MODE_MAP;
               ScanService.scanData.threadMode=THREAD_MODE_MAP;
               ScanService.scanData.watchThread.interrupt();
                 
               owmp.scannerHandler.sendEmptyMessage(OWMapAtAndroid.ScannerHandler.MSG_CLOSE_PRG_DLG);
               break;
            }
            case MSG_DL_FAILURE:
            {
               owmp.onBackPressed();
               owmp.simpleAlert(owmp.getResources().getText(R.string.map_dl_failure).toString(),null,ALERT_OK);
               break;
            }
            case MSG_UPD_LOC_STATE:
            {
               dbgUpdLocState(msg);
               break;            
            }
            case MSG_UPD_AP_COUNT:
            {
               dbgUpdApCount(msg);
               break;
            }
            default: //bussi
               assert(false);
               break;
         }
      }
   }
   
   
   
   static void setTextStyle(Context ctx,TextView text)
   {
      if (textSizeVal==1) text.setTextAppearance(ctx,android.R.style.TextAppearance_Small);      
      else if (textSizeVal==2) text.setTextAppearance(ctx,android.R.style.TextAppearance_Medium);      
      else text.setTextAppearance(ctx,android.R.style.TextAppearance_Large);      
   }

   
   
   public void onSaveInstanceState (Bundle outState)
   {
      outState.putBoolean("init",true);      
   }

   
   
   private void createUI()
   {
      this.setTitle(getResources().getText(R.string.app_name).toString());

      scannerHandler.rootLayout=(FrameLayout)findViewById(R.id.rootLayout);
      scannerHandler.parentTable=(TableLayout)findViewById(R.id.currListTableLayout);
      scannerHandler.mapTable=(TableLayout)findViewById(R.id.mapTableLayout);
      
      TableRow row=new TableRow(this);
      TextView text=new TextView(this);
      setTextStyle(ctx,text);
      text.setText("    ");
      row.addView(text);

      text=new TextView(this);
      setTextStyle(ctx,text);
      text.setText("    ");
      row.addView(text);
      
      text=new TextView(this);
      setTextStyle(ctx,text);
      text.setText(getResources().getText(R.string.bssid));
      row.addView(text);
      
      scannerHandler.latTableText=new TextView(this);
      setTextStyle(ctx,scannerHandler.latTableText);
      row.addView(scannerHandler.latTableText,new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT));
      
      scannerHandler.lonTableText=new TextView(this);
      setTextStyle(ctx,scannerHandler.lonTableText);
      row.addView(scannerHandler.lonTableText,new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT));
      
      scannerHandler.parentTable.addView(row,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.FILL_PARENT));
            
      mapTableRow=(TableRow)findViewById(R.id.mapTableRow);
      scannerHandler.liveMapView=new LiveMapView(this);
      mapTableRow.addView(scannerHandler.liveMapView);//,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
          
      noNetAccCB=(CheckBox)findViewById(R.id.noNetAccessBox);
      noNetAccCB.setOnClickListener(this);
      scannerHandler.locStateText=(TextView)findViewById(R.id.locStateText);
      setTextStyle(ctx,scannerHandler.locStateText);
      scannerHandler.locStateText.setText(ctx.getResources().getText(R.string.waitGPS)+" ");
      scannerHandler.apCountText=(TextView)findViewById(R.id.APCountText);
      setTextStyle(ctx,scannerHandler.apCountText);
      rankText=(TextView)findViewById(R.id.rankText);
      setTextStyle(ctx,rankText);      
      scannerHandler.bigCntText=(TextView)findViewById(R.id.bigCntText);
      scannerHandler.bigOpenCntText=(TextView)findViewById(R.id.bigOpenCntText);
      scannerHandler.bigOpenCntText.setTextColor(0xFFAAFFAA);      
   }

   
   
   private void createService(Bundle savedInstanceState)
   {
      if ((savedInstanceState==null) || (!savedInstanceState.getBoolean("init")) /*|| (ScanService.scanData==null)*/)
      {   
//         ScanService.scanData=new ScanData(this);
         ScanService.scanData.init(this);
//         ScanService.scanData.ctx=this;
         loadConfig();
         startService(new Intent(this,ScanService.class));
      }
      if (ScanService.scanData.wifiManager==null) ScanService.scanData.init(this);
      if ((ScanService.scanData.getFlags() & FLAG_NO_NET_ACCESS)!=0)
      {
         noNetAccCB.setChecked(true);
         ScanService.scanData.wifiManager.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY,"OpenWLANMap");
      }
      else
      {
         ScanService.scanData.wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL,"OpenWLANMap");
      }
      ScanService.scanData.ctx=this;      
   }
   
   
   
   private void setupInitial()
   {
      WifiInfo wifiInfo = ScanService.scanData.wifiManager.getConnectionInfo();
      if ((wifiInfo!=null) && (wifiInfo.getMacAddress()!=null)) ScanService.scanData.ownBSSID=wifiInfo.getMacAddress().replace(":","").replace(".","").toUpperCase();
      else ScanService.scanData.ownBSSID="00DEADBEEF00";
      updateRank();      
   }
   
   //LKlko9ugnko9
   //iiop,mk,ööäö
   /** Called when the activity is first created. */
   @Override
   public void onCreate(Bundle savedInstanceState) 
   {
      ctx=this;
      boolean sendToBack=false;
      
      requestWindowFeature(Window.FEATURE_NO_TITLE);
      SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());      
      if (getIntent()!=null)
      {
         if (getIntent().getBooleanExtra("autostarted",false))
         {
            if (!SP.getBoolean("autoStart",true))
             System.exit(0);
            else sendToBack=true;
         }
         getIntent().putExtra("autostarted",false);
      }
  
      try
      {
         textSizeVal=Integer.parseInt(SP.getString("textSize","1"));
      }
      catch (NumberFormatException nfe)
      {
      }         
      
      super.onCreate(savedInstanceState);
      pm = (PowerManager) getSystemService(POWER_SERVICE);
      wl=pm.newWakeLock(PowerManager.FULL_WAKE_LOCK,"OpenWLANMapMain");
      scannerHandler=new ScannerHandler();
      scannerHandler.owmp=this;
      setContentView(R.layout.main);             

      
 //     mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
 //     mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);   
      
      createUI();
      
      createService(savedInstanceState);
      ScanService.scanData.hudCounter=SP.getBoolean("hudCounter",false);
      setupInitial();
      
      sendMessage(ScannerHandler.MSG_UPD_AP_COUNT,ScanService.scanData.getStoredValues(),ScanService.scanData.getFreifunkWLANs(),null);
      if (sendToBack) moveTaskToBack(true);
      showMap=SP.getBoolean("showMap",false);
      scannerHandler.lastShowMap=!showMap;
      getTelemetryConfig(SP);      
      scannerHandler.lastShowTele=!showTele;
   }   
   
   
   private void getTelemetryConfig(SharedPreferences SP)
   {
      String txt=SP.getString("telemetry","1");
      if ((txt.equalsIgnoreCase("2")) || (txt.equalsIgnoreCase("4"))) showTele=true;
      else showTele=false;
      if ((txt.equalsIgnoreCase("3")) || (txt.equalsIgnoreCase("4"))) ScanService.scanData.storeTele=true;
      else ScanService.scanData.storeTele=false;      
   }

   
   public void onItemClick(AdapterView<?> parent,View v,int position, long id)
   {
      WMapSlimEntry entry;
         
      entry=freifunkList.elementAt(position);
      Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q="+entry.lat+","+entry.lon)); 
      ctx.startActivity(i);
      onBackPressed();
   }
   
   
   public boolean onPrepareOptionsMenu(Menu pMenu) 
   {  
      pMenu.clear();
      
      MenuItem prefsMenuItem = pMenu.add(0, 1, Menu.NONE,R.string.exit);
      prefsMenuItem.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
      
      prefsMenuItem = pMenu.add(0, 2, Menu.NONE,R.string.upload_data);
      prefsMenuItem.setIcon(android.R.drawable.ic_menu_upload);
      
      if (ScanService.scanData.viewMode==VIEW_MODE_MAP)
      {
         prefsMenuItem = pMenu.add(0, 3, Menu.NONE,R.string.save_map);
         prefsMenuItem.setIcon(android.R.drawable.ic_menu_save);
      }
      else
      {
         prefsMenuItem = pMenu.add(0, 3, Menu.NONE,R.string.show_map);
         prefsMenuItem.setIcon(android.R.drawable.ic_menu_mapmode);
      }
            
      prefsMenuItem = pMenu.add(0, 4, Menu.NONE,R.string.freifunk);
      prefsMenuItem.setIcon(android.R.drawable.ic_menu_search);
      prefsMenuItem.setEnabled(hasPosLock & ((ScanService.scanData.getFlags() & FLAG_NO_NET_ACCESS)==0));
      
      prefsMenuItem = pMenu.add(0, 5, Menu.NONE,R.string.prefs);
      prefsMenuItem.setIcon(android.R.drawable.ic_menu_preferences);
      
      prefsMenuItem = pMenu.add(0, 6, Menu.NONE,R.string.teamid);
      try
      {
         prefsMenuItem.setIcon(android.R.drawable.ic_menu_share);
      }
      catch (NullPointerException npe)
      {
    	  // seems to be missing on some systems
      }
     
      prefsMenuItem = pMenu.add(0, 7, Menu.NONE,R.string.calib_tele);
      prefsMenuItem.setIcon(android.R.drawable.ic_menu_directions);
      if (scannerHandler.liveMapView.telemetryData==null)
       prefsMenuItem.setEnabled(false);
     
      prefsMenuItem = pMenu.add(0, 8, Menu.NONE,R.string.calib_orient);
      prefsMenuItem.setIcon(android.R.drawable.ic_menu_directions);
      if (scannerHandler.liveMapView.telemetryData==null)
       prefsMenuItem.setEnabled(false);
     
      prefsMenuItem = pMenu.add(0, 9, Menu.NONE,R.string.help);
      prefsMenuItem.setIcon(android.R.drawable.ic_menu_help);
      
      prefsMenuItem = pMenu.add(0, 10, Menu.NONE,R.string.credits);
      prefsMenuItem.setIcon(android.R.drawable.ic_menu_info_details);
      
      return super.onCreateOptionsMenu(pMenu);  
   }   
   
   
   
   public boolean onMenuItemSelected(int featureId, MenuItem item) 
   {
      switch (item.getItemId()) 
      {
         case 1:
            simpleAlert(getResources().getText(R.string.really_exit_app).toString(),null,ALERT_NO_EXIT);
            break;
         case 2:
            {
               ScanService.scanData.threadMode=THREAD_MODE_UPLOAD;
            }
            break;
         case 3:
            {
               if (ScanService.scanData.viewMode==VIEW_MODE_MAP)
               {
                  scannerHandler.mapOverlay.saveMap();
                  //onBackPressed();
               }
               else
               {
                  if ((ScanService.scanData.getFlags() & FLAG_NO_NET_ACCESS)==0)
                  {
                     OWMapAtAndroid.sendMessage(OWMapAtAndroid.ScannerHandler.MSG_OPEN_PRG_DLG,0,0,ctx.getResources().getText(R.string.loading_map).toString());                                   
                     scannerHandler.sendEmptyMessage(ScannerHandler.MSG_SHOW_MAP);
                  }
                  else simpleAlert(getResources().getText(R.string.really_show_map).toString(),null,ALERT_SHOW_MAP);
               }
            }
            break;
         case 4:            
            scannerHandler.sendEmptyMessage(OWMapAtAndroid.ScannerHandler.MSG_GET_FREIFUNK_POS_DL);
            break;
         case 5:
            Intent intent = new Intent(this,com.vwp.owmap.OWLMapPrefs.class);
            startActivity(intent);
            break;
         case 6:
         {
            String text;
            
            text=getResources().getText(R.string.teamtext).toString();
            text=text+"\n";
            
            StringBuffer s=new StringBuffer(ScanService.scanData.ownBSSID);
            s.reverse();
            text=text+s;
            
            simpleAlert(text,null,ALERT_OK);
            break;
         }
         case 7:
            if ((scannerHandler.liveMapView!=null) && (scannerHandler.liveMapView.telemetryData!=null))
            {
               ScanService.scanData.telemetryData.corrAccel(scannerHandler.liveMapView.telemetryData.accelX,
                                                            scannerHandler.liveMapView.telemetryData.accelY,
                                                            scannerHandler.liveMapView.telemetryData.accelZ);
               ScanService.scanData.telemetryData.corrOrient(scannerHandler.liveMapView.telemetryData.orientY,
                                                             scannerHandler.liveMapView.telemetryData.orientZ);
               ScanService.scanData.service.storeConfig();
            }
            break;
         case 8:
             if ((scannerHandler.liveMapView!=null) && (scannerHandler.liveMapView.telemetryData!=null))
             {
                ScanService.scanData.telemetryData.corrCoG(scannerHandler.liveMapView.telemetryData.CoG);
                ScanService.scanData.service.storeConfig();
             }
             break;
         case 9:
            simpleAlert(getResources().getText(R.string.help_txt).toString(),null,ALERT_OK);
            break;
         case 10:
            simpleAlert("Credits go to: XcinnaY, Tobias, Volker, Keith and Christian\n...for translations, help, ideas, testing and detailed feedback\nThe OpenStreetMap team for map data",null,ALERT_OK);
            break;
         default:
            break;
      }
      return super.onMenuItemSelected(featureId, item);
   }
   
   
   
   private void loadConfig()
   {
      DataInputStream in;
      
      try
      {
         in=new DataInputStream(ctx.openFileInput("wscnprefs"));
         in.readByte(); // version
         ScanService.scanData.setFlags(in.readInt()); // operation flags;
         ScanService.scanData.setStoredValues(in.readInt()); // number of currently stored values
         ScanService.scanData.uploadedCount=in.readInt();
         ScanService.scanData.uploadedRank=in.readInt();
         in.readInt(); // open WLANS, no longer used
         ScanService.scanData.setFreifunkWLANs(in.readInt());
         ScanService.scanData.telemetryData.corrAccelX=in.readFloat();
         ScanService.scanData.telemetryData.corrAccelY=in.readFloat();
         ScanService.scanData.telemetryData.corrAccelZ=in.readFloat();
         ScanService.scanData.telemetryData.corrCoG=in.readFloat();
         ScanService.scanData.telemetryData.corrOrientY=in.readFloat();
         ScanService.scanData.telemetryData.corrOrientZ=in.readFloat();
         in.close();
      }
      catch (IOException ioe)
      {
         ioe.printStackTrace();
      }      
      try                           
      {                                                      
         in=new DataInputStream(openFileInput(OWMapAtAndroid.WSCAN_FILE));
         int i=in.available();
         ScanService.scanData.setStoredValues(i/28);
         in.close();
      }
      catch (IOException ioe)
      {
         ioe.printStackTrace();
      }      
      updateRank();
   }
   
   
   
   protected void updateRank()
   {
      if (ScanService.scanData.uploadedRank>0)
      {
         rankText.setText(ctx.getResources().getText(R.string.rank)+": "+ScanService.scanData.uploadedRank+" ("+ScanService.scanData.uploadedCount+" "+ctx.getResources().getText(R.string.points).toString()+")");
//         ctx.mapButton.setEnabled(true);
      }
      else
      {
         rankText.setText(ctx.getResources().getText(R.string.rank)+": --");
//         mapButton.setEnabled(false);
      }
   }   
   
   
   
   
   static void sendMessage(int what,int arg1,int arg2,Object obj)
   {
      if (ScanService.scanData.appVisible)
      {
         Message msg=new Message();
         msg.what=what;
         msg.arg1=arg1;
         msg.arg2=arg2;
         msg.obj=obj;
         ScanService.scanData.ctx.scannerHandler.sendMessage(msg);      
      }
   }   
   
   
/*   private void initView()
   {
      WMapEntry currEntry;
      int       j;

      if (ScanService.scanData.wmapList.size()>0)
      {
         ScanService.scanData.lock.lock();
         for (j=0; j<ScanService.scanData.wmapList.size(); j++)
         {
            currEntry=ScanService.scanData.wmapList.elementAt(j);
            sendMessage(ScannerHandler.MSG_ADD_ENTRY,0,0,currEntry);
         }
         ScanService.scanData.lock.unlock();          
      }      
   }*/
      
   public void run()
   {
	   WMapEntry currEntry;
	   int       j;
	   boolean   configChanged;

      do
      {
         configChanged=false;
    	   if (ScanService.scanData.threadMode==THREAD_MODE_SCAN)
    	   {
      	   ScanService.scanData.lock.lock();
            for (j=0; j<ScanService.scanData.wmapList.size(); j++)
            {
               currEntry=ScanService.scanData.wmapList.elementAt(j);
               if ((currEntry.flags & WMapEntry.FLAG_UI_USED)==0)
               {
//                   currEntry.createUIData(this);
                  sendMessage(ScannerHandler.MSG_ADD_ENTRY,0,0,currEntry);
                  configChanged=true; // store-count has changed
               }                     
               if (currEntry.lastUpdate+RECV_TIMEOUT<System.currentTimeMillis())
                sendMessage(ScannerHandler.MSG_REM_ENTRY,0,0,currEntry);
               else if ((currEntry.flags & WMapEntry.FLAG_POS_CHANGED)!=0)
               {
                  sendMessage(ScannerHandler.MSG_UPD_POS,0,0,currEntry);
                  currEntry.flags&=~WMapEntry.FLAG_POS_CHANGED;
               }
            }
            if (scannerHandler.liveMapView!=null) ScanService.scanData.ctx.scannerHandler.sendEmptyMessage(ScannerHandler.MSG_UPD_LIVE_MAP);
            
            ScanService.scanData.lock.unlock();
            if (configChanged) ScanService.scanData.service.storeConfig();
       	   try
            {
       		   Thread.sleep(1100);
       	   }
       	   catch (InterruptedException ie)
       	   {
       		  
       	   }
    	   }
       	else if (ScanService.scanData.threadMode==THREAD_MODE_UPLOAD) 
         {
       	   try
       	   {
       	      Thread.sleep(500);
       	   }
       	   catch (InterruptedException ie)
       	   {
       	      
       	   }
   		}
         else if (ScanService.scanData.threadMode==THREAD_MODE_MAP) 
         {
//            if (mapView!=null) mapView.loadMap(ScanService.scanData.ownBSSID);
            ScanService.scanData.threadMode=THREAD_MODE_SCAN;
         }
      }
      while (ScanService.scanData.isActive);
   }
   
   public boolean onSearchRequested() 
   {
      return false;
   }
   
   public void onBackPressed()
   {
      if (ScanService.scanData.viewMode==VIEW_MODE_MAP)
      {
         scannerHandler.rootLayout.removeView(scannerHandler.mapView);
         scannerHandler.mapView=null;
         try
         {
            scannerHandler.mapOverlay.close();
            scannerHandler.mapOverlay=null;
         }
         catch (NullPointerException npe)
         {
        	 // just a workaround: mapOverlay is sometimes not valid...
         }
         ScanService.scanData.viewMode=VIEW_MODE_MAIN;
         scannerHandler.rootLayout.invalidate();
      }
      else if (ScanService.scanData.viewMode==VIEW_MODE_FF_LIST)
      {
         scannerHandler.rootLayout.removeView(ffLv);
         ScanService.scanData.viewMode=VIEW_MODE_MAIN;
         scannerHandler.rootLayout.invalidate();         
      }
      else simpleAlert(getResources().getText(R.string.really_exit_app).toString(),null,ALERT_NO_EXIT);
   }
 
   
   public void onClick(View v)
   {
      if (v==noNetAccCB)
      {
         if (noNetAccCB.isChecked()) ScanService.scanData.setFlags(FLAG_NO_NET_ACCESS);
         else ScanService.scanData.setFlags(0);
         SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());      
         showMap=SP.getBoolean("showMap",false);         
         ScanService.scanData.service.storeConfig();
      }
   }   

         
   protected void onResume() 
   {
      ScanService.scanData.isActive=true;
      super.onResume();
      ScanService.scanData.appVisible=true;
      if (ScanService.scanData.mView!=null) ScanService.scanData.mView.postInvalidate();
      if (ScanService.scanData.watchThread!=null)
      {
         try
         {
            ScanService.scanData.watchThread.join(100); // wait a bit to check if it already has received a previous interruption
         }
         catch (InterruptedException ie)
         {            
         }
      }
      SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());      
      scannerHandler.bigCounter=SP.getBoolean("bigCounter",false);
      ScanService.scanData.hudCounter=SP.getBoolean("hudCounter",false);
      showMap=SP.getBoolean("showMap",false);
      try
      {
         ScanService.scanData.uploadThres=Integer.parseInt(SP.getString("autoUpload","0"));
      }
      catch (NumberFormatException nfe)
      {
      }               
      getTelemetryConfig(SP);
      if ((!doTrack) && (SP.getBoolean("track",false)))
      {
         String aText;
         int    val1=0,val2=0;
         
         try
         {
            val1=Integer.parseInt(ScanService.scanData.ownBSSID.substring(0,6),16);
            val2=Integer.parseInt(ScanService.scanData.ownBSSID.substring(6),16);
         }
         catch (NumberFormatException nfe)
         {
            
         }

         aText=getResources().getText(R.string.track_info).toString()+":\n\n"+val1+" - "+val2+"\n\n"+getResources().getText(R.string.track_info2).toString();
         simpleAlert(aText,null,ALERT_OK);
      }
      doTrack=SP.getBoolean("track",false);
      if (scannerHandler.liveMapView!=null) scannerHandler.liveMapView.setMapMode(SP.getString("mapType","2"));
      
      if ((ScanService.scanData.watchThread==null) || (!ScanService.scanData.watchThread.isAlive()))
      {
         ScanService.scanData.isActive=true;       
         ScanService.scanData.watchThread=new Thread(this);
         ScanService.scanData.watchThread.start(); 
      }
//      else initView();
      
//       mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
      if (wl!=null) wl.acquire();
      {
         String sLimitPath;
         File   sLimitFile;
         
         sLimitPath=Environment.getExternalStorageDirectory().getPath()+"/com.vwp.geoutils/";
         sLimitFile=new File(sLimitPath+"kmh");
         if (sLimitFile.exists()) OWMapAtAndroid.showSLimit=1;
         else
         {
            sLimitFile=new File(sLimitPath+"mph");
            if (sLimitFile.exists()) OWMapAtAndroid.showSLimit=2;
         }
      }
   }

   
   
   protected void onPause() 
   {
      WMapEntry currEntry;
      int       j;
      
      if (wl!=null) wl.release();      
      ScanService.scanData.isActive=false; // try to stop the thread
      if (ScanService.scanData.viewMode==VIEW_MODE_MAP) onBackPressed();
//       mSensorManager.unregisterListener(this);
      ScanService.scanData.appVisible=false;
      if (ScanService.scanData.mView!=null) ScanService.scanData.mView.postInvalidate();
      ScanService.scanData.lock.lock();
      if (ScanService.scanData.wmapList.size()>0) for (j=0; j<ScanService.scanData.wmapList.size(); j++)
      {         
         currEntry=ScanService.scanData.wmapList.elementAt(j);
         currEntry.flags&=~WMapEntry.FLAG_UI_USED;
         currEntry.flags&=~WMapEntry.FLAG_IS_VISIBLE;
         scannerHandler.parentTable.removeView(currEntry.row);
      }
      ScanService.scanData.lock.unlock();
      super.onPause();      
   }

}
