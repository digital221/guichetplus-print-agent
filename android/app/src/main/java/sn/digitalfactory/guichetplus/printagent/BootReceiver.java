package sn.digitalfactory.guichetplus.printagent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) { context.startActivity(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)); }
}
