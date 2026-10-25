package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * The System folder, like a phone's app folder: the preinstalled apps in a rounded panel over the
 * home screen, four to a row, and nothing else. OK opens an app; Back closes the folder.
 */
final class SystemFolder {

    private static final int COLUMNS = 4;

    private SystemFolder() {
    }

    static void show(Activity activity, String title, List<AppAdapter.Entry> apps) {
        View panel = LayoutInflater.from(activity).inflate(R.layout.dialog_folder, null, false);
        ((TextView) panel.findViewById(R.id.folderTitle)).setText(title);
        RecyclerView list = panel.findViewById(R.id.folderApps);
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(0.55f);
        }
        list.setLayoutManager(new GridLayoutManager(activity, COLUMNS));
        list.setAdapter(new Adapter(activity, apps, dialog));
        dialog.show();
        list.requestFocus();
    }

    private static final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        private final Activity activity;
        private final List<AppAdapter.Entry> apps;
        private final Dialog dialog;

        Adapter(Activity activity, List<AppAdapter.Entry> apps, Dialog dialog) {
            this.activity = activity;
            this.apps = apps;
            this.dialog = dialog;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_folder_app, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            AppAdapter.Entry e = apps.get(position);
            h.icon.setImageDrawable(e.icon);
            h.label.setText(e.label);
            h.itemView.setOnClickListener(v -> {
                dialog.dismiss();
                AppAdapter.launchEntry(activity, e);
            });
            // The focused app grows a little and its name brightens: no highlight box.
            h.itemView.setOnFocusChangeListener((v, focused) -> {
                v.animate().scaleX(focused ? 1.1f : 1f).scaleY(focused ? 1.1f : 1f).setDuration(160).start();
                h.label.setAlpha(focused ? 1f : 0.7f);
            });
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView label;

            Holder(View v) {
                super(v);
                icon = v.findViewById(R.id.icon);
                label = v.findViewById(R.id.label);
            }
        }
    }
}
