package bench;

import java.time.LocalDate;

public final class Age {

    private Age() {}

    /** Full years lived on `today`. */
    public static int years(LocalDate birth, LocalDate today) {
        return today.getYear() - birth.getYear();
    }
}
