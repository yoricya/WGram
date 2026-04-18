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
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import wgram.TweakSettings;

public class TweakSettingsActivity extends BaseFragment {

    @Override
    public View createView(Context context) {
        actionBar.setTitle("Tweak Settings");
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setOnClickListener(v -> finishFragment());

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(0, 0, 0, AndroidUtilities.dp(16));

        scrollView.addView(linearLayout, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Keep Deleted
        TextCheckCell keepDeletedCell = new TextCheckCell(context);
        keepDeletedCell.setTextAndCheck("Keep Deleted Messages", wgram.TweakSettings.KeepDeletedMessages, false);
        keepDeletedCell.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        keepDeletedCell.setOnClickListener(v -> {
            boolean newState = !keepDeletedCell.isChecked();
            keepDeletedCell.setChecked(newState);
            wgram.TweakSettings.KeepDeletedMessages = newState;
        });
        linearLayout.addView(keepDeletedCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Typing Hide
        TextCheckCell typingHideCell = new TextCheckCell(context);
        typingHideCell.setTextAndCheck("Typing Hide", wgram.TweakSettings.TypingHide, false);
        typingHideCell.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        typingHideCell.setOnClickListener(v -> {
            boolean newState = !typingHideCell.isChecked();
            typingHideCell.setChecked(newState);
            wgram.TweakSettings.TypingHide = newState;
        });
        linearLayout.addView(typingHideCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Allow Screenshots
        TextCheckCell allowScreenshotsCell = new TextCheckCell(context);
        allowScreenshotsCell.setTextAndCheck("Allow Screenshots", TweakSettings.AllowScreenshots, false);
        allowScreenshotsCell.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        allowScreenshotsCell.setOnClickListener(v -> {
            boolean newState = !allowScreenshotsCell.isChecked();
            allowScreenshotsCell.setChecked(newState);
            TweakSettings.AllowScreenshots = newState;
        });
        linearLayout.addView(allowScreenshotsCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));


        // Clear del-mesg button
        TextSettingsCell clearDbCell = new TextSettingsCell(context);
        clearDbCell.setText("Clear deleted messages", false);
        clearDbCell.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
        clearDbCell.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle("Clear deleted messages");
            builder.setMessage("Are you sure you want to clear all stored deleted messages?");

            // mediaNoRemove
            builder.setNeutralButton("Clear with mediaNoRemove", (d, w) -> {
                wgram.DeletedMessages.getQueue().postRunnable(() -> {
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
                wgram.DeletedMessages.getQueue().postRunnable(() -> {
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
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Canceled.").show();
            });

            // show
            showDialog(builder.create());
        });
        linearLayout.addView(clearDbCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Root FrameLayout
        FrameLayout rootView = new FrameLayout(context);
        rootView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        rootView.addView(scrollView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        fragmentView = rootView;
        return fragmentView;
    }
}