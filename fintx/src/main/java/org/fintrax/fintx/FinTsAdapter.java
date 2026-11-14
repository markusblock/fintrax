package org.fintrax.fintx;

import lombok.extern.slf4j.Slf4j;
import org.fintrax.model.Bank;
import org.fintrax.model.BankAccount;
import org.fintrax.model.SyncStage;
import org.fintrax.model.Transaction;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRKUms;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.GV_Result.GVRSaldoReq;
import org.kapott.hbci.callback.AbstractHBCICallback;
import org.kapott.hbci.manager.BankInfo;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.manager.HBCIVersion;
import org.kapott.hbci.passport.AbstractHBCIPassport;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import javax.net.ssl.SSLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

@Slf4j
public class FinTsAdapter implements BankingProtocol {
    private static final String PROTOCOL_NAME = "FinTS/HBCI";
    private static final String PASSPORT_FILE_PREFIX = "fintrax_passport_";
    private static final String PASSPORT_FILE_SUFFIX = ".dat";
    private static final String FINTRAX_DATA_DIRECTORY = ".fintrax";
    private static final String PASSPORT_FILTER_TYPE = "Base64";
    private static final Set<PosixFilePermission> PRIVATE_PASSPORT_FILE_PERMISSIONS =
            PosixFilePermissions.fromString("rw-------");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public synchronized List<Transaction> fetchTransactions(BankAccount account, String pin) throws BankingException {
        return fetchSyncData(account, legacyBankProfile(account), pin).transactions();
    }

    @Override
    public synchronized List<Transaction> fetchTransactions(BankAccount account, Bank bank, String pin)
            throws BankingException {
        return fetchSyncData(account, bank, pin).transactions();
    }

    @Override
    public synchronized BankingSyncData fetchSyncData(BankAccount account, String pin) throws BankingException {
        return fetchSyncData(account, legacyBankProfile(account), pin);
    }

    @Override
    public synchronized BankingSyncData fetchSyncData(BankAccount account, Bank bank, String pin)
            throws BankingException {
        return fetchSyncData(account, bank, pin, null);
    }

    @Override
    public synchronized BankingSyncData fetchSyncData(BankAccount account, String pin,
                                                       Consumer<SyncStage> progress) throws BankingException {
        return fetchSyncData(account, legacyBankProfile(account), pin, progress);
    }

