package com.quickblox.qmunicate.ui.base;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NavUtils;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.quickblox.qmunicate.App;
import com.quickblox.qmunicate.R;
import com.quickblox.qmunicate.core.command.Command;
import com.quickblox.qmunicate.service.QBService;
import com.quickblox.qmunicate.service.QBServiceConsts;
import com.quickblox.qmunicate.ui.dialogs.ProgressDialog;
import com.quickblox.qmunicate.utils.DialogUtils;
import com.quickblox.qmunicate.utils.ErrorUtils;

import java.util.HashMap;
import java.util.Map;

import de.keyboardsurfer.android.widget.crouton.Crouton;

public abstract class BaseActivity extends Activity {

    public static final int DOUBLE_BACK_DELAY = 2000;

    protected final ProgressDialog progress;
    protected BroadcastReceiver broadcastReceiver;
    protected BroadcastReceiver messageBroadcastReceiver;
    protected App app;
    protected ActionBar actionBar;
    protected QBService service;
    protected boolean useDoubleBackPressed;
    protected Fragment currentFragment;
    protected FailAction failAction;

    private View newMessageView;
    private TextView newMessageTextView;
    private TextView senderMessageTextView;
    private boolean doubleBackToExitPressedOnce;
    private Map<String, Command> broadcastCommandMap = new HashMap<String, Command>();
    private boolean bounded;
    private ServiceConnection serviceConnection = new QBChatServiceConnection();

    public BaseActivity() {
        progress = ProgressDialog.newInstance(R.string.dlg_wait_please);
    }

    public FailAction getFailAction() {
        return failAction;
    }

    public void showProgress() {
        progress.show(getFragmentManager(), null);
    }

    public void hideProgress() {
        if (progress != null && progress.getActivity() != null) {
            progress.dismissAllowingStateLoss();
        }
    }

    public void addAction(String action, Command command) {
        broadcastCommandMap.put(action, command);
    }

    public boolean hasAction(String action) {
        return broadcastCommandMap.containsKey(action);
    }

    public void removeAction(String action) {
        broadcastCommandMap.remove(action);
    }

    public void showNewMessageAlert(String sender, String message) {
        newMessageTextView.setText(message);
        senderMessageTextView.setText(sender);
        Crouton.cancelAllCroutons();
        Crouton.show(this, newMessageView);
    }


    public void updateBroadcastActionList() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageBroadcastReceiver);
        IntentFilter intentFilter = new IntentFilter();
        for (String commandName : broadcastCommandMap.keySet()) {
            intentFilter.addAction(commandName);
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter);
        LocalBroadcastManager.getInstance(this).registerReceiver(messageBroadcastReceiver, new IntentFilter(
                QBServiceConsts.GOT_CHAT_MESSAGE));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = App.getInstance();
        actionBar = getActionBar();
        broadcastReceiver = new BaseBroadcastReceiver();
        messageBroadcastReceiver = new MessageBroadcastReceiver();
        failAction = new FailAction();
        initUI();
    }

    @Override
    protected void onStart() {
        super.onStart();
        connectToService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBroadcastActionList();
    }

    @Override
    protected void onPause() {
        unregisterBroadcastReceiver();
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService();
    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce || !useDoubleBackPressed) {
            super.onBackPressed();
            return;
        }
        this.doubleBackToExitPressedOnce = true;
        DialogUtils.show(this, getString(R.string.dlg_click_back_again));
        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;
            }
        }, DOUBLE_BACK_DELAY);
    }

    protected void onConnectedToService() {
    }

    protected void navigateToParent() {
        Intent intent = NavUtils.getParentActivityIntent(this);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        NavUtils.navigateUpTo(this, intent);
    }

    @SuppressWarnings("unchecked")
    protected <T> T _findViewById(int viewId) {
        return (T) findViewById(viewId);
    }

    protected void setCurrentFragment(Fragment fragment) {
        currentFragment = fragment;
        getFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        FragmentTransaction transaction = buildTransaction();
        transaction.replace(R.id.container, fragment, null);
        transaction.commit();
    }

    private void initUI() {
        newMessageView = getLayoutInflater().inflate(R.layout.list_item_new_message, null);
        newMessageTextView = (TextView) newMessageView.findViewById(R.id.message_textview);
        senderMessageTextView = (TextView) newMessageView.findViewById(R.id.sender_textview);
    }

    private void unbindService() {
        if (bounded) {
            unbindService(serviceConnection);
        }
    }

    private void unregisterBroadcastReceiver() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
    }

    private void connectToService() {
        Intent intent = new Intent(this, QBService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private FragmentTransaction buildTransaction() {
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        return transaction;
    }

    protected void onFailAction(String action) {

    }

    public class FailAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            Exception e = (Exception) bundle.getSerializable(QBServiceConsts.EXTRA_ERROR);
            ErrorUtils.showError(BaseActivity.this, e);
            hideProgress();
            onFailAction(bundle.getString(QBServiceConsts.COMMAND_ACTION));
        }
    }

    private class BaseBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (intent != null && (action) != null) {
                Command command = broadcastCommandMap.get(action);
                if (command != null) {
                    Log.d("STEPS", "executing " + action);
                    try {
                        command.execute(intent.getExtras());
                    } catch (Exception e) {

                    }
                }
            }
        }
    }

    private class MessageBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                String message = extras.getString(QBServiceConsts.EXTRA_CHAT_MESSAGE);
                String sender = extras.getString(QBServiceConsts.EXTRA_SENDER_CHAT_MESSAGE);
                showNewMessageAlert(sender, message);
            }
        }
    }

    private class QBChatServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            bounded = true;
            service = ((QBService.QBServiceBinder) binder).getService();
            onConnectedToService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    }
}