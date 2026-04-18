package wgram;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;

public class DeletedMessages {
    // ROM cache block
    public static boolean RomCheckIsMarked(MessageObject object) {
        if (Thread.currentThread().getId() != Storage.getQueue().getId()) {
            FutureTask<Boolean> task = new FutureTask<>(() -> RomCheckIsMarked(object));
            Storage.getQueue().postRunnable(task);
            try {
                return task.get();
            } catch (Exception e) {
                FileLog.e("Wgram: Failed to get value from future task", e);
                return false;
            }
        }

        SQLiteCursor cursor = null;
        try {
            cursor = Storage.getDb().queryFinalized(
                    "SELECT 1 FROM del_m WHERE account_id = ? AND dialog_id = ? AND message_id = ? LIMIT 1",
                    AccountState.instance.currentAccountId.get(),
                    object.getDialogId(),
                    object.getId()
            );

            return cursor.next();
        } catch (Exception e) {
            FileLog.e("Wgram: Failed to select from ROM db", e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }

        return false;
    }

    public static void asyncRomCheckMarkRequest(MessageObject object) {
        long did = object.getDialogId();
        int mid = object.getId();

        Storage.getQueue().postRunnable(() -> {
            // Lookup ROM
            boolean mark = RomCheckIsMarked(object);

            // Modify RAM record
            ConcurrentHashMap<Integer, MessageRecord> dialogRecords = map.computeIfAbsent(did, k -> new ConcurrentHashMap<>());
            MessageRecord record = dialogRecords.computeIfAbsent(mid, k -> new MessageRecord(mark));
            if (record.isMarkedAsDeleted != mark) {
                record.isMarkedAsDeleted = mark;
            }

            ArrayList<Integer> arrayList = new ArrayList<>();
            arrayList.add(mid);

            // Send UI callback
            AndroidUtilities.runOnUIThread(() -> {
                AccountInstance.getInstance(UserConfig.selectedAccount).
                        getNotificationCenter().postNotificationName(NotificationCenter.wgramMessagesMarkAsDeleted, arrayList, -did, false);
            });
        });
    }

    public static void RomMark(long dialogId, int messageId) {
        if (Thread.currentThread().getId() != Storage.getQueue().getId()) {
            Storage.getQueue().postRunnable(() -> RomMark(dialogId, messageId));
            return;
        }

        try {
            SQLitePreparedStatement state = Storage.getDb().executeFast(
                    "INSERT OR REPLACE INTO del_m (account_id, dialog_id, message_id) VALUES (?, ?, ?)"
            );
            state.bindLong(1, AccountState.instance.currentAccountId.get());
            state.bindLong(2, dialogId);
            state.bindInteger(3, messageId);
            state.step();
            state.dispose();

            if (BuildVars.DEBUG_VERSION) {
                FileLog.d("Wgram: Saved to ROM: " + dialogId + " msg=" + messageId);
            }
        } catch (Exception e) {
            FileLog.e("Wgram: Failed to mark in ROM db", e);
        }
    }

    // RAM Cache block
    public static class MessageRecord {
        public volatile boolean isMarkedAsDeleted;

        public MessageRecord(boolean isMarked) {
            isMarkedAsDeleted = isMarked;
        }
    }

    private static final ConcurrentHashMap<Long, ConcurrentHashMap<Integer, MessageRecord>> map = new ConcurrentHashMap<>();
    private static final Set<Integer> zeroDiaMessages = ConcurrentHashMap.newKeySet();

    public static void mark(long chatId, int messageId) {
        if (BuildVars.DEBUG_VERSION) {
            FileLog.d("WGram: MARK: chat=" + chatId + " msg=" + messageId);
        }

        if (chatId == 0) {
            zeroDiaMessages.add(messageId);
        } else {
            RomMark(chatId, messageId);
            map.computeIfAbsent(chatId, k -> new ConcurrentHashMap<>())
                    .put(messageId, new MessageRecord(true));
        }
    }

    public static boolean isMarked(MessageObject object) {
        long did = object.getDialogId();
        int mid = object.getId();

        if (BuildVars.DEBUG_VERSION) {
            FileLog.d("WGram: mark check: did=" + did + " mid=" + mid);
        }

        // Lookup zeroDia
        if (!object.isFromChannel() && !object.isFromGroup() && zeroDiaMessages.remove(mid)) {
            if (BuildVars.DEBUG_VERSION) {
                FileLog.d("WGram: mid found in zeroDiaMessages: did=" + did + " mid=" + mid);
            }
            RomMark(did, mid);
            map.computeIfAbsent(did, k -> new ConcurrentHashMap<>())
                    .put(mid, new MessageRecord(true));
            object.wgramMarkAsDeleted = true;
            return true;
        }

        // Lookup RAM
        ConcurrentHashMap<Integer, MessageRecord> dialogRecords = map.computeIfAbsent(did, k -> new ConcurrentHashMap<>());
        MessageRecord record = dialogRecords.computeIfAbsent(mid, k -> {
            // Request to lookup ROM
            asyncRomCheckMarkRequest(object);
            return new MessageRecord(false);
        });

        // Check messageObject marker
        if (object.wgramMarkAsDeleted) {
            if (BuildVars.DEBUG_VERSION) {
                FileLog.d("WGram: MessageObject already bool marked: did=" + did + " mid=" + mid);
            }
            record.isMarkedAsDeleted = true;
            return true;
        }

        // Check record marker
        if (record.isMarkedAsDeleted) {
            object.wgramMarkAsDeleted = true;
            return true;
        }

        return false;
    }

    public static Exception clearSpecificDeletedMessage(long didOrCid, int messageId, boolean deleteOnOriginStorage) {
        if (Thread.currentThread().getId() != Storage.getQueue().getId()) {
            Storage.getQueue().postRunnable(() -> clearSpecificDeletedMessage(didOrCid, messageId, deleteOnOriginStorage));
            return null;
        }

        if (BuildVars.DEBUG_VERSION) {
            FileLog.d("WGram: Clear specific. did=" + didOrCid + " mid="+messageId+" delOnOrigin"+deleteOnOriginStorage);
        }

        AccountInstance instance = AccountInstance.getInstance(UserConfig.selectedAccount);

        try {
            // Remove form TG storage
            if (deleteOnOriginStorage) {
                ArrayList<Integer> messagesMap = new ArrayList<>();
                messagesMap.add(messageId);

                instance.getMessagesStorage().getStorageQueue().postRunnable(() -> {
                    ArrayList<Long> dialogIds = instance.getMessagesStorage().markMessagesAsDeleted(didOrCid, messagesMap, false, true, 0, 0);
                    if (BuildVars.DEBUG_VERSION && dialogIds != null) {
                        FileLog.d("WGram: Clear specific. did=" + didOrCid + " dids=" + Arrays.toString(dialogIds.toArray()));
                    } else if (BuildVars.DEBUG_VERSION) {
                        FileLog.d("WGram: Clear specific. did=" + didOrCid + " dids=null");
                    }
                    instance.getMessagesStorage().updateDialogsWithDeletedMessages(didOrCid, -didOrCid, messagesMap, dialogIds, false);
                });
            }

            SQLiteDatabase databaseStorage = Storage.getDb();

            // Remove from WGram ROM storage
            databaseStorage.executeFast("BEGIN").stepThis().dispose();
            SQLitePreparedStatement st = databaseStorage.executeFast("DELETE FROM del_m WHERE account_id=? AND dialog_id=? AND message_id=?");
            st.bindLong(1, instance.getUserConfig().clientUserId);
            st.bindLong(2, didOrCid);
            st.bindInteger(3, messageId);
            st.step();
            st.dispose();
            databaseStorage.executeFast("COMMIT").stepThis().dispose();

            // Remove from WGram RAM cache
            ConcurrentHashMap<Integer, MessageRecord> records = map.get(didOrCid);
            if (records != null) {
                records.remove(messageId);
            }
        } catch (Exception e) {
            FileLog.e("WGram: clear specific deleted message failed", e);
            return e;
        }

        return null;
    }

    public static Exception clearDeletedMessagesOnChat(long didOrCid, boolean deleteOnOriginStorage) {
        if (Thread.currentThread().getId() != Storage.getQueue().getId()) {
            Storage.getQueue().postRunnable(() -> clearDeletedMessagesOnChat(didOrCid, deleteOnOriginStorage));
            return null;
        }

        if (BuildVars.DEBUG_VERSION) {
            FileLog.d("WGram: Clear chat. did=" + didOrCid + " delOnOrigin=" + deleteOnOriginStorage);
        }

        AccountInstance instance = AccountInstance.getInstance(UserConfig.selectedAccount);

        SQLiteCursor cursor = null;
        try {
            SQLiteDatabase databaseStorage = Storage.getDb();

            cursor = databaseStorage.queryFinalized(
                    "SELECT message_id FROM del_m WHERE account_id=? AND dialog_id=?",
                    instance.getUserConfig().clientUserId, didOrCid
            );

            // Parse messages from db
            ArrayList<Integer> messagesList = new ArrayList<>();
            while (cursor.next()) {
                int mid = cursor.intValue(0);
                messagesList.add(mid);
            }

            cursor.dispose();

            // Remove form TG storage
            if (deleteOnOriginStorage && !messagesList.isEmpty()) {
                instance.getMessagesStorage().getStorageQueue().postRunnable(() -> {
                    ArrayList<Long> dialogIds = instance.getMessagesStorage().markMessagesAsDeleted(didOrCid, messagesList, false, true, 0, 0);
                    if (BuildVars.DEBUG_VERSION && dialogIds == null) {
                        FileLog.d("WGram: Clear on chat. did=" + didOrCid + " dids=null");
                    } else if (BuildVars.DEBUG_VERSION) {
                        FileLog.d("WGram: Clear on chat. did=" + didOrCid + " dids=" + Arrays.toString(dialogIds.toArray()));
                    }
                    instance.getMessagesStorage().updateDialogsWithDeletedMessages(didOrCid, -didOrCid, messagesList, dialogIds, false);
                });
            }

            // Remove from WGram ROM storage
            databaseStorage.executeFast("BEGIN").stepThis().dispose();
            SQLitePreparedStatement st = databaseStorage.executeFast("DELETE FROM del_m WHERE account_id=? AND dialog_id=?");
            st.bindLong(1, instance.getUserConfig().clientUserId);
            st.bindLong(2, didOrCid);
            st.step();
            st.dispose();
            databaseStorage.executeFast("COMMIT").stepThis().dispose();

            // Remove from WGram RAM cache
            map.remove(didOrCid);
        } catch (Exception e) {
            FileLog.e("WGram: clear deleted messages on chat failed", e);
            return e;
        } finally {
            if (cursor != null) cursor.dispose();
        }

        return null;
    }

    public static Exception clearDeletedMessages(boolean mediaNoRemove) {
        if (Thread.currentThread().getId() != Storage.getQueue().getId()) {
            Storage.getQueue().postRunnable(() -> clearDeletedMessages(mediaNoRemove));
            return null;
        }

        AccountInstance instance = AccountInstance.getInstance(UserConfig.selectedAccount);

        SQLiteCursor cursor = null;
        try {
            SQLiteDatabase databaseStorage = Storage.getDb();

            cursor = databaseStorage.queryFinalized(
                    "SELECT dialog_id, message_id FROM del_m WHERE account_id=?",
                    instance.getUserConfig().clientUserId
            );

            // Parse messages from db
            HashMap<Long, ArrayList<Integer>> messagesMap = new HashMap<>();
            while (cursor.next()) {
                long did = cursor.longValue(0);
                int mid = cursor.intValue(1);

                // Get TL message
                TLRPC.Message m = instance.getMessagesStorage().getMessage(did, mid);

                // No media -> always add
                if (!MessageObject.isMediaEmpty(m) && mediaNoRemove) {
                    continue;
                }

                if (m != null) {
                    messagesMap.computeIfAbsent(did, (k) -> new ArrayList<>())
                            .add(mid);
                }
            }

            cursor.dispose();

            // Delete messages
            for (Map.Entry<Long, ArrayList<Integer>> entry: messagesMap.entrySet()) {
                // Remove form TG storage
                instance.getMessagesStorage().getStorageQueue().postRunnable(() -> {
                    ArrayList<Long> dialogIds = instance.getMessagesStorage().markMessagesAsDeleted(entry.getKey(), entry.getValue(), false, true, 0, 0);
                    if (BuildVars.DEBUG_VERSION) {
                        FileLog.d("WGram: Clear. did=" + entry.getKey() + " dids=" + Arrays.toString(dialogIds.toArray()));
                    }
                    instance.getMessagesStorage().updateDialogsWithDeletedMessages(entry.getKey(), -entry.getKey(), entry.getValue(), dialogIds, false);
                });

                // Remove from WGram ROM storage
                databaseStorage.executeFast("BEGIN").stepThis().dispose();
                SQLitePreparedStatement st = databaseStorage.executeFast("DELETE FROM del_m WHERE account_id=? AND dialog_id=? AND message_id=?");
                for (int mid: entry.getValue()) {
                    st.requery();
                    st.bindLong(1, instance.getUserConfig().clientUserId);
                    st.bindLong(2, entry.getKey());
                    st.bindInteger(3, mid);
                    st.step();
                }
                st.dispose();
                databaseStorage.executeFast("COMMIT").stepThis().dispose();

                // Remove from WGram RAM cache
                ConcurrentHashMap<Integer, MessageRecord> records = map.get(entry.getKey());
                if (records == null) {
                    continue;
                }

                for (int mid: entry.getValue()) {
                    records.remove(mid);
                }
            }

            // Remove from db
        } catch (Exception e) {
            FileLog.e("WGram: clear deleted messages failed", e);
            return e;
        } finally {
            if (cursor != null) cursor.dispose();
        }
        return null;
    }
}