    @Override
    public synchronized BankingSyncData fetchSyncData(BankAccount account, Bank bank, String pin,
                                                       Consumer<SyncStage> progress) throws BankingException {
        List<Transaction> transactions = new ArrayList<>();

        GermanBankIdentity bankIdentity = germanBankIdentity(account.getIban());
        SyncStage stage = SyncStage.CONNECTION;

        try (PassportSession session = new PassportSession(null)) {
            validateBankProfile(bank);
            SimpleCallback callback = initializeHBCI(bank, pin, bankIdentity,
                    BankingException.FailureReason.CONNECTION_FAILED, null);
            Endpoint endpoint = resolveEndpoint(bank, bankIdentity);
            callback.setEndpoint(endpoint);

            session.passportFile = createPassportTempFile(privatePassportDirectory());
            session.passport = AbstractHBCIPassport.getInstance(session.passportFile);

            session.passport.setCountry("DE");
            session.passport.setBLZ(bankIdentity.bankCode());
            session.passport.setHost(endpoint.hostAndPath());
            session.passport.setPort(endpoint.port());
            session.passport.setFilterType(PASSPORT_FILTER_TYPE);

            session.handle = new HBCIHandler(HBCIVersion.HBCI_300.getId(), session.passport);
            stage = SyncStage.AUTHENTICATION;
            reportSyncProgress(progress, stage);

            Konto konto = new Konto(bankIdentity.bankCode(), bankIdentity.accountNumber());
            konto.country = "DE";
            konto.iban = bankIdentity.iban();
            konto.bic = bank.getBic();

            HBCIJob umsatzJob = session.handle.newJob("KUmsAll");
            umsatzJob.setParam("my", konto);
            // Let the bank enforce its advertised history limit; the full available range
            // gives repeated syncs an overlap instead of risking a fixed-window gap.
            umsatzJob.addToQueue();

            HBCIJob saldoJob = session.handle.newJob("SaldoReq");
            saldoJob.setParam("my", konto);
            saldoJob.addToQueue();

            stage = SyncStage.DATA_RETRIEVAL;
            reportSyncProgress(progress, stage);
            HBCIExecStatus status = session.handle.execute();

            if (!status.isOK()) {
                throw new BankingException(BankingException.FailureReason.BANK_REJECTED,
                        SyncStage.DATA_RETRIEVAL,
                        "Bank rejected the transaction sync request.");
            }

            GVRKUms result = (GVRKUms) umsatzJob.getJobResult();

            if (!result.isOK()) {
                throw new BankingException(BankingException.FailureReason.INCOMPLETE_RESPONSE, stage,
                        "Bank did not return a complete transaction response.");
            }

            for (UmsLine line : result.getFlatData()) {
                transactions.add(mapUmsLine(line, account.getId(), true));
            }
            for (UmsLine line : result.getFlatDataUnbooked()) {
                transactions.add(mapUmsLine(line, account.getId(), false));
            }

            GVRSaldoReq saldoResult = (GVRSaldoReq) saldoJob.getJobResult();
            if (!saldoResult.isOK()) {
                return new BankingSyncData(transactions, null, null);
            }
            GVRSaldoReq.Info[] saldoEntries = saldoResult.getEntries();
            if (saldoEntries.length == 0 || saldoEntries[0].ready == null
                    || saldoEntries[0].ready.value == null) {
                return new BankingSyncData(transactions, null, null);
            }
            BigDecimal accountBalance = saldoEntries[0].ready.value.getBigDecimalValue();
            LocalDateTime balanceDate = saldoEntries[0].ready.timestamp == null ? null
                    : saldoEntries[0].ready.timestamp.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            return new BankingSyncData(transactions, accountBalance, balanceDate);

        } catch (BankingException e) {
            throw withStage(e, e.getSyncStage() == null
                    ? stageForBankingFailure(e.getFailureReason(), stage) : e.getSyncStage());
        } catch (TanRequiredException e) {
            throw new BankingException(BankingException.FailureReason.TAN_REQUIRED, SyncStage.AUTHENTICATION,
                    BankingException.TAN_REQUIRED_MESSAGE, e);
        } catch (Exception e) {
            if (containsTanRequired(e)) {
                throw new BankingException(BankingException.FailureReason.TAN_REQUIRED, SyncStage.AUTHENTICATION,
                        BankingException.TAN_REQUIRED_MESSAGE, e);
            }
            throw new BankingException(BankingException.FailureReason.CONNECTION_FAILED,
                    stageForUnexpectedFailure(e, stage),
                    "Bank transaction sync could not be completed.", e);
        }
    }

    private BankingException withStage(BankingException exception, SyncStage stage) {
        return new BankingException(exception.getFailureReason(), stage, exception.getMessage(), exception.getCause());
    }

    private void validateBankProfile(Bank bank) throws BankingException {
        if (bank == null) {
            throw new BankingException(BankingException.FailureReason.MISSING_BANK_PROFILE,
                    SyncStage.CONNECTION, "No bank profile is linked to this account.");
        }
        if (bank.getFintsUserId() == null || bank.getFintsUserId().isBlank()) {
            throw new BankingException(BankingException.FailureReason.MISSING_USER_ID,
                    SyncStage.AUTHENTICATION, "FinTS User ID is missing from the bank profile.");
        }
    }

    private SyncStage stageForBankingFailure(BankingException.FailureReason reason, SyncStage currentStage) {
        return switch (reason) {
            case TAN_REQUIRED, MISSING_USER_ID -> SyncStage.AUTHENTICATION;
            case BANK_REJECTED, INCOMPLETE_RESPONSE, MAPPING_FAILED, ACCOUNT_MISMATCH -> SyncStage.DATA_RETRIEVAL;
            default -> currentStage;
        };
    }

