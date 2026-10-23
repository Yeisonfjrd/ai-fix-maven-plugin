package bench;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InvoiceTest {

    @Test
    void foodPaysTheReducedRate() {
        Invoice invoice = new Invoice();
        invoice.add("Food", 10_000);
        assertEquals(1_050, invoice.tax());
    }
}
