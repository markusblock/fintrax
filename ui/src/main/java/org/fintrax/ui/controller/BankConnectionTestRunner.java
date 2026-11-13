package org.fintrax.ui.controller;

import org.fintrax.fintx.BankingException;
import org.fintrax.fintx.BankingProtocol;
import org.fintrax.fintx.DiscoveredBankAccount;
import org.fintrax.model.Bank;
import org.fintrax.model.BankAccount;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

final class BankConnectionTestRunner {
    private BankConnectionTestRunner() {
    }

    static Result testAndDiscover(BankingProtocol protocol, BankAccount account, Bank bank, String pin,
                                  Consumer<String> progress) {
        try {
            boolean connected = protocol.validatePin(account, bank, pin, progress);
            if (!connected) {
                return new Result(false, List.of());
            }
            if (progress != null) {
                progress.accept("Starting bank account discovery");
            }
            return new Result(true, protocol.discoverAccounts(account, bank, pin, progress));
        } catch (BankingException e) {
            throw new CompletionException(e);
        }
    }

    record Result(boolean connected, List<DiscoveredBankAccount> accounts) {
    }
}
