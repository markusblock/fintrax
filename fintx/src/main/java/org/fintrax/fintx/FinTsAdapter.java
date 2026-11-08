package org.fintrax.fintx;

import lombok.extern.slf4j.Slf4j;
import org.fintrax.model.BankAccount;
import org.fintrax.model.Transaction;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRKUms;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.callback.AbstractHBCICallback;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.manager.HBCIVersion;
import org.kapott.hbci.passport.AbstractHBCIPassport;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Properties;

@Slf4j
public class FinTsAdapter implements BankingProtocol {
    private static final String PROTOCOL_NAME = "FinTS/HBCI";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public synchronized List<Transaction> fetchTransactions(BankAccount account, String pin) throws BankingException {
        List<Transaction> transactions = new ArrayList<>();

        HBCIPassport passport = null;
        HBCIHandler handle = null;
        File passportFile = null;
        SimpleCallback callback = new SimpleCallback(account, pin, newTemporaryPassphrase());

        try {
            Properties props = new Properties();
            HBCIUtils.init(props, callback);

            HBCIUtils.setParam("client.passport.default", "PinTan");
            HBCIUtils.setParam("client.passport.PinTan.init", "1");
            HBCIUtils.setParam("client.passport.PinTan.checkcert", "1");

            passportFile = File.createTempFile("fintrax_passport_", ".dat");
            passport = AbstractHBCIPassport.getInstance(passportFile);

            passport.setCountry("DE");
            passport.setHost(account.getBic());
            passport.setPort(443);
            passport.setFilterType("Base64");

            handle = new HBCIHandler(HBCIVersion.HBCI_300.getId(), passport);

            Konto konto = new Konto();
            konto.iban = account.getIban();
            konto.bic = account.getBic();

            HBCIJob umsatzJob = handle.newJob("KUmsAll");
            umsatzJob.setParam("my", konto);
            // Let the bank enforce its advertised history limit; the full available range
            // gives repeated syncs an overlap instead of risking a fixed-window gap.
            umsatzJob.addToQueue();

            HBCIExecStatus status = handle.execute();

            if (!status.isOK()) {
                throw new BankingException("Bank rejected the transaction sync request.");
            }

            GVRKUms result = (GVRKUms) umsatzJob.getJobResult();

            if (!result.isOK()) {
                throw new BankingException("Bank did not return a complete transaction response.");
            }

            for (UmsLine line : result.getFlatData()) {
                transactions.add(mapUmsLine(line, account.getId(), true));
            }
            for (UmsLine line : result.getFlatDataUnbooked()) {
                transactions.add(mapUmsLine(line, account.getId(), false));
            }

        } catch (BankingException e) {
            throw e;
        } catch (TanRequiredException e) {
            throw new BankingException(BankingException.TAN_REQUIRED_MESSAGE, e);
        } catch (Exception e) {
            if (containsTanRequired(e)) {
                throw new BankingException(BankingException.TAN_REQUIRED_MESSAGE, e);
            }
            log.warn("Bank transaction sync could not be completed");
            throw new BankingException("Bank transaction sync could not be completed.", e);
        } finally {
            closeSession(handle, passport, passportFile);
        }

        return transactions;
    }

    @Override
    public synchronized boolean validatePin(BankAccount account, String pin) throws BankingException {
        HBCIPassport passport = null;
        HBCIHandler handle = null;
        File passportFile = null;
        SimpleCallback callback = new SimpleCallback(account, pin, newTemporaryPassphrase());

        try {
            Properties props = new Properties();
            HBCIUtils.init(props, callback);

            HBCIUtils.setParam("client.passport.default", "PinTan");
            HBCIUtils.setParam("client.passport.PinTan.init", "1");
            HBCIUtils.setParam("client.passport.PinTan.checkcert", "1");

            passportFile = File.createTempFile("fintrax_passport_", ".dat");
            passport = AbstractHBCIPassport.getInstance(passportFile);

            passport.setCountry("DE");
            passport.setHost(account.getBic());
            passport.setPort(443);
            passport.setFilterType("Base64");

            handle = new HBCIHandler(HBCIVersion.HBCI_300.getId(), passport);

            Konto konto = new Konto();
            konto.iban = account.getIban();
            konto.bic = account.getBic();

            HBCIJob saldoJob = handle.newJob("SaldoReq");
            saldoJob.setParam("my", konto);
            saldoJob.addToQueue();

            HBCIExecStatus status = handle.execute();

            return status.isOK();

        } catch (TanRequiredException e) {
            throw new BankingException(BankingException.TAN_REQUIRED_MESSAGE, e);
        } catch (Exception e) {
            if (containsTanRequired(e)) {
                throw new BankingException(BankingException.TAN_REQUIRED_MESSAGE, e);
            }
            log.warn("Bank credential validation could not be completed");
            throw new BankingException("Bank credential validation could not be completed.", e);
        } finally {
            closeSession(handle, passport, passportFile);
        }
    }

    @Override
    public String getProtocolName() {
        return PROTOCOL_NAME;
    }

    private Transaction mapUmsLine(UmsLine line, Long accountId, boolean booked) throws BankingException {
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
                    .booked(booked)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            throw new BankingException("Bank returned a transaction that could not be mapped completely.", e);
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

    private void closeSession(HBCIHandler handle, HBCIPassport passport, File passportFile) {
        if (handle != null) {
            try {
                handle.close();
            } catch (Exception e) {
                log.warn("Bank session handler could not be closed");
            }
        }
        if (passport != null) {
            try {
                passport.close();
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
    }

    private static class SimpleCallback extends AbstractHBCICallback {
        private final BankAccount account;
        private final String pin;
        private final String temporaryPassphrase;

        public SimpleCallback(BankAccount account, String pin, String temporaryPassphrase) {
            this.account = account;
            this.pin = pin;
            this.temporaryPassphrase = temporaryPassphrase;
        }

        @Override
        public void callback(HBCIPassport passport, int reason, String msg, int datatype, StringBuffer retData) {
            switch (reason) {
                case NEED_PASSPHRASE_LOAD:
                case NEED_PASSPHRASE_SAVE:
                    retData.replace(0, retData.length(), temporaryPassphrase);
                    break;
                case NEED_PT_PIN:
                    retData.replace(0, retData.length(), pin);
                    break;
                case NEED_BLZ:
                    retData.replace(0, retData.length(), "");
                    break;
                case NEED_USERID:
                case NEED_CUSTOMERID:
                    retData.replace(0, retData.length(), account.getAccountHolder());
                    break;
                case NEED_PT_SECMECH:
                    String options = retData.toString();
                    if (options.contains("912")) {
                        retData.replace(0, retData.length(), "912");
                    } else if (options.contains("913")) {
                        retData.replace(0, retData.length(), "913");
                    } else {
                        retData.replace(0, retData.length(), options.split(":")[0]);
                    }
                    break;
                case NEED_PT_TAN:
                case NEED_PT_TANMEDIA:
                case NEED_PT_PHOTOTAN:
                case NEED_PT_QRTAN:
                case NEED_PT_DECOUPLED:
                case NEED_PT_DECOUPLED_RETRY:
                    throw new TanRequiredException();
                default:
                    break;
            }
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
}
