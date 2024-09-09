public class Timer {
    public static void timed(String name, CB cb) {
        System.out.printf("BEGIN %s%n", name);
        final long start = System.currentTimeMillis();
        cb.call();
        System.out.printf("END %s in %sms%n", name, System.currentTimeMillis() - start);
    }
}