    private SyncStage stageForUnexpectedFailure(Throwable error, SyncStage currentStage) {
        if (containsTransportFailure(error)) {
            return SyncStage.CONNECTION;
        }
        return currentStage;
    }

    private boolean containsTransportFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof NoRouteToHostException
                    || cause instanceof SocketException || cause instanceof SocketTimeoutException
                    || cause instanceof UnknownHostException || cause instanceof SSLException) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized boolean validatePin(BankAccount account, String pin) throws BankingException {
        return validatePin(account, legacyBankProfile(account), pin);
    }

    @Override
    public synchronized boolean validatePin(BankAccount account, Bank bank, String pin) throws BankingException {
        return validatePin(account, bank, pin, null);
    }

    @Override
    public synchronized boolean validatePin(BankAccount account, Bank bank, String pin,
                                             Consumer<String> progress) throws BankingException {
        GermanBankIdentity bankIdentity = germanBankIdentity(account.getIban());

        try (PassportSession session = new PassportSession(progress)) {
            validateBankProfile(bank);
            reportProgress(progress, "HBCI4Java-Version " + hbci4JavaVersion());
            reportProgress(progress, "initializing FinTS/HBCI protocol session");
            SimpleCallback callback = initializeHBCI(bank, pin, bankIdentity,
                    BankingException.FailureReason.CONNECTION_FAILED, progress);
            Endpoint endpoint = resolveEndpoint(bank, bankIdentity);
            callback.setEndpoint(endpoint);

            reportProgress(progress, "open pin/tan passport");
            session.passportFile = createPassportTempFile(privatePassportDirectory());
            session.passport = AbstractHBCIPassport.getInstance(session.passportFile);

            session.passport.setCountry("DE");
            session.passport.setBLZ(bankIdentity.bankCode());
            session.passport.setHost(endpoint.hostAndPath());
            session.passport.setPort(endpoint.port());
            session.passport.setFilterType(PASSPORT_FILTER_TYPE);

            reportProgress(progress, "[PIN/TAN] url         : " + safeEndpoint(endpoint));
            reportProgress(progress, "[PIN/TAN] blz         : " + bankIdentity.bankCode());
            reportProgress(progress, "[PIN/TAN] filter      : Base64");
            reportProgress(progress, "[PIN/TAN] HBCI version: " + HBCIVersion.HBCI_300.getId());

            session.handle = new HBCIHandler(HBCIVersion.HBCI_300.getId(), session.passport);

            Konto konto = new Konto(bankIdentity.bankCode(), bankIdentity.accountNumber());
            konto.country = "DE";
            konto.iban = bankIdentity.iban();
            konto.bic = bank.getBic();

            HBCIJob saldoJob = session.handle.newJob("SaldoReq");
            saldoJob.setParam("my", konto);
            saldoJob.addToQueue();

            reportProgress(progress, "executing balance request");
            HBCIExecStatus status = session.handle.execute();
            reportProgress(progress, "bank access response received");
            return status.isOK();

        } catch (BankingException e) {
            throw e;
        } catch (TanRequiredException e) {
            throw new BankingException(BankingException.FailureReason.TAN_REQUIRED,
                    BankingException.TAN_REQUIRED_MESSAGE, e);
        } catch (Exception e) {
            if (containsTanRequired(e)) {
                throw new BankingException(BankingException.FailureReason.TAN_REQUIRED,
                        BankingException.TAN_REQUIRED_MESSAGE, e);
            }
            log.warn("Bank credential validation could not be completed");
            throw new BankingException(BankingException.FailureReason.CREDENTIAL_VALIDATION_FAILED,
                    "Bank credential validation could not be completed.", e);
        }
    }

    @Override
    public synchronized List<DiscoveredBankAccount> discoverAccounts(BankAccount account, Bank bank, String pin,
                                                                       Consumer<String> progress)
            throws BankingException {
        GermanBankIdentity bankIdentity = germanBankIdentity(account.getIban());

        try (PassportSession session = new PassportSession(progress)) {
            validateBankProfile(bank);
            reportProgress(progress, "HBCI4Java-Version " + hbci4JavaVersion());
            reportProgress(progress, "initializing FinTS/HBCI protocol session");
            SimpleCallback callback = initializeHBCI(bank, pin, bankIdentity,
                    BankingException.FailureReason.ACCOUNT_DISCOVERY_FAILED, progress);
            Endpoint endpoint = resolveEndpoint(bank, bankIdentity);
            callback.setEndpoint(endpoint);

            reportProgress(progress, "open pin/tan passport");
            session.passportFile = createPassportTempFile(privatePassportDirectory());
            session.passport = AbstractHBCIPassport.getInstance(session.passportFile);
            session.passport.setCountry("DE");
            session.passport.setBLZ(bankIdentity.bankCode());
            session.passport.setHost(endpoint.hostAndPath());
            session.passport.setPort(endpoint.port());
            session.passport.setFilterType(PASSPORT_FILTER_TYPE);

            reportProgress(progress, "[PIN/TAN] url         : " + safeEndpoint(endpoint));
            reportProgress(progress, "[PIN/TAN] blz         : " + bankIdentity.bankCode());
            reportProgress(progress, "[PIN/TAN] filter      : Base64");
            reportProgress(progress, "[PIN/TAN] HBCI version: " + HBCIVersion.HBCI_300.getId());

            session.handle = new HBCIHandler(HBCIVersion.HBCI_300.getId(), session.passport);
            reportProgress(progress, "reading accounts from pin/tan passport");
            org.kapott.hbci.structures.Konto[] passportAccounts = session.passport.getAccounts();
            if (passportAccounts == null || passportAccounts.length == 0) {
                reportProgress(progress, "no accounts returned by the bank");
                return List.of();
            }

            List<DiscoveredBankAccount> accounts = new ArrayList<>(passportAccounts.length);
            for (org.kapott.hbci.structures.Konto discovered : passportAccounts) {
                accounts.add(new DiscoveredBankAccount(
                        firstNonBlank(discovered.type, discovered.number),
                        firstNonBlank(discovered.type, discovered.acctype),
                        joinNonBlank(discovered.name, discovered.name2),
                        firstNonBlank(discovered.iban),
                        firstNonBlank(discovered.bic),
                        firstNonBlank(discovered.blz, bankIdentity.bankCode()),
                        firstNonBlank(discovered.number),
                        firstNonBlank(discovered.subnumber),
                        firstNonBlank(discovered.curr, "EUR")));
            }
            reportProgress(progress, "found " + accounts.size() + " bank account(s)");
            return List.copyOf(accounts);
        } catch (BankingException e) {
            throw e;
        } catch (TanRequiredException e) {
            throw new BankingException(BankingException.FailureReason.TAN_REQUIRED,
                    BankingException.TAN_REQUIRED_MESSAGE, e);
        } catch (Exception e) {
            log.warn("FinTS account discovery could not be completed");
            throw new BankingException(BankingException.FailureReason.ACCOUNT_DISCOVERY_FAILED,
                    "Bank account discovery could not be completed.", e);
        }
    }

    @Override
    public String getProtocolName() {
        return PROTOCOL_NAME;
    }

    @Override
    public synchronized String lookupPinTanUrl(BankAccount account) throws BankingException {
        BankDirectoryEntry entry = lookupBankDetails(account.getIban());
        String address = entry == null ? null : firstNonBlank(entry.pinTanAddress());
        if (address != null) {
            endpoint(address);
        }
        return address;
    }

    @Override
    public synchronized BankDirectoryEntry lookupBankDetails(String germanIban) throws BankingException {
        GermanBankIdentity bankIdentity = germanBankIdentity(germanIban);
        try {
            initializeHBCI(null, null, bankIdentity,
                    BankingException.FailureReason.BANK_DIRECTORY_LOOKUP_FAILED, null);
            BankInfo bankInfo = HBCIUtils.getBankInfo(bankIdentity.bankCode());
            String pinTanAddress = bankInfo == null ? null : firstNonBlank(bankInfo.getPinTanAddress());
            if (pinTanAddress != null) {
                endpoint(pinTanAddress);
            }
            return new BankDirectoryEntry(bankIdentity.bankCode(),
                    bankInfo == null ? null : firstNonBlank(bankInfo.getName()),
                    bankInfo == null ? null : firstNonBlank(bankInfo.getBic()),
                    pinTanAddress);
        } catch (BankingException e) {
            throw e;
        } catch (Exception e) {
            throw new BankingException(BankingException.FailureReason.BANK_DIRECTORY_LOOKUP_FAILED,
                    "Bank directory lookup could not be completed.", e);
        } finally {
            HBCIUtils.doneThread();
        }
    }

    private SimpleCallback initializeHBCI(Bank bank, String pin, GermanBankIdentity bankIdentity,
                                          BankingException.FailureReason failureReason,
                                          Consumer<String> progress) throws BankingException {
        SimpleCallback callback = new SimpleCallback(bank, pin, newTemporaryPassphrase(),
                bankIdentity.bankCode(), progress);
        try {
            HBCIUtils.init(new Properties(), callback);
            HBCIUtils.setParam("client.passport.default", "PinTan");
            HBCIUtils.setParam("client.passport.PinTan.init", "1");
            HBCIUtils.setParam("client.passport.PinTan.checkcert", "1");
        } catch (Exception e) {
            throw new BankingException(failureReason, "FinTS protocol initialization failed.", e);
        }
        return callback;
    }

    private GermanBankIdentity germanBankIdentity(String iban) throws BankingException {
        String normalizedIban = iban == null ? "" : iban.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (!normalizedIban.matches("DE\\d{20}") || !HBCIUtils.checkIBANCRC(normalizedIban)) {
            throw new BankingException(BankingException.FailureReason.INVALID_IBAN,
                    "A valid German IBAN is required to derive BLZ and account number.");
        }
        return new GermanBankIdentity(normalizedIban, normalizedIban.substring(4, 12),
                normalizedIban.substring(12, 22));
    }

    private Bank legacyBankProfile(BankAccount account) {
        return Bank.builder()
                .name(account.getBankName())
                .bic(account.getBic())
                .fintsServerUrl(account.getFintsServerUrl())
                .build();
    }

    private Endpoint resolveEndpoint(Bank bank, GermanBankIdentity bankIdentity) throws BankingException {
        String url = bank == null ? null : firstNonBlank(bank.getFintsServerUrl());
        if (url == null) {
            BankInfo bankInfo = HBCIUtils.getBankInfo(bankIdentity.bankCode());
            url = bankInfo == null ? null : firstNonBlank(bankInfo.getPinTanAddress());
        }
        if (url == null) {
            throw new BankingException(BankingException.FailureReason.MISSING_ENDPOINT,
                    "No FinTS server address was found for this bank.");
        }
        return endpoint(url);
    }

    private Endpoint endpoint(String address) throws BankingException {
        String normalized = address.trim();
        if (!normalized.contains("://")) {
            normalized = "https://" + normalized;
        }
        try {
            URI uri = new URI(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new URISyntaxException(normalized, "Expected a plain HTTPS server address");
            }
            int port = uri.getPort() < 0 ? 443 : uri.getPort();
            if (port < 1 || port > 65535) {
                throw new URISyntaxException(normalized, "Invalid server port");
            }
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            if (path.isEmpty() && uri.getRawQuery() != null) {
                path = "/";
            }
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            return new Endpoint(uri.getHost() + path + query, port);
        } catch (URISyntaxException e) {
            throw new BankingException(BankingException.FailureReason.INVALID_ENDPOINT,
                    "FinTS server URL must be a valid HTTPS address.", e);
        }
    }

    private String hbci4JavaVersion() {
        String version = HBCIHandler.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "unknown" : version;
    }

    private String safeEndpoint(Endpoint endpoint) {
        int queryStart = endpoint.hostAndPath().indexOf('?');
        return queryStart < 0 ? endpoint.hostAndPath() : endpoint.hostAndPath().substring(0, queryStart);
    }

    private Path privatePassportDirectory() throws IOException {
        Path directory = Path.of(System.getProperty("user.home"), FINTRAX_DATA_DIRECTORY);
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)) {
            throw new IOException("Fintrax data directory must not be a symbolic link.");
        }
        if (Files.getFileAttributeView(directory, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        }
        return directory;
    }

    private File createPassportTempFile(Path privateDirectory) throws IOException {
        if (Files.getFileAttributeView(privateDirectory, PosixFileAttributeView.class) != null) {
            FileAttribute<Set<PosixFilePermission>> permissions = PosixFilePermissions
                    .asFileAttribute(PRIVATE_PASSPORT_FILE_PERMISSIONS);
            return Files.createTempFile(privateDirectory, PASSPORT_FILE_PREFIX, PASSPORT_FILE_SUFFIX,
                    permissions).toFile();
        }
        return Files.createTempFile(privateDirectory, PASSPORT_FILE_PREFIX, PASSPORT_FILE_SUFFIX).toFile();
    }

    private static void reportProgress(Consumer<String> progress, String message) {
        if (progress == null) {
            return;
        }
        log.info("FinTS connection test: {}", message);
        progress.accept(message);
    }

    private static void reportSyncProgress(Consumer<SyncStage> progress, SyncStage stage) {
        if (progress != null) {
            progress.accept(stage);
        }
    }

    Transaction mapUmsLine(UmsLine line, Long accountId, boolean booked) throws BankingException {
        try {
            if (line == null || line.value == null) {
                throw new IllegalArgumentException("Transaction amount is missing");
            }
            LocalDate bookingDate = toLocalDate(line.bdate);
            LocalDate valueDate = toLocalDate(line.valuta);
            BigDecimal amount = line.value.getBigDecimalValue();
            BigDecimal balanceAfter = booked && line.saldo != null && line.saldo.value != null
                    ? line.saldo.value.getBigDecimalValue() : null;
            String payee = line.other == null ? null : joinNonBlank(line.other.name, line.other.name2);
            String purpose = line.usage == null ? "" : String.join(" ", line.usage).trim();
            if (purpose.isBlank() && line.additional != null) {
                purpose = line.additional.trim();
            }

            return Transaction.builder()
                    .accountId(accountId)
                    .originalPayee(payee)
                    .purpose(purpose)
                    .amount(amount)
                    .bookingDate(bookingDate)
                    .valueDate(valueDate)
                    .balanceAfter(balanceAfter)
                    .transactionType(line.text)
                    .checksum(sourceChecksum(line))
                    .endToEndId(line.endToEndId)
                    .mandateId(line.mandateId)
                    .creditorId(line.other == null ? null : line.other.creditorid)
                    .customerReference(line.customerref)
                    .primanota(line.primanota)
                    .gvCode(line.gvcode)
                    .booked(booked)
                    .createdAt(LocalDateTime.now(ZoneId.systemDefault()))
                    .updatedAt(LocalDateTime.now(ZoneId.systemDefault()))
                    .build();
        } catch (Exception e) {
            throw new BankingException(BankingException.FailureReason.MAPPING_FAILED,
                    "Bank returned a transaction that could not be mapped completely.", e);
        }
    }

    private LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private String sourceChecksum(UmsLine line) {
        String sourceId = firstNonBlank(line.id, line.instref, line.customerref);
        return sourceId == null ? null : "fints:" + sourceId;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String joinNonBlank(String... values) {
        return java.util.Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private String newTemporaryPassphrase() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getEncoder().encodeToString(randomBytes);
    }

    private boolean containsTanRequired(Throwable error) {
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof TanRequiredException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private final class PassportSession implements AutoCloseable {
        private final Consumer<String> progress;
        private HBCIPassport passport;
        private HBCIHandler handle;
        private File passportFile;

        private PassportSession(Consumer<String> progress) {
            this.progress = progress;
        }

        @Override
        public void close() {
            if (handle != null) {
                try {
                    handle.close();
                } catch (Exception e) {
                    log.warn("Bank session handler could not be closed");
                }
            }
            if (passport != null) {
                reportProgress(progress, "closing pin/tan passport");
                try {
                    passport.close();
                    reportProgress(progress, "pin/tan passport closed");
                } catch (Exception e) {
                    log.warn("Bank session passport could not be closed");
                }
            }
            if (passportFile != null) {
                try {
                    Files.deleteIfExists(passportFile.toPath());
                } catch (Exception e) {
                    log.warn("Temporary bank session file could not be removed");
                }
            }
            HBCIUtils.doneThread();
        }
    }

    private static class SimpleCallback extends AbstractHBCICallback {
        private final Bank bank;
        private final String pin;
        private final String temporaryPassphrase;
        private final String bankCode;
        private final Consumer<String> progress;
        private Endpoint endpoint;

        public SimpleCallback(Bank bank, String pin, String temporaryPassphrase, String bankCode,
                              Consumer<String> progress) {
            this.bank = bank;
            this.pin = pin;
            this.temporaryPassphrase = temporaryPassphrase;
            this.bankCode = bankCode;
            this.progress = progress;
        }

        public void setEndpoint(Endpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public void callback(HBCIPassport passport, int reason, String msg, int datatype, StringBuffer retData) {
            switch (reason) {
                case NEED_PASSPHRASE_LOAD, NEED_PASSPHRASE_SAVE:
                    retData.replace(0, retData.length(), temporaryPassphrase);
                    break;
                case NEED_PT_PIN:
                    retData.replace(0, retData.length(), valueOrEmpty(pin));
                    break;
                case NEED_BLZ:
                    retData.replace(0, retData.length(), bankCode);
                    break;
                case NEED_HOST:
                    if (endpoint != null) {
                        retData.replace(0, retData.length(), endpoint.hostAndPath());
                    }
                    break;
                case NEED_PORT:
                    if (endpoint != null) {
                        retData.replace(0, retData.length(), String.valueOf(endpoint.port()));
                    }
                    break;
                case NEED_USERID:
                    retData.replace(0, retData.length(), bank == null ? "" : valueOrEmpty(bank.getFintsUserId()));
                    break;
                case NEED_CUSTOMERID:
                    retData.replace(0, retData.length(), bank == null ? "" : valueOrEmpty(bank.getFintsCustomerId()));
                    break;
                case NEED_PT_SECMECH:
                    String options = retData.toString();
                    String selectedMechanism;
                    if (options.contains("912")) {
                        selectedMechanism = "912";
                    } else if (options.contains("913")) {
                        selectedMechanism = "913";
                    } else {
                        selectedMechanism = options.split(":")[0];
                    }
                    retData.replace(0, retData.length(), selectedMechanism);
                    reportProgress(progress, "[PIN/TAN] selected sec mech: " + selectedMechanism);
                    break;
                case NEED_PT_TAN, NEED_PT_TANMEDIA, NEED_PT_PHOTOTAN, NEED_PT_QRTAN,
                     NEED_PT_DECOUPLED, NEED_PT_DECOUPLED_RETRY:
                    throw new TanRequiredException();
                default:
                    break;
            }
        }

        private String valueOrEmpty(String value) {
            return value == null ? "" : value;
        }

        @Override
        public void status(HBCIPassport passport, int statusTag, Object[] o) {
        }

        @Override
        public void log(String msg, int level, Date date, StackTraceElement trace) {
        }
    }

    private static final class TanRequiredException extends RuntimeException {
    }

    private record GermanBankIdentity(String iban, String bankCode, String accountNumber) {}

    private record Endpoint(String hostAndPath, int port) {}
}
