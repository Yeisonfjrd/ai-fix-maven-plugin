package bench;

public final class Counter {

    private static int count;

    public void increment() {
        count++;
    }

    public int value() {
        return count;
    }
}
