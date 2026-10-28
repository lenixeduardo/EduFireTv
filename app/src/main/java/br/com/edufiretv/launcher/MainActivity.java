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
import java.net.*;
import java.io.*;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Base64;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;

public class MainActivity extends Activity {
 private WebView web;
 private ServerSocket remoteSocket;
 private Thread remoteThread;
 private final String remoteToken=UUID.randomUUID().toString().replace("-","");
 private String remoteUrl="";
 @Override public void onCreate(Bundle state){super.onCreate(state);
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  web=new WebView(this);web.setBackgroundColor(0xff03070c);web.setFocusable(true);web.setFocusableInTouchMode(true);
  WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setAllowFileAccess(true);s.setDomStorageEnabled(true);
  web.setWebViewClient(new WebViewClient());web.addJavascriptInterface(new Bridge(),"EduFireNative");
  setContentView(web);startRemote();web.loadUrl("file:///android_asset/index.html");web.requestFocus();
 }
 private List<ResolveInfo> available(){PackageManager pm=getPackageManager();Map<String,ResolveInfo> found=new LinkedHashMap<>();
  for(String cat:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
   Intent query=new Intent(Intent.ACTION_MAIN);query.addCategory(cat);
   for(ResolveInfo r:pm.queryIntentActivities(query,0)){if(r.activityInfo!=null&&!r.activityInfo.packageName.equals(getPackageName()))found.put(r.activityInfo.packageName,r);}
  }return new ArrayList<>(found.values());}
 public class Bridge {
  @JavascriptInterface public String getRemoteInfo(){JSONObject obj=new JSONObject();try{obj.put("url",remoteUrl);if(!remoteUrl.isEmpty())obj.put("qr",makeQr(remoteUrl));}catch(Exception ignored){}return obj.toString();}

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
 private String makeQr(String url)throws Exception{
  BitMatrix bits=new MultiFormatWriter().encode(url,BarcodeFormat.QR_CODE,280,280);
  Bitmap bmp=Bitmap.createBitmap(280,280,Bitmap.Config.ARGB_8888);
  for(int y=0;y<280;y++)for(int x=0;x<280;x++)bmp.setPixel(x,y,bits.get(x,y)?Color.BLACK:Color.WHITE);
  ByteArrayOutputStream b=new ByteArrayOutputStream();bmp.compress(Bitmap.CompressFormat.PNG,100,b);
  return Base64.encodeToString(b.toByteArray(),Base64.NO_WRAP);
 }
 private void startRemote(){
  try{
   String ip="";Enumeration<NetworkInterface> nets=NetworkInterface.getNetworkInterfaces();
   while(nets.hasMoreElements()){NetworkInterface net=nets.nextElement();if(!net.isUp()||net.isLoopback())continue;
    Enumeration<InetAddress> ips=net.getInetAddresses();while(ips.hasMoreElements()){InetAddress addr=ips.nextElement();if(addr instanceof Inet4Address&&!addr.isLoopbackAddress()&&addr.isSiteLocalAddress()){ip=addr.getHostAddress();break;}}if(!ip.isEmpty())break;}
   if(ip.isEmpty())return;
   remoteSocket=new ServerSocket(8765);
   remoteUrl="http://"+ip+":8765/?token="+remoteToken;
   remoteThread=new Thread(()->{while(remoteSocket!=null&&!remoteSocket.isClosed()){try{Socket s=remoteSocket.accept();s.setSoTimeout(3000);new Thread(()->handleRemote(s)).start();}catch(Exception ignored){}}});
   remoteThread.setDaemon(true);remoteThread.start();
  }catch(Exception ignored){}
 }
 private void handleRemote(Socket s){
  try(Socket socket=s){
   BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));
   String line=reader.readLine();if(line==null)return;
   String path=line.split(" ")[1];boolean authorized=path.contains("token="+remoteToken);
   String body;String type="text/html; charset=utf-8";int status=authorized?200:403;
   if(!authorized)body="Acesso negado";
   else if(path.startsWith("/key?")){
    String key="";for(String part:path.substring(path.indexOf('?')+1).split("&")){if(part.startsWith("key="))key=part.substring(4);}
    final String chosen=key;
    if(Arrays.asList("ArrowUp","ArrowDown","ArrowLeft","ArrowRight","Enter","Escape").contains(chosen)){
     runOnUiThread(()->web.evaluateJavascript("document.dispatchEvent(new KeyboardEvent('keydown',{key:'"+chosen+"',bubbles:true}))",null));
    }
    body="OK";type="text/plain";
   }else{
    body="<!doctype html><html lang='pt-BR'><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{background:#080e19;color:white;font:20px Arial;text-align:center;padding:24px}button{background:#1b2a42;color:white;border:1px solid #7a91b4;border-radius:18px;font-size:28px;width:85px;height:76px;margin:6px}h1{font-size:26px}</style><h1>eduTV · Controle remoto</h1><p>Conectado à TV</p><div><button onclick='send(&quot;ArrowUp&quot;)'>↑</button></div><div><button onclick='send(&quot;ArrowLeft&quot;)'>←</button><button onclick='send(&quot;Enter&quot;)'>OK</button><button onclick='send(&quot;ArrowRight&quot;)'>→</button></div><div><button onclick='send(&quot;ArrowDown&quot;)'>↓</button></div><div><button onclick='send(&quot;Escape&quot;)'>Voltar</button></div><script>function send(k){fetch('/key?token="+remoteToken+"&key='+k)}</script></html>";
   }
   byte[] bytes=body.getBytes("UTF-8");OutputStream out=socket.getOutputStream();
   String headers="HTTP/1.1 "+status+" OK\\r\\nContent-Type: "+type+"\\r\\nContent-Length: "+bytes.length+"\\r\\nCache-Control: no-store\\r\\nConnection: close\\r\\n\\r\\n";
   out.write(headers.replace("\\r","\r").replace("\\n","\n").getBytes("UTF-8"));out.write(bytes);out.flush();
  }catch(Exception ignored){}
 }
 @Override public void onBackPressed(){if(web!=null)web.evaluateJavascript("document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}))",null);else super.onBackPressed();}
 @Override public boolean onKeyDown(int code,KeyEvent event){String key=null;
  switch(code){case KeyEvent.KEYCODE_DPAD_UP:key="ArrowUp";break;case KeyEvent.KEYCODE_DPAD_DOWN:key="ArrowDown";break;case KeyEvent.KEYCODE_DPAD_LEFT:key="ArrowLeft";break;case KeyEvent.KEYCODE_DPAD_RIGHT:key="ArrowRight";break;case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:key="Enter";break;}
  if(key!=null){web.evaluateJavascript("document.dispatchEvent(new KeyboardEvent('keydown',{key:'"+key+"',bubbles:true}))",null);return true;}
  return super.onKeyDown(code,event);
 }
 @Override protected void onDestroy(){try{if(remoteSocket!=null)remoteSocket.close();}catch(Exception ignored){}if(web!=null){web.destroy();web=null;}super.onDestroy();}
}