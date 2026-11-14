package org.fintrax.fintx;

import org.fintrax.model.Transaction;
import org.junit.jupiter.api.Test;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Value;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinTsAdapterTest {
    @Test
    void mapsAdditionalTransactionReferencesFromFinTs() throws BankingException {
        UmsLine line = new UmsLine();
        line.value = new Value(BigDecimal.ONE);
        line.endToEndId = "E2E-123";
        line.mandateId = "MANDATE-123";
        line.customerref = "CUSTOMER-123";
        line.primanota = "PRIMANOTA-123";
        line.gvcode = "166";
        line.other = new Konto();
        line.other.creditorid = "CREDITOR-123";

        Transaction transaction = new FinTsAdapter().mapUmsLine(line, 42L, true);

        assertEquals("E2E-123", transaction.getEndToEndId());
        assertEquals("MANDATE-123", transaction.getMandateId());
        assertEquals("CREDITOR-123", transaction.getCreditorId());
        assertEquals("CUSTOMER-123", transaction.getCustomerReference());
        assertEquals("PRIMANOTA-123", transaction.getPrimanota());
        assertEquals("166", transaction.getGvCode());
    }
}
