package com.quickblox.qmunicate.qb.commands;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.quickblox.module.content.model.QBFile;
import com.quickblox.qmunicate.core.command.ServiceCommand;
import com.quickblox.qmunicate.qb.helpers.QBPrivateChatHelper;
import com.quickblox.qmunicate.service.QBService;
import com.quickblox.qmunicate.service.QBServiceConsts;

import java.io.File;

public class QBLoadAttachFileCommand extends ServiceCommand {

    private static final String TAG = QBLoadAttachFileCommand.class.getSimpleName();

    private final QBPrivateChatHelper privateChatHelper;

    public QBLoadAttachFileCommand(Context context, QBPrivateChatHelper privateChatHelper,
            String successAction, String failAction) {
        super(context, successAction, failAction);
        this.privateChatHelper = privateChatHelper;
    }

    public static void start(Context context, File file) {
        Intent intent = new Intent(QBServiceConsts.LOAD_ATTACH_FILE_ACTION, null, context, QBService.class);
        intent.putExtra(QBServiceConsts.EXTRA_FILE, file);
        context.startService(intent);
    }

    @Override
    protected Bundle perform(Bundle extras) throws Exception {
        File file = (File) extras.getSerializable(QBServiceConsts.EXTRA_FILE);

        QBFile qbFile = privateChatHelper.loadAttachFile(file);

        Bundle result = new Bundle();
        result.putSerializable(QBServiceConsts.EXTRA_ATTACH_FILE, qbFile);

        return result;
    }
}