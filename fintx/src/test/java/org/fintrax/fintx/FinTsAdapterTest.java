package org.fintrax.fintx;

import org.fintrax.model.Transaction;
import org.junit.jupiter.api.Test;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.exceptions.HBCI_Exception;
import org.kapott.hbci.exceptions.ProcessException;
import org.kapott.hbci.status.HBCIDialogStatus;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.status.HBCIMsgStatus;
import org.kapott.hbci.status.HBCIRetVal;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Value;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinTsAdapterTest {
    @Test
    void detectsInvalidPinResponsesAcrossDialogMessages() {
        HBCIMsgStatus invalidPin = new HBCIMsgStatus();
        invalidPin.globStatus.addRetVal(new HBCIRetVal(null, null, null,
                "9942", "Invalid PIN", null));
        HBCIDialogStatus dialog = new HBCIDialogStatus();
        dialog.msgStatus = new HBCIMsgStatus[]{new HBCIMsgStatus(), invalidPin};
        HBCIExecStatus status = new HBCIExecStatus();
        status.addDialogStatus("bank", dialog);

        assertTrue(FinTsAdapter.hasInvalidPinResponse(status));

        HBCIExecStatus successfulStatus = new HBCIExecStatus();
        HBCIDialogStatus successfulDialog = new HBCIDialogStatus();
        successfulDialog.msgStatus = new HBCIMsgStatus[]{new HBCIMsgStatus()};
        successfulStatus.addDialogStatus("bank", successfulDialog);
        assertFalse(FinTsAdapter.hasInvalidPinResponse(successfulStatus));
    }

    @Test
    void detectsInvalidPinResponseWrappedByHandlerInitialization() {
        HBCIMsgStatus invalidPin = new HBCIMsgStatus();
        invalidPin.globStatus.addRetVal(new HBCIRetVal(null, null, null,
                "9942", "Invalid PIN", null));
        ProcessException rejectedSync = new ProcessException("System ID sync failed", invalidPin);
        HBCI_Exception handlerInitializationFailure = new HBCI_Exception(rejectedSync);

        assertTrue(FinTsAdapter.hasInvalidPinResponse(handlerInitializationFailure));

        HBCIMsgStatus otherFailure = new HBCIMsgStatus();
        otherFailure.globStatus.addRetVal(new HBCIRetVal(null, null, null,
                "9999", "Other failure", null));
        assertFalse(FinTsAdapter.hasInvalidPinResponse(
                new HBCI_Exception(new ProcessException("Sync failed", otherFailure))));
    }

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
