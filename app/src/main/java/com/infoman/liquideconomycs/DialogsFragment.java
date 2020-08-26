package com.infoman.liquideconomycs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import org.bitcoinj.core.ECKey;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDialogFragment;

import static com.infoman.liquideconomycs.SyncServiceIntent.startActionSync;
import static com.infoman.liquideconomycs.TrieServiceIntent.startActionInsert;
import static com.infoman.liquideconomycs.Utils.ageToBytes;

public class DialogsFragment extends AppCompatDialogFragment {
    private int dialogCmd, cantFindPubKey = 0;
    private String  dialogHead, dialogMsg, dialogActivity;

    DialogsFragment(Context context, String activity, int cmd) {

        int findPubKey = 1;
        if(cmd==cantFindPubKey){
            dialogHead = context.getResources().getString(R.string.Attention);
            dialogMsg = context.getResources().getString(R.string.pubKeyNotFound);
        }else if(cmd== findPubKey){
            dialogHead = getResources().getString(R.string.Attention);
            dialogMsg = getResources().getString(R.string.pubKeyFound);
        }

        dialogCmd       = cmd;
        dialogActivity  = activity;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(dialogHead).setMessage(dialogMsg);

                //.setIcon(R.drawable.ic_launcher_cat)
                if(dialogCmd==cantFindPubKey) {
                    builder.setPositiveButton(getResources().getString(android.R.string.yes), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            if (dialogActivity.equals("MainActivity")) {

                                startActionSync(((MainActivity) getActivity()),
                                        "Main",
                                        "",
                                        Utils.hexToByte(Utils.parseQRString(((MainActivity) Objects.requireNonNull(getActivity())).resultTextView.getText().toString())[0]),
                                        "",
                                        true);

                                dialog.cancel();

                            }
                        }
                    })
                    .setNegativeButton(getResources().getString(android.R.string.no), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });
                }
        return builder.create();
    }
}
