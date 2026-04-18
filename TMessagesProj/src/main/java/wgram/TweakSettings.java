package wgram;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;

public class TweakSettings {
    public static volatile boolean KeepDeletedMessages = true;
    public static volatile boolean AllowScreenshots = true;
    public static volatile boolean ReadHide = true;
    public static volatile boolean TypingHide = true;

    // Get tweak setting from ROM
    static {
        Storage.getQueue().postRunnable(() -> {
            try {
                SQLiteCursor cursor = Storage.getDb().queryFinalized("SELECT tweak, bool_state FROM twk_set");
                while (cursor.next()) {
                    String tweak = cursor.stringValue(0);
                    boolean state = cursor.intValue(1) != 0;

                    switch (tweak) {
                        case "keepDeletedMessages":
                            KeepDeletedMessages = state;
                            break;
                        case "allowScreenShots":
                            AllowScreenshots = state;
                            break;
                        case "readHide":
                            ReadHide = state;
                            break;
                        case "typingHide":
                            TypingHide = state;
                            break;
                    }
                }
            } catch (Exception e) {
                AndroidUtilities.runOnUIThread(() -> {
                    FileLog.e("Wgram: Cannot init TweakSettings from ROM db");
                    throw new RuntimeException("WGram: Failed to initialize tweak settings from ROM database. App cannot continue.", e);
                });
            }
        });
    }

    public static Exception saveSettingsToRom() {
        if (Thread.currentThread().getId() != Storage.getQueue().getId()) {
            Storage.getQueue().postRunnable(() -> saveSettingsToRom());
            return null;
        }

        SQLitePreparedStatement st = null;
        try {
            SQLiteDatabase db = Storage.getDb();

            db.executeFast("BEGIN").stepThis().dispose();
            st = db.executeFast("REPLACE INTO twk_set (tweak, bool_state) VALUES (?, ?)");

            Object[][] settings = {
                    {"keepDeletedMessages", KeepDeletedMessages},
                    {"allowScreenShots", AllowScreenshots},
                    {"readHide", ReadHide},
                    {"typingHide", TypingHide}
            };

            for (Object[] setting : settings) {
                st.requery();
                st.bindString(1, (String) setting[0]);
                st.bindInteger(2, (Boolean) setting[1] ? 1 : 0);
                st.step();
            }

            db.executeFast("COMMIT").stepThis().dispose();
        } catch (Exception e) {
            FileLog.e("Wgram: Failed to save tweaks to ROM", e);
            return e;
        } finally {
            if (st != null) {
                st.dispose();
            }
        }

        return null;
    }
}
