package bench;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InvoiceHiddenTest {

    @Test
    void booksPayNothingWhateverTheCase() {
        Invoice invoice = new Invoice();
        invoice.add("books", 50_000);
        invoice.add("Books", 20_000);
        assertEquals(0, invoice.tax());
    }

    @Test
    void unknownCategoryPaysTheGeneralRate() {
        Invoice invoice = new Invoice();
        invoice.add("Toys", 10_000);
        assertEquals(2_100, invoice.tax());
    }

    @Test
    void theTableItselfIgnoresCase() {
        assertEquals(105, new TaxTable().perMille("fOoD"));
    }
}
