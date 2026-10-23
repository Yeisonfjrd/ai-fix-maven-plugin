package bench;

public final class Counter {

    private int count;

    public void increment() {
        count++;
    }

    public int value() {
        return count;
    }
}
