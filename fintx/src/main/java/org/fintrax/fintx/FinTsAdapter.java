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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

@Slf4j
public class FinTsAdapter implements BankingProtocol {
    private static final String PROTOCOL_NAME = "FinTS/HBCI";

    @Override
    public List<Transaction> fetchTransactions(BankAccount account, String pin) throws BankingException {
        log.info("Fetching transactions for account {}", account.getIban());
        List<Transaction> transactions = new ArrayList<>();

        HBCIPassport passport = null;
        HBCIHandler handle = null;

        try {
            Properties props = new Properties();
            HBCIUtils.init(props, new SimpleCallback(account, pin));

            HBCIUtils.setParam("client.passport.default", "PinTan");
            HBCIUtils.setParam("client.passport.PinTan.init", "1");

            File passportFile = File.createTempFile("fintrax_passport_", ".dat");
            passportFile.deleteOnExit();
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
            umsatzJob.addToQueue();

            HBCIExecStatus status = handle.execute();

            if (!status.isOK()) {
                throw new BankingException("HBCI execution failed: " + status.toString());
            }

            GVRKUms result = (GVRKUms) umsatzJob.getJobResult();

            if (!result.isOK()) {
                throw new BankingException("Transaction fetch failed: " + result.toString());
            }

            List<UmsLine> buchungen = result.getFlatData();
            for (UmsLine buchung : buchungen) {
                Transaction tx = mapUmsLine(buchung, account.getId());
                if (tx != null) {
                    transactions.add(tx);
                }
            }

        } catch (BankingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch transactions for {}", account.getIban(), e);
            throw new BankingException("Failed to fetch transactions: " + e.getMessage(), e);
        } finally {
            if (handle != null) {
                handle.close();
            }
            if (passport != null) {
                passport.close();
            }
        }

        log.info("Fetched {} transactions for {}", transactions.size(), account.getIban());
        return transactions;
    }

    @Override
    public boolean validatePin(BankAccount account, String pin) throws BankingException {
        log.info("Validating PIN for account {}", account.getIban());

        HBCIPassport passport = null;
        HBCIHandler handle = null;

        try {
            Properties props = new Properties();
            HBCIUtils.init(props, new SimpleCallback(account, pin));

            HBCIUtils.setParam("client.passport.default", "PinTan");
            HBCIUtils.setParam("client.passport.PinTan.init", "1");

            File passportFile = File.createTempFile("fintrax_passport_", ".dat");
            passportFile.deleteOnExit();
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

        } catch (Exception e) {
            log.error("Failed to validate PIN for {}", account.getIban(), e);
            throw new BankingException("Failed to validate PIN: " + e.getMessage(), e);
        } finally {
            if (handle != null) {
                handle.close();
            }
            if (passport != null) {
                passport.close();
            }
        }
    }

    @Override
    public String getProtocolName() {
        return PROTOCOL_NAME;
    }

    private Transaction mapUmsLine(UmsLine line, Long accountId) {
        try {
            LocalDate valuta = LocalDate.parse(line.valuta.toString());
            BigDecimal amount = new BigDecimal(line.value.toString());

            String payee = "Unknown";

            String usage = "";
            if (line.usage != null && !line.usage.isEmpty()) {
                usage = String.join(" ", line.usage);
            }

            String checksum = computeChecksum(valuta, amount, payee, usage);

            return Transaction.builder()
                    .accountId(accountId)
                    .originalPayee(payee)
                    .purpose(usage)
                    .amount(amount)
                    .bookingDate(valuta)
                    .valueDate(valuta)
                    .checksum(checksum)
                    .booked(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.warn("Failed to map UmsLine: {}", e.getMessage());
            return null;
        }
    }

    private String computeChecksum(LocalDate date, BigDecimal amount, String payee, String usage) {
        try {
            String data = date + "|" + amount + "|" + payee + "|" + usage;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            String fallback = date + "|" + amount + "|" + payee + "|" + usage;
            return String.valueOf(fallback.hashCode());
        }
    }

    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private static class SimpleCallback extends AbstractHBCICallback {
        private final BankAccount account;
        private final String pin;

        public SimpleCallback(BankAccount account, String pin) {
            this.account = account;
            this.pin = pin;
        }

        @Override
        public void callback(HBCIPassport passport, int reason, String msg, int datatype, StringBuffer retData) {
            switch (reason) {
                case NEED_PASSPHRASE_LOAD:
                case NEED_PASSPHRASE_SAVE:
                    retData.replace(0, retData.length(), "fintrax");
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
                    retData.replace(0, retData.length(), "");
                    break;
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
}
