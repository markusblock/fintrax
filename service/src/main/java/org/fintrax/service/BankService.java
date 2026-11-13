package org.fintrax.service;

import lombok.extern.slf4j.Slf4j;
import org.fintrax.fintx.BankingException;
import org.fintrax.fintx.BankingProtocol;
import org.fintrax.fintx.BankDirectoryEntry;
import org.fintrax.model.Bank;
import org.fintrax.model.BankAccount;
import org.fintrax.store.DataRoot;
import org.fintrax.store.StoreManager;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
public class BankService {
    private final StoreManager store;
    private final BankingProtocol bankingProtocol;
    private long nextId;

    public BankService(StoreManager store, BankingProtocol bankingProtocol) {
        this.store = store;
        this.bankingProtocol = bankingProtocol;
        ensureBankProfiles();
        initializeNextId();
        migrateLegacyAccounts();
    }

    public List<Bank> getAllBanks() {
        return List.copyOf(store.getRoot().getBanks());
    }

    public Optional<Bank> getBank(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return store.getRoot().getBanks().stream()
                .filter(bank -> id.equals(bank.getId()))
                .findFirst();
    }

    public Optional<Bank> getBankForAccount(BankAccount account) {
        return account == null ? Optional.empty() : getBank(account.getBankId());
    }

    public Optional<Bank> findByBlz(String blz) {
        String normalizedBlz = normalizeBlz(blz);
        if (normalizedBlz == null) {
            return Optional.empty();
        }
        return store.getRoot().getBanks().stream()
                .filter(bank -> normalizedBlz.equals(normalizeBlz(bank.getBlz())))
                .findFirst();
    }

    public Optional<Bank> findByBic(String bic) {
        String normalizedBic = normalizeBic(bic);
        if (normalizedBic == null) {
            return Optional.empty();
        }
        return store.getRoot().getBanks().stream()
                .filter(bank -> normalizedBic.equals(normalizeBic(bank.getBic())))
                .findFirst();
    }

    public Optional<Bank> findForIban(String iban) {
        return findByBlz(bankCodeFromIban(iban));
    }

    public Optional<Bank> findForIban(String iban, String bic) {
        String blz = bankCodeFromIban(iban);
        return blz == null ? findByBic(bic) : findByBlz(blz);
    }

    public String bankCodeForGermanIban(String iban) {
        return bankCodeFromIban(iban);
    }

    public BankDirectoryEntry lookupBankDirectoryEntry(String iban) throws BankingException {
        return bankingProtocol.lookupBankDetails(iban);
    }

    public Bank createBank(String blz, String name, String bic, String fintsServerUrl,
                           String fintsUserId, String fintsCustomerId, String comment) {
        String normalizedBlz = normalizeBlz(blz);
        if (normalizedBlz == null) {
            throw new IllegalArgumentException("A German BLZ is required for a bank profile.");
        }
        if (findByBlz(normalizedBlz).isPresent()) {
            throw new IllegalArgumentException("A bank profile already exists for BLZ " + normalizedBlz);
        }

        Bank bank = createBankRecord(normalizedBlz, name, bic, fintsServerUrl, fintsUserId, fintsCustomerId, comment);
        store.store(store.getRoot().getBanks());
        return bank;
    }

    public Bank updateBank(Long id, String name, String bic, String fintsServerUrl,
                           String fintsUserId, String fintsCustomerId, String comment) {
        Bank bank = getBank(id).orElseThrow(() -> new IllegalArgumentException("Bank not found: " + id));
        bank.setName(trimToNull(name));
        bank.setBic(normalizeBic(bic));
        bank.setFintsServerUrl(trimToNull(fintsServerUrl));
        bank.setFintsUserId(trimToNull(fintsUserId));
        bank.setFintsCustomerId(trimToNull(fintsCustomerId));
        bank.setComment(trimToNull(comment));
        bank.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        store.store(bank);
        return bank;
    }

    public void deleteIfUnused(Long bankId) {
        if (bankId == null || store.getRoot().getAccounts().stream()
                .anyMatch(account -> bankId.equals(account.getBankId()))) {
            return;
        }
        getBank(bankId).ifPresent(bank -> {
            store.getRoot().getBanks().remove(bank);
            store.store(store.getRoot().getBanks());
        });
    }

    public Bank findOrCreateForImport(String iban, String bic) {
        String blz = bankCodeFromIban(iban);
        Optional<Bank> existing = blz == null ? findByBic(bic) : findByBlz(blz);
        if (existing.isPresent()) {
            return existing.get();
        }

        BankDirectoryEntry directoryEntry = lookupDirectoryEntry(iban);
        return createBankRecord(blz,
                firstNonBlank(directoryEntry == null ? null : directoryEntry.name(),
                        fallbackBankName(blz, bic)),
                firstNonBlank(bic, directoryEntry == null ? null : directoryEntry.bic()),
                directoryEntry == null ? null : directoryEntry.pinTanAddress(),
                null, null, null);
    }

