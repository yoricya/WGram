package wgram;

import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;

import java.io.File;

public class Storage {
    private static final DispatchQueue queue = new DispatchQueue("WGramDeletedMessagesQueue");
    private static volatile SQLiteDatabase storageDb;

    static {
        queue.postRunnable(() -> {
            try {
                File filesDir = new File(ApplicationLoader.getFilesDirFixed(), "wgram.db");
                storageDb = new SQLiteDatabase(filesDir.getAbsolutePath());

                // Init table for deleted log
                storageDb.executeFast(
                        "CREATE TABLE IF NOT EXISTS del_m (" +
                                "account_id INTEGER," +
                                "dialog_id INTEGER," +
                                "message_id INTEGER," +
                                "PRIMARY KEY (account_id, dialog_id, message_id)" +
                                ");"
                ).stepThis().dispose();

                // Init table for tweak settings
                storageDb.executeFast(
                        "CREATE TABLE IF NOT EXISTS twk_set (" +
                                "tweak TEXT PRIMARY KEY," +
                                "bool_state BOOLEAN," +
                                "text_state TEXT" +
                                ");"

                ).stepThis().dispose();

                // maybe need
                storageDb.executeFast("PRAGMA journal_mode=WAL;").stepThis().dispose();
            } catch (Exception e) {
                AndroidUtilities.runOnUIThread(() -> {
                    FileLog.e("Wgram: Cannot init ROM db");
                    throw new RuntimeException("WGram: Failed to initialize ROM database. App cannot continue.", e);
                });
            }
        });
    }

    public static SQLiteDatabase getDb() throws IllegalThreadStateException {
        if (Thread.currentThread().getId() != Storage.getQueue().getId()) {
            throw new IllegalThreadStateException("WGram database is only allowed used in storage queue thread");
        }

        return storageDb;
    }

    public static DispatchQueue getQueue() {
        return queue;
    }
}
