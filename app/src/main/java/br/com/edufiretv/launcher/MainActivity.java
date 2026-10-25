package br.com.edufiretv.launcher;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.widget.Toast;
import android.view.KeyEvent;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public class MainActivity extends Activity {
 private WebView web;
 @Override public void onCreate(Bundle state){super.onCreate(state);
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  web=new WebView(this);web.setBackgroundColor(0xff03070c);web.setFocusable(true);web.setFocusableInTouchMode(true);
  WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setAllowFileAccess(true);s.setDomStorageEnabled(true);
  web.setWebViewClient(new WebViewClient());web.addJavascriptInterface(new Bridge(),"EduFireNative");
  setContentView(web);web.loadUrl("file:///android_asset/index.html");web.requestFocus();
 }
 private List<ResolveInfo> available(){PackageManager pm=getPackageManager();Map<String,ResolveInfo> found=new LinkedHashMap<>();
  for(String cat:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
   Intent query=new Intent(Intent.ACTION_MAIN);query.addCategory(cat);
   for(ResolveInfo r:pm.queryIntentActivities(query,0)){if(r.activityInfo!=null&&!r.activityInfo.packageName.equals(getPackageName()))found.put(r.activityInfo.packageName,r);}
  }return new ArrayList<>(found.values());}
 public class Bridge {
  @JavascriptInterface public String getApps(){JSONArray out=new JSONArray();PackageManager pm=getPackageManager();
   for(ResolveInfo r:available()){try{JSONObject obj=new JSONObject();obj.put("label",r.loadLabel(pm).toString());obj.put("packageName",r.activityInfo.packageName);out.put(obj);}catch(Exception ignored){}}
   return out.toString();}
  @JavascriptInterface public void launch(String name){runOnUiThread(()->{
   PackageManager pm=getPackageManager();String pkg=null;
   for(ResolveInfo r:available()){
    String label=r.loadLabel(pm).toString();
    if(r.activityInfo.packageName.equalsIgnoreCase(name)||label.equalsIgnoreCase(name)||label.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT).replace(" 4.21",""))){pkg=r.activityInfo.packageName;break;}
   }
   if(pkg==null){Toast.makeText(MainActivity.this,name+" não está instalado",Toast.LENGTH_LONG).show();return;}
   Intent launch=pm.getLeanbackLaunchIntentForPackage(pkg);
   if(launch==null)launch=pm.getLaunchIntentForPackage(pkg);
   if(launch!=null){try{startActivity(launch);}catch(Exception e){Toast.makeText(MainActivity.this,"Não foi possível abrir "+name,Toast.LENGTH_LONG).show();}}
   else Toast.makeText(MainActivity.this,"Aplicativo sem atividade inicial",Toast.LENGTH_LONG).show();
  });}
 }
 @Override public void onBackPressed(){if(web!=null)web.evaluateJavascript("document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}))",null);else super.onBackPressed();}
 @Override public boolean onKeyDown(int code,KeyEvent event){String key=null;
  switch(code){case KeyEvent.KEYCODE_DPAD_UP:key="ArrowUp";break;case KeyEvent.KEYCODE_DPAD_DOWN:key="ArrowDown";break;case KeyEvent.KEYCODE_DPAD_LEFT:key="ArrowLeft";break;case KeyEvent.KEYCODE_DPAD_RIGHT:key="ArrowRight";break;case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:key="Enter";break;}
  if(key!=null){web.evaluateJavascript("document.dispatchEvent(new KeyboardEvent('keydown',{key:'"+key+"',bubbles:true}))",null);return true;}
  return super.onKeyDown(code,event);
 }
 @Override protected void onDestroy(){if(web!=null){web.destroy();web=null;}super.onDestroy();}
}