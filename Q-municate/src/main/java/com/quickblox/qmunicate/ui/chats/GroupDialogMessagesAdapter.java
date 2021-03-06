package com.quickblox.qmunicate.ui.chats;

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.quickblox.qmunicate.R;
import com.quickblox.qmunicate.caching.DatabaseManager;
import com.quickblox.qmunicate.caching.tables.DialogMessageTable;
import com.quickblox.qmunicate.model.Friend;
import com.quickblox.qmunicate.qb.commands.QBUpdateStatusMessageCommand;
import com.quickblox.qmunicate.ui.views.RoundedImageView;
import com.quickblox.qmunicate.ui.views.smiles.ChatTextView;
import com.quickblox.qmunicate.utils.Consts;
import com.quickblox.qmunicate.utils.DateUtils;

public class GroupDialogMessagesAdapter extends BaseDialogMessagesAdapter {

    public GroupDialogMessagesAdapter(Context context, Cursor cursor,
                                      ScrollMessagesListener scrollMessagesListener) {
        super(context, cursor);
        this.scrollMessagesListener = scrollMessagesListener;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view;
        ViewHolder viewHolder = new ViewHolder();

        int senderId = cursor.getInt(cursor.getColumnIndex(DialogMessageTable.Cols.SENDER_ID));
        if (isOwnMessage(senderId)) {
            view = layoutInflater.inflate(R.layout.list_item_dialog_own_message, null, true);
        } else {
            view = layoutInflater.inflate(R.layout.list_item_group_dialog_opponent_message, null, true);
            viewHolder.avatarImageView = (RoundedImageView) view.findViewById(R.id.avatar_imageview);
            viewHolder.avatarImageView.setOval(true);
            viewHolder.avatarImageView.setVisibility(View.VISIBLE);
            viewHolder.nameTextView = (TextView) view.findViewById(R.id.name_textview);
            viewHolder.nameTextView.setVisibility(View.VISIBLE);
        }

        viewHolder.attachMessageRelativeLayout = (RelativeLayout) view.findViewById(R.id.attach_message_relativelayout);
        viewHolder.timeAttachMessageTextView = (TextView) view.findViewById(R.id.time_attach_message_textview);
        viewHolder.progressRelativeLayout = (RelativeLayout) view.findViewById(R.id.progress_relativelayout);
        viewHolder.textMessageLinearLayout = (LinearLayout) view.findViewById(R.id.text_message_linearlayout);
        viewHolder.messageTextView = (ChatTextView) view.findViewById(R.id.message_textview);
        viewHolder.attachImageView = (ImageView) view.findViewById(R.id.attach_imageview);
        viewHolder.timeTextMessageTextView = (TextView) view.findViewById(R.id.time_text_message_textview);
        viewHolder.verticalProgressBar = (ProgressBar) view.findViewById(R.id.vertical_progressbar);
        viewHolder.verticalProgressBar.setProgressDrawable(context.getResources().getDrawable(R.drawable.vertical_progressbar));
        viewHolder.centeredProgressBar =  (ProgressBar) view.findViewById(R.id.centered_progressbar);

        view.setTag(viewHolder);

        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        ViewHolder viewHolder = (ViewHolder) view.getTag();

        String avatarUrl = null;
        String senderName;

        String body = cursor.getString(cursor.getColumnIndex(DialogMessageTable.Cols.BODY));
        String attachUrl = cursor.getString(cursor.getColumnIndex(DialogMessageTable.Cols.ATTACH_FILE_ID));
        int senderId = cursor.getInt(cursor.getColumnIndex(DialogMessageTable.Cols.SENDER_ID));
        long time = cursor.getLong(cursor.getColumnIndex(DialogMessageTable.Cols.TIME));
        boolean isOwnMessage = isOwnMessage(senderId);

        viewHolder.attachMessageRelativeLayout.setVisibility(View.GONE);

        if (isOwnMessage) {
            avatarUrl = getAvatarUrlForCurrentUser();
        } else {
            Friend senderFriend = DatabaseManager.getFriendById(context, senderId);
            if (senderFriend != null) {
                senderName = senderFriend.getFullname();
                avatarUrl = getAvatarUrlForFriend(senderFriend);
            } else {
                senderName = senderId + Consts.EMPTY_STRING;
            }
            viewHolder.nameTextView.setTextColor(getTextColor(senderId));
            viewHolder.nameTextView.setText(senderName);
        }

        if (!TextUtils.isEmpty(attachUrl)) {
            viewHolder.timeAttachMessageTextView.setText(DateUtils.longToMessageDate(time));
            viewHolder.textMessageLinearLayout.setVisibility(View.GONE);
            viewHolder.progressRelativeLayout.setVisibility(View.VISIBLE);
            displayAttachImage(attachUrl, viewHolder.attachImageView, viewHolder.progressRelativeLayout,
                    viewHolder.attachMessageRelativeLayout, viewHolder.verticalProgressBar,
                    viewHolder.centeredProgressBar, isOwnMessage);
        } else {
            viewHolder.timeTextMessageTextView.setText(DateUtils.longToMessageDate(time));
            viewHolder.textMessageLinearLayout.setVisibility(View.VISIBLE);
            viewHolder.attachMessageRelativeLayout.setVisibility(View.GONE);
            viewHolder.messageTextView.setText(body);
        }

        boolean isRead = cursor.getInt(cursor.getColumnIndex(
                DialogMessageTable.Cols.IS_READ)) > Consts.ZERO_INT_VALUE;
        if (!isRead) {
            String messageId = cursor.getString(cursor.getColumnIndex(DialogMessageTable.Cols.ID));
            QBUpdateStatusMessageCommand.start(context, messageId, true);
        }

        displayAvatarImage(avatarUrl, viewHolder.avatarImageView);
    }

    private static class ViewHolder {

        RelativeLayout progressRelativeLayout;
        RelativeLayout attachMessageRelativeLayout;
        RoundedImageView avatarImageView;
        TextView nameTextView;
        LinearLayout textMessageLinearLayout;
        ChatTextView messageTextView;
        ImageView attachImageView;
        TextView timeTextMessageTextView;
        TextView timeAttachMessageTextView;
        ProgressBar verticalProgressBar;
        ProgressBar centeredProgressBar;
    }
}