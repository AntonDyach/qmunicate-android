package com.quickblox.qmunicate.ui.chats;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.quickblox.module.chat.model.QBDialog;
import com.quickblox.module.content.model.QBFile;
import com.quickblox.qmunicate.R;
import com.quickblox.qmunicate.caching.DatabaseManager;
import com.quickblox.qmunicate.core.command.Command;
import com.quickblox.qmunicate.model.SerializableKeys;
import com.quickblox.qmunicate.qb.commands.QBLoadAttachFileCommand;
import com.quickblox.qmunicate.qb.commands.QBLoadDialogMessagesCommand;
import com.quickblox.qmunicate.qb.helpers.BaseChatHelper;
import com.quickblox.qmunicate.service.QBService;
import com.quickblox.qmunicate.service.QBServiceConsts;
import com.quickblox.qmunicate.ui.base.BaseCursorAdapter;
import com.quickblox.qmunicate.ui.base.BaseFragmentActivity;
import com.quickblox.qmunicate.ui.chats.animation.HeightAnimator;
import com.quickblox.qmunicate.ui.chats.smiles.SmilesTabFragmentAdapter;
import com.quickblox.qmunicate.ui.uihelper.SimpleTextWatcher;
import com.quickblox.qmunicate.ui.views.indicator.IconPageIndicator;
import com.quickblox.qmunicate.ui.views.smiles.ChatEditText;
import com.quickblox.qmunicate.ui.views.smiles.SmileClickListener;
import com.quickblox.qmunicate.ui.views.smiles.SmileysConvertor;
import com.quickblox.qmunicate.utils.Consts;
import com.quickblox.qmunicate.utils.ImageHelper;
import com.quickblox.qmunicate.utils.SizeUtility;

import java.io.File;
import java.nio.charset.Charset;

public abstract class BaseDialogActivity extends BaseFragmentActivity implements SwitchViewListener, ScrollMessagesListener {

    protected static final float SMILES_SIZE_IN_DIPS = 220;

    protected ChatEditText chatEditText;
    protected ListView messagesListView;
    protected EditText messageEditText;
    protected ImageButton sendButton;
    protected String currentOpponent;
    protected String dialogId;

    protected ViewPager smilesViewPager;
    protected View smilesLayout;
    protected IconPageIndicator smilesPagerIndicator;
    protected HeightAnimator smilesAnimator;
    protected SmileSelectedBroadcastReceiver smileSelectedBroadcastReceiver;
    protected int layoutResID;
    protected ImageHelper imageHelper;
    protected BaseCursorAdapter messagesAdapter;
    protected QBDialog dialog;
    protected boolean isNeedToScrollMessages;

    protected BaseChatHelper chatHelper;

    private int chatHelperIdentifier;

    public BaseDialogActivity(int layoutResID, int chatHelperIdentifier) {
        this.chatHelperIdentifier = chatHelperIdentifier;
        this.layoutResID = layoutResID;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(layoutResID);

        imageHelper = new ImageHelper(this);

        initUI();
        initListeners();
        initSmileWidgets();
        initSmiles();

        addActions();

        isNeedToScrollMessages = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        onUpdateChatDialog();
        hideSmileLayout();
    }

    public void initSmileWidgets() {
        chatEditText.setSmileClickListener(new OnSmileClickListener());
        chatEditText.setSwitchViewListener(this);
        FragmentStatePagerAdapter adapter = new SmilesTabFragmentAdapter(getSupportFragmentManager());
        smilesViewPager.setAdapter(adapter);
        smilesPagerIndicator.setViewPager(smilesViewPager);
        smilesAnimator = new HeightAnimator(chatEditText, smilesLayout);
    }

    protected void attachButtonOnClick() {
        canPerformLogout.set(false);
        imageHelper.getImage();
    }

    @Override
    public void showLastListItem() {
        if (isSmilesLayoutShowing()) {
            hideSmileLayout();
        }
    }

    private void hideSmileLayout() {
        hideView(smilesLayout);
        chatEditText.switchSmileIcon();
    }

    protected void addActions() {
        addAction(QBServiceConsts.LOAD_ATTACH_FILE_SUCCESS_ACTION, new LoadAttachFileSuccessAction());
        addAction(QBServiceConsts.LOAD_ATTACH_FILE_FAIL_ACTION, failAction);
        updateBroadcastActionList();
    }

    protected abstract void onUpdateChatDialog();