    public Bank findOrCreateLegacy(String iban, String bic, String serverUrl, String legacyName) {
        BankDirectoryEntry directoryEntry = lookupDirectoryEntry(iban);
        String blz = directoryEntry == null ? bankCodeFromIban(iban) : directoryEntry.blz();
        String resolvedBic = firstNonBlank(bic, directoryEntry == null ? null : directoryEntry.bic());
        Optional<Bank> existing = findByBlz(blz).or(() -> findByBic(resolvedBic));
        if (existing.isPresent()) {
            return existing.get();
        }

        Bank bank = createBankRecord(blz,
                firstNonBlank(directoryEntry == null ? null : directoryEntry.name(), legacyName),
                resolvedBic,
                firstNonBlank(serverUrl, directoryEntry == null ? null : directoryEntry.pinTanAddress()),
                null, null, null);
        store.store(store.getRoot().getBanks());
        return bank;
    }

    private void migrateLegacyAccounts() {
        DataRoot root = store.getRoot();
        boolean changed = false;
        for (BankAccount account : root.getAccounts()) {
            changed |= migrateLegacyAccount(account);
        }

        if (changed) {
            store.store(root.getBanks());
            store.store(root.getAccounts());
        }
    }

    private boolean migrateLegacyAccount(BankAccount account) {
        if (getBank(account.getBankId()).isPresent()) {
            if (isBlank(account.getAccountName())) {
                account.setAccountName(trimToNull(account.getBankName()));
                return true;
            }
            return false;
        }

        String blz = bankCodeFromIban(account.getIban());
        Bank bank = (blz == null ? findByBic(account.getBic()) : findByBlz(blz)).orElse(null);
        BankDirectoryEntry directoryEntry = lookupDirectoryEntry(account.getIban());
        if (bank == null) {
            bank = createBankRecord(blz,
                    firstNonBlank(directoryEntry == null ? null : directoryEntry.name(),
                            fallbackBankName(blz, account.getBic())),
                    firstNonBlank(account.getBic(), directoryEntry == null ? null : directoryEntry.bic()),
                    firstNonBlank(account.getFintsServerUrl(),
                            directoryEntry == null ? null : directoryEntry.pinTanAddress()),
                    null, null, null);
        } else {
            fillMissingLegacyBankDetails(bank, account, blz, directoryEntry);
        }
        account.setBankId(bank.getId());
        if (isBlank(account.getAccountName())) {
            account.setAccountName(trimToNull(account.getBankName()));
        }
        return true;
    }

    private void fillMissingLegacyBankDetails(Bank bank, BankAccount account, String blz,
                                               BankDirectoryEntry directoryEntry) {
        if (isBlank(bank.getName())) {
            bank.setName(firstNonBlank(directoryEntry == null ? null : directoryEntry.name(),
                    fallbackBankName(blz, account.getBic())));
        }
        if (isBlank(bank.getBic())) {
            bank.setBic(firstNonBlank(account.getBic(), directoryEntry == null ? null : directoryEntry.bic()));
        }
        if (isBlank(bank.getFintsServerUrl())) {
            bank.setFintsServerUrl(firstNonBlank(account.getFintsServerUrl(),
                    directoryEntry == null ? null : directoryEntry.pinTanAddress()));
        }
    }

    private Bank createBankRecord(String blz, String name, String bic, String fintsServerUrl,
                                  String fintsUserId, String fintsCustomerId, String comment) {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        Bank bank = Bank.builder()
                .id(nextId++)
                .name(trimToNull(name))
                .bic(normalizeBic(bic))
                .blz(normalizeBlz(blz))
                .fintsServerUrl(trimToNull(fintsServerUrl))
                .fintsUserId(trimToNull(fintsUserId))
                .fintsCustomerId(trimToNull(fintsCustomerId))
                .comment(trimToNull(comment))
                .createdAt(now)
                .updatedAt(now)
                .build();
        store.getRoot().getBanks().add(bank);
        return bank;
    }

    private void ensureBankProfiles() {
        if (store.getRoot().getBanks() == null) {
            store.getRoot().setBanks(new ArrayList<>());
        }
    }

    private void initializeNextId() {
        nextId = store.getRoot().getBanks().stream()
                .mapToLong(Bank::getId)
                .max()
                .orElse(0) + 1;
    }

    private BankDirectoryEntry lookupDirectoryEntry(String iban) {
        if (isBlank(iban)) {
            return null;
        }
        try {
            return bankingProtocol.lookupBankDetails(iban);
        } catch (BankingException e) {
            log.debug("Local FinTS directory lookup unavailable during bank profile setup: {}",
                    e.getFailureReason());
            return null;
        }
    }

    private String bankCodeFromIban(String iban) {
        if (iban == null) {
            return null;
        }
        String normalized = iban.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        return normalized.matches("DE\\d{20}") ? normalized.substring(4, 12) : null;
    }

    private String normalizeBlz(String blz) {
        if (isBlank(blz)) {
            return null;
        }
        String normalized = blz.replaceAll("\\s+", "");
        return normalized.matches("\\d{8}") ? normalized : null;
    }

    private String normalizeBic(String bic) {
        String normalized = trimToNull(bic);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String fallbackBankName(String blz, String bic) {
        if (!isBlank(blz)) {
            return "BLZ " + blz;
        }
        return normalizeBic(bic);
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? trimToNull(second) : first.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
