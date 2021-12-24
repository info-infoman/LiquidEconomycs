package com.infoman.liquideconomycs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDialogFragment;

public class DialogsFragment extends AppCompatDialogFragment {
    private final int dialogCmd;
    private final int cantFindPubKey = 0;
    private String  dialogHead;
    private String dialogMsg;
    private final String dialogActivity;
    private Core app;

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
        app = (Core) getActivity().getApplicationContext();
        AlertDialog.Builder builder = new AlertDialog.Builder(Objects.requireNonNull(getActivity()));
        builder.setTitle(dialogHead).setMessage(dialogMsg);

                //.setIcon(R.drawable.ic_launcher_cat)
                if(dialogCmd==cantFindPubKey) {
                    builder.setPositiveButton(getResources().getString(android.R.string.yes), (dialog, id) -> {
                        if (dialogActivity.equals("MainActivity")) {
                            app.startActionSync(getActivity(),
                                    "Main",
                                    "",
                                    Utils.hexToByte(Utils.parseQRString(((MainActivity) Objects.requireNonNull(getActivity())).resultTextView.getText().toString())[0]),
                                    "",
                                    true);
                            app.startActionStopSync(getActivity());
                            dialog.cancel();
                        }
                    }).setNegativeButton(getResources().getString(android.R.string.no), (dialog, id) -> dialog.cancel());
                }
        return builder.create();
    }
}