    protected Cursor getAllDialogMessagesByDialogId() {
        return DatabaseManager.getAllDialogMessagesByDialogId(this, dialogId);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        canPerformLogout.set(true);
        if (resultCode == RESULT_OK) {
            isNeedToScrollMessages = true;
            onFileSelected(data.getData());
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(smileSelectedBroadcastReceiver);
        if (chatHelper != null) {
            chatHelper.closeChat(dialog, generateBundleToInitDialog());
        }
        removeActions();
    }

    protected void removeActions() {
        removeAction(QBServiceConsts.LOAD_ATTACH_FILE_SUCCESS_ACTION);
        removeAction(QBServiceConsts.LOAD_ATTACH_FILE_FAIL_ACTION);
        removeAction(QBServiceConsts.LOAD_DIALOG_MESSAGES_SUCCESS_ACTION);
        removeAction(QBServiceConsts.LOAD_DIALOG_MESSAGES_FAIL_ACTION);
    }

    protected abstract void onFileSelected(Uri originalUri);

    protected void startLoadAttachFile(File file) {
        showProgress();
        QBLoadAttachFileCommand.start(this, file);
    }

    protected abstract void onFileLoaded(QBFile file);

    protected void startLoadDialogMessages(QBDialog dialog, long lastDateLoad) {
        if (dialog != null) {
            QBLoadDialogMessagesCommand.start(this, dialog, lastDateLoad);
        }
    }

    @Override
    protected void onConnectedToService(QBService service) {
        if (chatHelper == null) {
            chatHelper = (BaseChatHelper) service.getHelper(chatHelperIdentifier);
            chatHelper.createChatLocally(dialog, generateBundleToInitDialog());
        }
    }

    protected abstract Bundle generateBundleToInitDialog();

    private void initUI() {
        smilesLayout = findViewById(R.id.smiles_linearlayout);
        smilesPagerIndicator = (IconPageIndicator) findViewById(R.id.smiles_pager_indicator);
        smilesViewPager = (ViewPager) findViewById(R.id.smiles_viewpager);
        chatEditText = (ChatEditText) findViewById(R.id.message_edittext);
        messagesListView = (ListView) findViewById(R.id.messages_listview);
        messageEditText = _findViewById(R.id.message_edittext);
        sendButton = _findViewById(R.id.send_button);
        sendButton.setEnabled(false);
    }

    private void initListeners() {
        messageEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                super.onTextChanged(charSequence, start, before, count);
                if (TextUtils.isEmpty(charSequence) || TextUtils.isEmpty(charSequence.toString().trim())) {
                    sendButton.setEnabled(false);
                } else {
                    sendButton.setEnabled(true);
                }
            }
        });
    }

    private void initSmiles() {
        IntentFilter filter = new IntentFilter(QBServiceConsts.SMILE_SELECTED);
        smileSelectedBroadcastReceiver = new SmileSelectedBroadcastReceiver();
        registerReceiver(smileSelectedBroadcastReceiver, filter);
    }

    protected boolean isSmilesLayoutShowing() {
        return smilesLayout.getHeight() != Consts.ZERO_INT_VALUE;
    }

    private void hideView(View view) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        params.height = Consts.ZERO_INT_VALUE;
        view.setLayoutParams(params);
    }

    @Override
    public void onScrollToBottom() {
        scrollListView();
    }

    @Override
    protected void onReceiveMessage(Bundle extras) {
        String dialogId = extras.getString(QBServiceConsts.EXTRA_DIALOG_ID);
        boolean isFromCurrentChat = dialogId != null && dialogId.equals(this.dialogId);
        if (!isFromCurrentChat) {
            super.onReceiveMessage(extras);
        }
    }

    protected void scrollListView() {
        if (isNeedToScrollMessages) {
            isNeedToScrollMessages = false;
            messagesListView.setSelection(messagesAdapter.getCount() - 1);
        }
    }

    private int getSmileLayoutSizeInPixels() {
        return SizeUtility.dipToPixels(this, SMILES_SIZE_IN_DIPS);
    }

    public class LoadAttachFileSuccessAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            QBFile file = (QBFile) bundle.getSerializable(QBServiceConsts.EXTRA_ATTACH_FILE);
            onFileLoaded(file);
            hideProgress();
        }
    }

    private class SmileSelectedBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int resourceId = intent.getIntExtra(SerializableKeys.SMILE_ID, R.drawable.smile);
            int cursorPosition = chatEditText.getSelectionStart();
            String roundTrip;
            byte[] bytes = SmileysConvertor.getSymbolByResourceId(resourceId).getBytes(Charset.forName(
                    Consts.ENCODING_UTF8));
            roundTrip = new String(bytes, Charset.forName(Consts.ENCODING_UTF8));
            chatEditText.getText().insert(cursorPosition, roundTrip);
        }
    }

    private class OnSmileClickListener implements SmileClickListener {

        @Override
        public void onSmileClick() {
            int smilesLayoutHeight = getSmileLayoutSizeInPixels();
            if (isSmilesLayoutShowing()) {
                smilesAnimator.animateHeightFrom(smilesLayoutHeight, Consts.ZERO_INT_VALUE);
            } else {
                smilesAnimator.animateHeightFrom(Consts.ZERO_INT_VALUE, smilesLayoutHeight);
            }
        }
    }
}