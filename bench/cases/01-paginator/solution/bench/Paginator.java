package bench;

import java.util.List;

public final class Paginator {

    private Paginator() {}

    /** Pages start at 1. A page past the end is empty. */
    public static <T> List<T> page(List<T> items, int page, int size) {
        if (page < 1 || size < 1) throw new IllegalArgumentException("page and size start at 1");
        int from = (page - 1) * size;
        if (from >= items.size()) return List.of();
        int to = Math.min(from + size, items.size());
        return items.subList(from, to);
    }
}
