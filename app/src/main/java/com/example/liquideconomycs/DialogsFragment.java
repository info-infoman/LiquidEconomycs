package com.example.liquideconomycs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDialogFragment;

import static com.example.liquideconomycs.SyncServiceIntent.startActionSync;

public class DialogsFragment extends AppCompatDialogFragment {
    int dialogCmd;
    String  dialogHead;
    String dialogMsg;
    String dialogActivity;

    int cantFindPubKey = 0;
    int findPubKey = 1;

    public DialogsFragment(String activity, int cmd) {

        if(cmd==cantFindPubKey){
            dialogHead = getResources().getString(R.string.Attention);
            dialogMsg = getResources().getString(R.string.pubKeyNotFound);
        }else if(cmd==findPubKey){
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
                                startActionSync(((MainActivity) getActivity()), "Main", "", Utils.hexToByte(((MainActivity) getActivity()).resultTextView.getText().toString()), "", true);
                                ((MainActivity) getActivity()).redyToNextScan = true;
                                dialog.cancel();
                            }
                        }
                    })
                    .setNegativeButton(getResources().getString(android.R.string.no), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            ((MainActivity) getActivity()).redyToNextScan = true;
                            dialog.cancel();
                        }
                    });
                }
        return builder.create();
    }
}
