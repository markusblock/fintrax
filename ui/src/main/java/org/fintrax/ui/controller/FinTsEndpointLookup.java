package org.fintrax.ui.controller;

import org.fintrax.fintx.BankingException;
import org.fintrax.fintx.BankDirectoryEntry;
import org.fintrax.service.BankService;

final class FinTsEndpointLookup {
    private FinTsEndpointLookup() {
    }

    static Result find(BankService bankService, String iban, String currentEndpoint, boolean matchedBankProfile) {
        boolean manualEndpoint = currentEndpoint != null && !currentEndpoint.isBlank();
        try {
            BankDirectoryEntry entry = bankService.lookupBankDirectoryEntry(iban);
            String endpoint = entry == null ? null : trimToNull(entry.pinTanAddress());
            if (endpoint == null) {
                String messageKey = manualEndpoint
                        ? "message.fints.lookupNotFoundManual" : "message.fints.lookupNotFound";
                return new Result(entry, null, messageKey, false, manualEndpoint);
            }
            String messageKey = matchedBankProfile || !manualEndpoint
                    ? "message.fints.lookupFound" : "message.fints.lookupFoundManual";
            return new Result(entry, endpoint, messageKey, true, manualEndpoint);
        } catch (BankingException exception) {
            String messageKey = switch (exception.getFailureReason()) {
                case INVALID_IBAN -> "message.sync.invalidIban";
                case INVALID_ENDPOINT -> "message.sync.endpointInvalid";
                default -> "message.fints.lookupFailed";
            };
            return new Result(null, null, messageKey, false, manualEndpoint);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    record Result(BankDirectoryEntry entry, String endpoint, String messageKey,
                  boolean found, boolean manualEndpoint) {
    }
}
