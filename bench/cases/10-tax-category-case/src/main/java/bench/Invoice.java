package bench;

import java.util.ArrayList;
import java.util.List;

public final class Invoice {

    private record Line(String category, long cents) {}

    private final TaxTable taxes = new TaxTable();
    private final List<Line> lines = new ArrayList<>();

    public void add(String category, long cents) {
        lines.add(new Line(category, cents));
    }

    /** Total tax in cents. */
    public long tax() {
        long total = 0;
        for (Line line : lines) {
            total += line.cents() * taxes.perMille(line.category()) / 1000;
        }
        return total;
    }
}
