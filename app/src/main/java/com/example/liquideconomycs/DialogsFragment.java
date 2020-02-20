package com.example.liquideconomycs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDialogFragment;

public class DialogsFragment extends AppCompatDialogFragment {
    String dialogHead;
    String dialogMsg;
    public DialogsFragment(String head, String msg) {
        dialogHead = head;
        dialogMsg = msg;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(dialogHead)
                .setMessage(dialogMsg)
                //.setIcon(R.drawable.ic_launcher_cat)
                .setPositiveButton(getResources().getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        ((ScanerActivity) getActivity()).okPubKeyNotFoundClicked();
                        dialog.cancel();
                    }
                })
                .setNegativeButton(getResources().getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        ((ScanerActivity) getActivity()).onResume();
                        dialog.cancel();
                    }
                });
        return builder.create();
    }
}
