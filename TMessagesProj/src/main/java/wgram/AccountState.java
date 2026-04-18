package wgram;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import java.util.concurrent.atomic.AtomicLong;

public class AccountState implements NotificationCenter.NotificationCenterDelegate {
    public static final AccountState instance = init();
    public final AtomicLong currentAccountId = new AtomicLong();

    public static AccountState init() {
        AccountState ins = new AccountState();
        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getGlobalInstance().addObserver(ins, NotificationCenter.mainUserInfoChanged);
            updateId();
        });
        return ins;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.mainUserInfoChanged) {
            updateId();
        }
    }

    private static void updateId() {
        int selected = UserConfig.selectedAccount;
        long uid = AccountInstance.getInstance(selected).getUserConfig().clientUserId;
        instance.currentAccountId.set(uid);
    }
}