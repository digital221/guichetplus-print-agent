package sn.digitalfactory.guichetplus.printagent;

import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.content.*;
import android.graphics.Color;
import android.content.pm.PackageManager;
import android.hardware.usb.*;
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
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final String URL = "https://gplus.digitalfactory.sn/kiosk", PRINTER = "192.168.1.196";
    private static final int MIN_ADMIN_PASSWORD_LENGTH = 8;
    private static final String ACTION_USB_PERMISSION = "sn.digitalfactory.guichetplus.USB_PERMISSION";
    private static final String[] PRINTER_MODES = {"Automatique (USB → Bluetooth → Réseau)","Réseau (IP)","USB","Bluetooth"};
    private static final String[] PRINTER_MODE_VALUES = {"auto","network","usb","bluetooth"};
    private static final String[] PRINTER_PROFILES = {"Universel · imprimantes chinoises","ESC/POS moderne · Epson compatible","Ancien modèle · ESC *"};
    private static final String[] PRINTER_PROFILE_VALUES = {EscPosCompat.AUTO,EscPosCompat.NATIVE,EscPosCompat.LEGACY};
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
    private UsbManager usbManager;
    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!ACTION_USB_PERMISSION.equals(intent.getAction())) return;
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false) && device != null) toast("Imprimante USB autorisée");
            else toast("Autorisation USB refusée");
        }
    };
    private final Runnable refreshWatchdog = new Runnable() {
        @Override public void run() {
            if (web != null && System.currentTimeMillis() - lastInteraction > 5 * 60_000L) web.reload();
            handler.postDelayed(this, 15 * 60_000L);
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        keepScreenAwake();
        prefs = getSharedPreferences("agent", MODE_PRIVATE);
        usbManager = (UsbManager)getSystemService(USB_SERVICE);
        IntentFilter usbFilter = new IntentFilter(ACTION_USB_PERMISSION);
        if(android.os.Build.VERSION.SDK_INT>=33)registerReceiver(usbPermissionReceiver,usbFilter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(usbPermissionReceiver,usbFilter);
        dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        admin = new ComponentName(this, KioskDeviceAdminReceiver.class);
        handleUsbIntent(getIntent());
        boolean configured=prefs.getBoolean("configured",false);
        if(configured&&prefs.getInt("setup_revision",0)>=1)showKiosk();else showSetup(true);
    }

    private void showSetup(boolean first) {
        unlock(); showBars();
        FrameLayout backdrop=new FrameLayout(this);backdrop.setBackgroundResource(R.drawable.setup_background);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        LinearLayout root = new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER_HORIZONTAL);root.setPadding(dp(24),dp(34),dp(24),dp(34));
        ImageView icon = new ImageView(this); icon.setImageResource(R.drawable.guichetplus_icon); root.addView(icon, new LinearLayout.LayoutParams(dp(90),dp(90)));
        TextView title = text(first ? "Bienvenue sur Guichet+" : "Réglages Guichet+", 30, true);title.setTextColor(Color.WHITE);title.setGravity(Gravity.CENTER);root.addView(title,lp(7));
        TextView help = text(first?"Préparons votre borne en quelques instants.":"Modifiez votre borne en toute sécurité.",16,false);help.setTextColor(Color.rgb(207,235,233));help.setGravity(Gravity.CENTER);root.addView(help,lp(22));
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(25),dp(24),dp(25),dp(24));card.setBackgroundResource(R.drawable.setup_card);card.setElevation(dp(10));
        LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(-1,-2);cardLp.setMargins(0,0,0,dp(18));cardLp.width=Math.min(dp(680),getResources().getDisplayMetrics().widthPixels-dp(48));root.addView(card,cardLp);
        EditText url=field("URL du kiosque",prefs.getString("url",URL)), host=field("IP de l’imprimante",prefs.getString("host",PRINTER)), port=field("Port ESC/POS",String.valueOf(prefs.getInt("port",9100)));
        EditText password=field(hasStoredAdminPassword()?"Nouveau mot de passe administrateur (facultatif, 8 caractères minimum)":"Mot de passe administrateur (8 caractères minimum)",""); password.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(sectionLabel("01  VOTRE BORNE"),lp(10));card.addView(url,lp(22));
        card.addView(sectionLabel("02  IMPRESSION"),lp(10));
        Spinner printerMode=new Spinner(this);printerMode.setBackgroundResource(R.drawable.setup_field);printerMode.setPadding(dp(14),0,dp(14),0);printerMode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,PRINTER_MODES));printerMode.setSelection(modeIndex(prefs.getString("printer_mode","network")));card.addView(printerMode,fixedLp(54,10));
        Spinner printerProfile=new Spinner(this);printerProfile.setBackgroundResource(R.drawable.setup_field);printerProfile.setPadding(dp(14),0,dp(14),0);printerProfile.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,PRINTER_PROFILES));printerProfile.setSelection(profileIndex(prefs.getString("printer_profile",EscPosCompat.AUTO)));card.addView(printerProfile,fixedLp(54,10));
        LinearLayout networkRow=new LinearLayout(this);networkRow.setOrientation(LinearLayout.HORIZONTAL);networkRow.addView(host,new LinearLayout.LayoutParams(0,dp(54),2));LinearLayout.LayoutParams portLp=new LinearLayout.LayoutParams(0,dp(54),1);portLp.setMargins(dp(8),0,0,0);networkRow.addView(port,portLp);card.addView(networkRow,lp(10));
        Button usb=secondaryButton(prefs.contains("usb_vendor")?"USB · "+prefs.getString("usb_name","enregistrée"):"Connecter une imprimante USB");usb.setOnClickListener(v->{printerMode.setSelection(2);selectUsbPrinter(usb);});card.addView(usb,fixedLp(52,8));
        Button bluetooth=secondaryButton(prefs.contains("bt_address")?"Bluetooth · "+prefs.getString("bt_name","enregistrée"):"Connecter une imprimante Bluetooth");bluetooth.setOnClickListener(v->{printerMode.setSelection(3);selectBluetoothPrinter(bluetooth);});card.addView(bluetooth,fixedLp(52,22));
        card.addView(sectionLabel("03  CONNEXION ET SÉCURITÉ"),lp(10));
        TextView currentWifi=text("Wi-Fi actuel  ·  "+wifiName(),15,true);card.addView(currentWifi,lp(8));
        Button wifi=secondaryButton("Sélectionner le réseau Wi-Fi"); wifi.setOnClickListener(v->openSettings(Settings.ACTION_WIFI_SETTINGS)); card.addView(wifi,fixedLp(52,8));
        Button camera=button(checkSelfPermission(android.Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED?"Caméra QR autorisée":"Autoriser la caméra pour les QR codes");
        camera.setBackgroundResource(R.drawable.setup_button_secondary);camera.setOnClickListener(v->requestPermissions(new String[]{android.Manifest.permission.CAMERA},40));card.addView(camera,fixedLp(52,10));
        card.addView(password,lp(18));
        Button test=secondaryButton("Imprimer un ticket de test"); test.setOnClickListener(v->{if(save(url,host,port,password,printerMode,printerProfile,false))printTest();});card.addView(test,fixedLp(52,10));
        Button done=primaryButton(first?"Lancer ma borne":"Enregistrer et retourner au kiosk");done.setOnClickListener(v->{if(save(url,host,port,password,printerMode,printerProfile,true))showKiosk();});card.addView(done,fixedLp(58,0));
        TextView footer=text("DIGITAL FACTORY  ·  BORNE SÉCURISÉE",12,true);footer.setTextColor(Color.rgb(177,216,213));footer.setGravity(Gravity.CENTER);root.addView(footer,lp(0));
        scroll.addView(root,new ScrollView.LayoutParams(-1,-2));backdrop.addView(scroll,new FrameLayout.LayoutParams(-1,-1));setContentView(backdrop);
    }

    private void showKiosk() {
        immersive();
        FrameLayout frame=new FrameLayout(this); LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        frame.setKeepScreenOn(true);
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
            dpm.setGlobalSetting(admin,Settings.Global.STAY_ON_WHILE_PLUGGED_IN,"7");
            dpm.setSystemSetting(admin,Settings.System.SCREEN_OFF_TIMEOUT,String.valueOf(Integer.MAX_VALUE));
            if(dpm.isLockTaskPermitted(getPackageName())) startLockTask();
        } catch(RuntimeException e){toast("Mode kiosk : "+e.getMessage());}
    }
    private void keepScreenAwake(){
        Window window=getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams attributes=window.getAttributes();
        attributes.screenBrightness=1.0f;
        window.setAttributes(attributes);
    }
    private void unlock(){try{stopLockTask();}catch(RuntimeException ignored){}}
    private void secretTap(){long now=System.currentTimeMillis();taps=now-lastTap<=1500?taps+1:1;lastTap=now;if(taps>=7){taps=0;if(hasStoredAdminPassword())requestAdminPassword();else defineAdminPassword();}}

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

    private void defineAdminPassword() {
        final EditText input=field("Nouveau mot de passe administrateur","");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setPadding(dp(20),dp(8),dp(20),dp(8));
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("Mot de passe administrateur")
                .setMessage("Aucun mot de passe n’est encore enregistré. Choisissez-en un d’au moins 8 caractères.")
                .setView(input)
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Enregistrer",null)
                .create();
        dialog.setOnShowListener(v->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
                if(input.getText().toString().length()<MIN_ADMIN_PASSWORD_LENGTH){input.setError("8 caractères minimum");input.requestFocus();return;}
                if(!storeAdminPassword(input.getText().toString())){toast("Impossible d’enregistrer le mot de passe administrateur.");return;}
                dialog.dismiss(); maintenance();
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
    private void print(byte[] data){executor.execute(()->{try{sendToPrinter(data);}catch(Exception e){runOnUiThread(()->toast("Imprimante indisponible : "+e.getMessage()));}});}

    private String sendToPrinter(byte[] data)throws Exception{
        data=EscPosCompat.adapt(data,prefs.getString("printer_profile",EscPosCompat.AUTO));
        String mode=prefs.getString("printer_mode","network");
        if("usb".equals(mode)){sendUsb(data);return "USB";}
        if("bluetooth".equals(mode)){sendBluetooth(data);return "Bluetooth";}
        if("network".equals(mode)){sendNetwork(data);return "réseau";}
        Exception last=null;
        try{sendUsb(data);return "USB";}catch(Exception e){last=e;}
        try{sendBluetooth(data);return "Bluetooth";}catch(Exception e){last=e;}
        try{sendNetwork(data);return "réseau";}catch(Exception e){last=e;}
        throw last==null?new Exception("aucune imprimante configurée"):last;
    }
    private void sendNetwork(byte[] data)throws Exception{String host=prefs.getString("host",PRINTER);int port=prefs.getInt("port",9100);try(Socket s=new Socket()){s.connect(new InetSocketAddress(host,port),4000);OutputStream o=s.getOutputStream();o.write(data);o.flush();}}
    private void sendUsb(byte[] data)throws Exception{
        UsbDevice device=findUsbPrinter();if(device==null)throw new Exception("imprimante USB non connectée");
        if(!usbManager.hasPermission(device)){runOnUiThread(()->requestUsbPermission(device));throw new Exception("autorisation USB requise");}
        UsbInterface selected=null;UsbEndpoint output=null;
        for(int i=0;i<device.getInterfaceCount()&&output==null;i++){UsbInterface iface=device.getInterface(i);for(int j=0;j<iface.getEndpointCount();j++){UsbEndpoint ep=iface.getEndpoint(j);if(ep.getDirection()==UsbConstants.USB_DIR_OUT&&ep.getType()==UsbConstants.USB_ENDPOINT_XFER_BULK){selected=iface;output=ep;break;}}}
        if(selected==null||output==null)throw new Exception("interface ESC/POS USB introuvable");
        UsbDeviceConnection connection=usbManager.openDevice(device);if(connection==null)throw new Exception("connexion USB impossible");
        try{if(!connection.claimInterface(selected,true))throw new Exception("interface USB occupée");for(int offset=0;offset<data.length;){int length=Math.min(16384,data.length-offset);int written=connection.bulkTransfer(output,data,offset,length,5000);if(written<=0)throw new Exception("écriture USB interrompue");offset+=written;}}finally{try{connection.releaseInterface(selected);}catch(Exception ignored){}connection.close();}
    }
    private void sendBluetooth(byte[] data)throws Exception{
        if(android.os.Build.VERSION.SDK_INT>=31&&checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)throw new Exception("autorisation Bluetooth requise");
        String address=prefs.getString("bt_address","");if(address.isEmpty())throw new Exception("imprimante Bluetooth non sélectionnée");
        BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();if(adapter==null||!adapter.isEnabled())throw new Exception("Bluetooth désactivé");
        BluetoothDevice device=adapter.getRemoteDevice(address);UUID spp=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        try(BluetoothSocket socket=device.createRfcommSocketToServiceRecord(spp)){socket.connect();OutputStream out=socket.getOutputStream();out.write(data);out.flush();}
    }

    private void selectUsbPrinter(Button button){
        ArrayList<UsbDevice> devices=new ArrayList<>(usbManager.getDeviceList().values());if(devices.isEmpty()){toast("Aucune imprimante USB détectée. Vérifiez le câble OTG.");return;}
        String[] labels=new String[devices.size()];for(int i=0;i<devices.size();i++){UsbDevice d=devices.get(i);String name=android.os.Build.VERSION.SDK_INT>=21?d.getProductName():null;labels[i]=(name==null?d.getDeviceName():name)+" ("+d.getVendorId()+":"+d.getProductId()+")";}
        new AlertDialog.Builder(this).setTitle("Imprimante USB").setItems(labels,(dialog,which)->{UsbDevice d=devices.get(which);prefs.edit().putInt("usb_vendor",d.getVendorId()).putInt("usb_product",d.getProductId()).putString("usb_name",labels[which]).putString("printer_mode","usb").apply();button.setText("USB : "+labels[which]);requestUsbPermission(d);}).setNegativeButton("Annuler",null).show();
    }
    private UsbDevice findUsbPrinter(){int vendor=prefs.getInt("usb_vendor",-1),product=prefs.getInt("usb_product",-1);for(UsbDevice d:usbManager.getDeviceList().values())if((vendor<0||d.getVendorId()==vendor)&&(product<0||d.getProductId()==product))return d;return null;}
    private void requestUsbPermission(UsbDevice device){if(usbManager.hasPermission(device)){toast("Imprimante USB prête");return;}Intent intent=new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName());PendingIntent pending=PendingIntent.getBroadcast(this,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);usbManager.requestPermission(device,pending);}
    private void handleUsbIntent(Intent intent){
        if(intent==null||!UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction()))return;
        UsbDevice device=intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);if(device==null)return;
        if(!prefs.contains("usb_vendor")){String name=device.getProductName()==null?device.getDeviceName():device.getProductName();prefs.edit().putInt("usb_vendor",device.getVendorId()).putInt("usb_product",device.getProductId()).putString("usb_name",name).apply();}
        if(!usbManager.hasPermission(device))requestUsbPermission(device);
    }
    private void selectBluetoothPrinter(Button button){
        if(android.os.Build.VERSION.SDK_INT>=31&&checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_CONNECT},41);toast("Autorisez Bluetooth puis appuyez de nouveau");return;}
        BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();if(adapter==null){toast("Bluetooth indisponible");return;}if(!adapter.isEnabled()){openSettings(Settings.ACTION_BLUETOOTH_SETTINGS);return;}
        ArrayList<BluetoothDevice> devices=new ArrayList<>(adapter.getBondedDevices());if(devices.isEmpty()){toast("Aucune imprimante appairée. Appairez-la dans Android.");openSettings(Settings.ACTION_BLUETOOTH_SETTINGS);return;}
        String[] labels=new String[devices.size()];for(int i=0;i<devices.size();i++){BluetoothDevice d=devices.get(i);labels[i]=(d.getName()==null?"Bluetooth":d.getName())+" ("+d.getAddress()+")";}
        new AlertDialog.Builder(this).setTitle("Imprimante Bluetooth appairée").setItems(labels,(dialog,which)->{BluetoothDevice d=devices.get(which);prefs.edit().putString("bt_address",d.getAddress()).putString("bt_name",labels[which]).putString("printer_mode","bluetooth").apply();button.setText("Bluetooth : "+labels[which]);}).setNegativeButton("Annuler",null).show();
    }

    private boolean save(EditText u, EditText h, EditText p, EditText password, Spinner printerMode, Spinner printerProfile, boolean configured) {
        String newPassword = password.getText().toString();
        if (!newPassword.isEmpty() && newPassword.length() < MIN_ADMIN_PASSWORD_LENGTH) {
            password.setError("8 caractères minimum");
            toast("Le mot de passe administrateur doit contenir au moins 8 caractères.");
            return false;
        }
        if (configured && !hasStoredAdminPassword() && newPassword.isEmpty()) {
            password.setError("Mot de passe requis");
            toast("Définissez un mot de passe administrateur avant de lancer la borne.");
            return false;
        }
        int port = 9100;
        try { port = Integer.parseInt(p.getText().toString().trim()); } catch (Exception ignored) {}
        SharedPreferences.Editor edit = prefs.edit()
                .putString("url", u.getText().toString().trim())
                .putString("host", h.getText().toString().trim())
                .putInt("port", port)
                .putString("printer_mode", PRINTER_MODE_VALUES[printerMode.getSelectedItemPosition()])
                .putString("printer_profile", PRINTER_PROFILE_VALUES[printerProfile.getSelectedItemPosition()])
                .putBoolean("configured", configured || prefs.getBoolean("configured", false));
        if (configured) edit.putInt("setup_revision", 1);
        if (!newPassword.isEmpty() && !storeAdminPassword(newPassword)) {
            toast("Impossible d’enregistrer le mot de passe administrateur.");
            return false;
        }
        edit.apply();
        return true;
    }

    private boolean storeAdminPassword(String newPassword) {
        String salt = newPasswordSalt();
        String hash = sha256(salt + newPassword);
        if (hash == null) return false;
        prefs.edit().putString("admin_password_salt", salt).putString("admin_password_hash", hash).apply();
        return true;
    }

    private boolean hasStoredAdminPassword() {
        String stored = prefs.getString("admin_password_hash", "");
        return stored != null && !stored.isEmpty();
    }

    private boolean passwordMatches(String candidate) {
        String stored = prefs.getString("admin_password_hash", "");
        if (stored == null || stored.isEmpty()) return false;
        String salt = prefs.getString("admin_password_salt", "");
        String digest = (salt == null || salt.isEmpty()) ? sha256(candidate) : sha256(salt + candidate);
        return digest != null && stored.equals(digest);
    }

    private String newPasswordSalt() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) result.append(String.format("%02x", b));
            return result.toString();
        } catch (Exception e) {
            return null;
        }
    }
    private int modeIndex(String value){for(int i=0;i<PRINTER_MODE_VALUES.length;i++)if(PRINTER_MODE_VALUES[i].equals(value))return i;return 1;}
    private int profileIndex(String value){for(int i=0;i<PRINTER_PROFILE_VALUES.length;i++)if(PRINTER_PROFILE_VALUES[i].equals(value))return i;return 0;}
    private String wifiName(){try{WifiInfo i=((WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE)).getConnectionInfo();String s=i==null?null:i.getSSID();return s==null||"<unknown ssid>".equals(s)?"à sélectionner":s.replace("\"","");}catch(Exception e){return "à sélectionner";}}
    private EditText field(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine(true);e.setTextSize(16);e.setTextColor(Color.rgb(15,42,49));e.setHintTextColor(Color.rgb(105,126,130));e.setPadding(dp(15),0,dp(15),0);e.setBackgroundResource(R.drawable.setup_field);e.setSelectAllOnFocus(false);return e;}
    private Button button(String value){Button b=new Button(this);b.setText(value);b.setAllCaps(false);return b;}
    private Button primaryButton(String value){Button b=button(value);b.setTextColor(Color.WHITE);b.setTextSize(16);b.setTypeface(null,android.graphics.Typeface.BOLD);b.setBackgroundResource(R.drawable.setup_button_primary);return b;}
    private Button secondaryButton(String value){Button b=button(value);b.setTextColor(Color.rgb(9,111,108));b.setTextSize(15);b.setBackgroundResource(R.drawable.setup_button_secondary);return b;}
    private TextView sectionLabel(String value){TextView t=text(value,13,true);t.setTextColor(Color.rgb(9,132,126));t.setLetterSpacing(.08f);return t;}
    private TextView text(String value,int size,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.rgb(15,42,49));if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;}
    private LinearLayout.LayoutParams lp(int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(bottom));return p;}
    private LinearLayout.LayoutParams fixedLp(int height,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(height));p.setMargins(0,0,0,dp(bottom));return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void immersive(){getWindow().getDecorView().setSystemUiVisibility(5894|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
    private void showBars(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==40&&cameraRequest!=null){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)cameraRequest.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});else cameraRequest.deny();cameraRequest=null;}}
    @Override protected void onResume(){super.onResume();if(returningFromSettings){returningFromSettings=false;if(prefs.getBoolean("configured",false))showKiosk();}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleUsbIntent(intent);}
    @Override public void onBackPressed(){if(!prefs.getBoolean("configured",false))super.onBackPressed();}
    @Override protected void onDestroy(){handler.removeCallbacks(refreshWatchdog);try{unregisterReceiver(usbPermissionReceiver);}catch(RuntimeException ignored){}if(connectivity!=null&&networkCallback!=null)try{connectivity.unregisterNetworkCallback(networkCallback);}catch(RuntimeException ignored){}executor.shutdownNow();if(web!=null)web.destroy();super.onDestroy();}
}
