package com.google.ar.core.examples.java.cloudanchor;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.google.common.base.Preconditions;
import com.hootsuite.nachos.NachoTextView;

import java.util.ArrayList;

public class PromptAnchorData extends DialogFragment {
    interface OkListener {
        /**
         * This method is called by the dialog box when its OK button is pressed.
         *
         * @param anchorName       the anchor name
         * @param connectedAnchors the connected anchor names
         */


        void onOkPressed(String anchorName, ArrayList<String> connectedAnchors);
    }

    private EditText editText;
    private NachoTextView nachoTextView;

    private PromptAnchorData.OkListener okListener;

    public void setOkListener(PromptAnchorData.OkListener okListener) {
        this.okListener = okListener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FragmentActivity activity =
                Preconditions.checkNotNull(getActivity(), "The activity cannot be null.");
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        View dialogView = activity.getLayoutInflater().inflate(R.layout.activity_prompt_anchor_data, null);

        ArrayList<String> anchorNames = getArguments().getStringArrayList("anchorNames");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_dropdown_item_1line, anchorNames);
        nachoTextView = dialogView.findViewById(R.id.nacho_text_view);
        nachoTextView.setAdapter(adapter);


        editText = dialogView.findViewById(R.id.plain_text_input);

        builder
                .setView(dialogView)
                .setTitle(R.string.resolve_dialog_title)
                .setPositiveButton(
                        R.string.resolve_dialog_ok,
                        (dialog, which) -> {
                            String anchorName = editText.getText().toString();
                            if (okListener != null && anchorName != null && anchorName.length() > 0) {
                                ArrayList<String> connectedAnchors = new ArrayList<>();
                                connectedAnchors.addAll(nachoTextView.getChipValues());
                                okListener.onOkPressed(anchorName, connectedAnchors);
                            }
                        })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                });
        return builder.create();
    }
}