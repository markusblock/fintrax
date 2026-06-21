package org.fintrax.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.fintx.BankingProtocol;
import org.fintrax.fintx.FinTsAdapter;
import org.fintrax.fintx.PinStorage;
import org.fintrax.service.hibiscus.HibiscusXmlImporter;
import org.fintrax.store.StoragePathResolver;
import org.fintrax.store.StoreManager;

import java.nio.file.Path;

@Slf4j
public class ServiceRegistry {
    @Getter
    private static ServiceRegistry instance;

    @Getter
    private final StoreManager storeManager;
    @Getter
    private final ActivityLogger activityLogger;
    @Getter
    private final AccountService accountService;
    @Getter
    private final TransactionService transactionService;
    @Getter
    private final CategoryService categoryService;
    @Getter
    private final LabelService labelService;
    @Getter
    private final RuleEngine ruleEngine;
    @Getter
    private final RuleService ruleService;
    @Getter
    private final SyncService syncService;
    @Getter
    private final BankingProtocol bankingProtocol;
    @Getter
    private final PinStorage pinStorage;
    @Getter
    private final HibiscusXmlImporter hibiscusXmlImporter;

    public ServiceRegistry() {
        log.info("Initializing ServiceRegistry");
        Path storagePath = StoragePathResolver.resolve();
        storeManager = new StoreManager(storagePath);
        activityLogger = new ActivityLogger(storeManager);
        bankingProtocol = new FinTsAdapter();
        pinStorage = new PinStorage(storagePath);

        accountService = new AccountService(storeManager, activityLogger);
        transactionService = new TransactionService(storeManager, activityLogger);
        categoryService = new CategoryService(storeManager, activityLogger);
        labelService = new LabelService(storeManager, activityLogger);
        ruleEngine = new RuleEngine(storeManager, activityLogger);
        ruleService = new RuleService(storeManager, activityLogger, ruleEngine);
        syncService = new SyncService(storeManager, bankingProtocol, transactionService,
                accountService, activityLogger, pinStorage, ruleEngine);
        hibiscusXmlImporter = new HibiscusXmlImporter(storeManager);

        log.info("ServiceRegistry initialized with {} accounts, {} transactions, {} categories",
                storeManager.getRoot().getAccounts().size(),
                storeManager.getRoot().getTransactions().size(),
                storeManager.getRoot().getCategories().size());
    }

    public static void initialize() {
        if (instance == null) {
            instance = new ServiceRegistry();
        }
    }

    public static void shutdown() {
        if (instance != null) {
            log.info("Shutting down ServiceRegistry");
            instance.storeManager.shutdown();
            instance = null;
        }
    }
}
