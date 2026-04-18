package wgram.activities;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.*;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import wgram.DeletedMessages;
import wgram.TweakSettings;
import wgram.WGram;

public class TweakSettingsActivity extends BaseFragment {

    LinearLayout linearLayout;
    @Override
    public View createView(Context context) {
        actionBar.setTitle("Tweak Settings");
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setOnClickListener(v -> finishFragment());

        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(0, 0, 0, AndroidUtilities.dp(16));

        // Setup views
        setupHeader(context);
        setupBody(context);

        // Setup root layouts
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        FrameLayout rootView = new FrameLayout(context);
        rootView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        rootView.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        fragmentView = rootView;
        return fragmentView;
    }

    public void setupHeader(Context context) {
        // WGram info text
        HeaderCell headerCell = new HeaderCell(context);
        headerCell.setText("WhitedGram v" + WGram.Ver);
        linearLayout.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // divider
        linearLayout.addView(new ShadowSectionCell(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    public void setupBody(Context context){
        addBodySwitch(context, "Keep Deleted Messages", TweakSettings.KeepDeletedMessages, true, (v) -> {
            boolean newState = !((TextCheckCell) v).isChecked();
            ((TextCheckCell) v).setChecked(newState);
            TweakSettings.KeepDeletedMessages = newState;
        });

        addBodySwitch(context, "Typing Hide", TweakSettings.TypingHide, true, (v) -> {
            boolean newState = !((TextCheckCell) v).isChecked();
            ((TextCheckCell) v).setChecked(newState);
            TweakSettings.TypingHide = newState;
        });

        addBodySwitch(context, "Allow Screenshots", TweakSettings.AllowScreenshots, false, (v) -> {
            boolean newState = !((TextCheckCell) v).isChecked();
            ((TextCheckCell) v).setChecked(newState);
            TweakSettings.AllowScreenshots = newState;
        });

        // Clear deleted messages button
        TextSettingsCell clearDbCell = new TextSettingsCell(context);
        clearDbCell.setText("Clear deleted messages", false);
        clearDbCell.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
        clearDbCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        clearDbCell.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle("Clear deleted messages");
            builder.setMessage("Are you sure you want to clear all stored deleted messages?");

            // mediaNoRemove
            builder.setNeutralButton("Clear with mediaNoRemove", (d, w) -> {
                DeletedMessages.getQueue().postRunnable(() -> {
                    Exception e = wgram.DeletedMessages.clearDeletedMessages(true);
                    AndroidUtilities.runOnUIThread(() -> {
                        int flag = R.raw.contact_check;
                        String str = "Messages cleared.";

                        if (e != null) {
                            flag = R.raw.error;
                            str = "Exception thrown while clear messages.";
                        }

                        BulletinFactory.of(this).createSimpleBulletin(flag, str).show();
                    });
                });
            });

            // clear all
            builder.setPositiveButton("Clear", (dialog, w) -> {
                DeletedMessages.getQueue().postRunnable(() -> {
                    Exception e = wgram.DeletedMessages.clearDeletedMessages(false);
                    AndroidUtilities.runOnUIThread(() -> {
                        int flag = R.raw.contact_check;
                        String str = "Messages cleared";

                        if (e != null) {
                            flag = R.raw.error;
                            str = "Exception thrown while clear messages.";
                        }

                        BulletinFactory.of(this).createSimpleBulletin(flag, str).show();
                    });
                });
            });

            // cancel
            builder.setNegativeButton("Cancel", (dialog, w) -> {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.hand_1, "Canceled.").show();
            });

            // show
            showDialog(builder.create());
        });

        linearLayout.addView(clearDbCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    public void addBodySwitch(Context context, String name, boolean isChecked, boolean divider, View.OnClickListener listener) {
        TextCheckCell cell = new TextCheckCell(context);
        cell.setTextAndCheck(name, isChecked, divider);
        cell.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        cell.setOnClickListener(listener);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }
}