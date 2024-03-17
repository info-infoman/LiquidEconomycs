package com.infoman.liquideconomycs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDialogFragment;

public class DialogsFragment extends AppCompatDialogFragment {
    private String  dialogHead;
    private String dialogMsg;
    private final String dialogActivity;
    private Core app;

    DialogsFragment(Context context, String activity, int cmd) {
        dialogHead = context.getResources().getString(R.string.Attention);
        dialogMsg = context.getResources().getString(R.string.pubKeyNotFound);
        dialogActivity  = activity;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        app = (Core) getActivity().getApplicationContext();
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setTitle(dialogHead).setMessage(dialogMsg);
                builder.setPositiveButton(getResources().getString(android.R.string.ok), (dialog, id) -> {
                    if (dialogActivity.equals("MainActivity")) {
                        dialog.cancel();
                    }
                });
        return builder.create();
    }
}
