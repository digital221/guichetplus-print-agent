package sn.digitalfactory.guichetplus.printagent;

import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.graphics.Color;
import android.content.pm.PackageManager;
import android.net.*;
import android.net.wifi.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final String URL = "https://gplus.digitalfactory.sn/kiosk", PRINTER = "192.168.1.196";
    private static final String DEFAULT_ADMIN_PASSWORD = "2008";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private DevicePolicyManager dpm;
    private ComponentName admin;
    private WebView web;
    private boolean returningFromSettings;
    private int taps;
    private long lastTap;
    private long lastInteraction = System.currentTimeMillis();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PermissionRequest cameraRequest;
    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback networkCallback;
    private final Runnable refreshWatchdog = new Runnable() {
        @Override public void run() {
            if (web != null && System.currentTimeMillis() - lastInteraction > 5 * 60_000L) web.reload();
            handler.postDelayed(this, 15 * 60_000L);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("agent", MODE_PRIVATE);
        dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        admin = new ComponentName(this, KioskDeviceAdminReceiver.class);
        if (prefs.getBoolean("configured", false)) showKiosk(); else showSetup(true);
    }

    private void showSetup(boolean first) {
        unlock(); showBars();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(54), dp(24), dp(54), dp(24)); root.setBackgroundColor(Color.rgb(239,246,246));
        ImageView icon = new ImageView(this); icon.setImageResource(R.drawable.guichetplus_icon); root.addView(icon, new LinearLayout.LayoutParams(dp(82),dp(82)));
        TextView title = text(first ? "Configuration initiale Guichet+" : "Réglages Guichet+", 27, true); title.setGravity(Gravity.CENTER); root.addView(title, lp(10));
        TextView help = text("Configurez la borne, le réseau et l’imprimante avant de lancer le kiosk.",16,false); help.setGravity(Gravity.CENTER); root.addView(help,lp(15));
        EditText url=field("URL du kiosque",prefs.getString("url",URL)), host=field("IP de l’imprimante",prefs.getString("host",PRINTER)), port=field("Port ESC/POS",String.valueOf(prefs.getInt("port",9100)));
        EditText password=field("Nouveau mot de passe administrateur (facultatif)",""); password.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(url,lp(5)); root.addView(host,lp(5)); root.addView(port,lp(5)); root.addView(password,lp(8));
        root.addView(text("Wi-Fi actuel : "+wifiName(),16,true),lp(4));
        Button wifi=button("Sélectionner le réseau Wi-Fi"); wifi.setOnClickListener(v->openSettings(Settings.ACTION_WIFI_SETTINGS)); root.addView(wifi,lp(8));
        Button camera=button(checkSelfPermission(android.Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED?"Caméra QR autorisée":"Autoriser la caméra pour les QR codes");
        camera.setOnClickListener(v->requestPermissions(new String[]{android.Manifest.permission.CAMERA},40)); root.addView(camera,lp(8));
        LinearLayout actions=new LinearLayout(this); actions.setGravity(Gravity.CENTER);
        Button test=button("Tester l’imprimante"); test.setOnClickListener(v->{save(url,host,port,password,false);printTest();}); actions.addView(test);
        Button done=button(first?"Enregistrer et lancer la borne":"Enregistrer et retourner au kiosk"); done.setOnClickListener(v->{save(url,host,port,password,true);showKiosk();}); actions.addView(done);
        root.addView(actions,lp(0)); setContentView(root);
    }

    private void showKiosk() {
        immersive();
        FrameLayout frame=new FrameLayout(this); LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        web=new WebView(this); WebSettings ws=web.getSettings(); ws.setJavaScriptEnabled(true); ws.setDomStorageEnabled(true); ws.setDatabaseEnabled(true); ws.setMediaPlaybackRequiresUserGesture(false);
        android.webkit.CookieManager.getInstance().setAcceptCookie(true); android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onPermissionRequest(PermissionRequest request){
                for(String resource:request.getResources()) if(PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)){
                    if(checkSelfPermission(android.Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    else {cameraRequest=request;requestPermissions(new String[]{android.Manifest.permission.CAMERA},40);} return;
                }
                request.deny();
            }
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return intercept(r.getUrl().toString());}
            @Override public boolean shouldOverrideUrlLoading(WebView v,String u){return intercept(u);}
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())handler.postDelayed(()->{if(web!=null)web.reload();},5000);}
        });
        web.setOnTouchListener((v,event)->{lastInteraction=System.currentTimeMillis();return false;});
        web.setFocusableInTouchMode(true); web.requestFocus();
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1)); frame.addView(root,new FrameLayout.LayoutParams(-1,-1));
        View secret=new View(this); secret.setContentDescription("Zone maintenance"); secret.setOnClickListener(v->secretTap()); frame.addView(secret,new FrameLayout.LayoutParams(dp(120),dp(120),Gravity.BOTTOM|Gravity.START));
        setContentView(frame); web.loadUrl(prefs.getString("url",URL)); startNetworkRecovery(); handler.removeCallbacks(refreshWatchdog); handler.postDelayed(refreshWatchdog,15*60_000L); lock();
    }

    private void lock() {
        if(!dpm.isDeviceOwnerApp(getPackageName())) return;
        try {
            dpm.setLockTaskPackages(admin,new String[]{getPackageName()});
            dpm.setPermissionGrantState(admin,getPackageName(),android.Manifest.permission.CAMERA,DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
            IntentFilter home=new IntentFilter(Intent.ACTION_MAIN); home.addCategory(Intent.CATEGORY_HOME); home.addCategory(Intent.CATEGORY_DEFAULT);
            dpm.addPersistentPreferredActivity(admin,home,new ComponentName(this,MainActivity.class));
            if(dpm.isLockTaskPermitted(getPackageName())) startLockTask();
        } catch(RuntimeException e){toast("Mode kiosk : "+e.getMessage());}
    }
    private void unlock(){try{stopLockTask();}catch(RuntimeException ignored){}}
    private void secretTap(){long now=System.currentTimeMillis();taps=now-lastTap<=1500?taps+1:1;lastTap=now;if(taps>=7){taps=0;requestAdminPassword();}}

    private void requestAdminPassword() {
        final EditText input=field("Mot de passe administrateur","");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setPadding(dp(20),dp(8),dp(20),dp(8));
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("Accès administrateur")
                .setMessage("Saisissez le mot de passe pour accéder aux réglages.")
                .setView(input)
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Déverrouiller",null)
                .create();
        dialog.setOnShowListener(v->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
                if(passwordMatches(input.getText().toString())){dialog.dismiss();maintenance();}
                else {input.setText("");input.setError("Mot de passe incorrect");input.requestFocus();}
            });
            input.requestFocus(); dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
        dialog.show();
    }

    private void maintenance() {
        unlock(); LinearLayout menu=new LinearLayout(this); menu.setOrientation(LinearLayout.VERTICAL); menu.setPadding(dp(24),dp(10),dp(24),dp(10));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Maintenance Guichet+").setView(menu).create();
        Button app=button("Configurer URL et imprimante"); app.setOnClickListener(v->{dialog.dismiss();showSetup(false);}); menu.addView(app,lp(6));
        Button wifi=button("Réglages Wi-Fi Android"); wifi.setOnClickListener(v->{dialog.dismiss();openSettings(Settings.ACTION_WIFI_SETTINGS);}); menu.addView(wifi,lp(6));
        Button system=button("Réglages Android"); system.setOnClickListener(v->{dialog.dismiss();openSettings(Settings.ACTION_SETTINGS);}); menu.addView(system,lp(6));
        Button back=button("Retourner au kiosk"); back.setOnClickListener(v->{dialog.dismiss();showKiosk();}); menu.addView(back,lp(0)); dialog.setOnCancelListener(v->showKiosk()); dialog.show();
    }
    private void openSettings(String action){unlock();returningFromSettings=true;startActivity(new Intent(action));}
    private void startNetworkRecovery(){
        if(networkCallback!=null)return;
        connectivity=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        networkCallback=new ConnectivityManager.NetworkCallback(){@Override public void onAvailable(Network network){handler.postDelayed(()->{if(web!=null)web.reload();},1200);}};
        try{connectivity.registerDefaultNetworkCallback(networkCallback);}catch(RuntimeException ignored){}
    }

    private boolean intercept(String u){if(!u.startsWith("intent:base64,"))return false;int end=u.indexOf("#Intent;");if(end<0){toast("Ticket ESC/POS invalide");return true;}try{print(Base64.decode(u.substring(14,end),Base64.DEFAULT));}catch(Exception e){toast("Ticket ESC/POS illisible");}return true;}
    private void printTest(){print(new byte[]{0x1b,0x40,0x1b,0x61,0x01,0x1b,0x45,0x01,'G','U','I','C','H','E','T','+','\n','T','e','s','t',' ','k','i','o','s','k','\n','\n',0x1d,0x56,0x42,0x00});}
    private void print(byte[] data){String host=prefs.getString("host",PRINTER);int port=prefs.getInt("port",9100);executor.execute(()->{try(Socket s=new Socket()){s.connect(new InetSocketAddress(host,port),4000);OutputStream o=s.getOutputStream();o.write(data);o.flush();runOnUiThread(()->toast("Ticket imprimé"));}catch(Exception e){runOnUiThread(()->toast("Imprimante indisponible : "+e.getMessage()));}});}

    private void save(EditText u,EditText h,EditText p,EditText password,boolean configured){int port=9100;try{port=Integer.parseInt(p.getText().toString().trim());}catch(Exception ignored){}SharedPreferences.Editor edit=prefs.edit().putString("url",u.getText().toString().trim()).putString("host",h.getText().toString().trim()).putInt("port",port).putBoolean("configured",configured||prefs.getBoolean("configured",false));String newPassword=password.getText().toString();if(!newPassword.isEmpty())edit.putString("admin_password_hash",sha256(newPassword));edit.apply();}
    private boolean passwordMatches(String candidate){String stored=prefs.getString("admin_password_hash",sha256(DEFAULT_ADMIN_PASSWORD));return stored.equals(sha256(candidate));}
    private String sha256(String value){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder result=new StringBuilder();for(byte b:bytes)result.append(String.format("%02x",b));return result.toString();}catch(Exception e){return value;}}
    private String wifiName(){try{WifiInfo i=((WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE)).getConnectionInfo();String s=i==null?null:i.getSSID();return s==null||"<unknown ssid>".equals(s)?"à sélectionner":s.replace("\"","");}catch(Exception e){return "à sélectionner";}}
    private EditText field(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine(true);e.setTextSize(17);return e;}
    private Button button(String value){Button b=new Button(this);b.setText(value);b.setAllCaps(false);return b;}
    private TextView text(String value,int size,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.rgb(15,42,49));if(bold)t.setTypeface(null,1);return t;}
    private LinearLayout.LayoutParams lp(int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(bottom));return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void immersive(){getWindow().getDecorView().setSystemUiVisibility(5894|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
    private void showBars(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==40&&cameraRequest!=null){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)cameraRequest.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});else cameraRequest.deny();cameraRequest=null;}}
    @Override protected void onResume(){super.onResume();if(returningFromSettings){returningFromSettings=false;if(prefs.getBoolean("configured",false))showKiosk();}}
    @Override public void onBackPressed(){if(!prefs.getBoolean("configured",false))super.onBackPressed();}
    @Override protected void onDestroy(){handler.removeCallbacks(refreshWatchdog);if(connectivity!=null&&networkCallback!=null)try{connectivity.unregisterNetworkCallback(networkCallback);}catch(RuntimeException ignored){}executor.shutdownNow();if(web!=null)web.destroy();super.onDestroy();}
}
