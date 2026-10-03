    // Real pending promises. The awaited suite owns their completion; daemon
    // timer threads alone must never keep an incomplete test process alive.
    private static Object timer(int ms, boolean fail) {
        var promise = new __M$Promise_Internal.PromiseValue();
        var thread = new Thread(() -> {
            try {
                Thread.sleep(ms);
                if (fail) promise.rejectWith(new RuntimeException("timed out after " + ms + "ms"));
                else promise.resolveWith(ms);
            } catch (Throwable error) { promise.rejectWith(error); }
        }, "promise-test-timer");
        thread.setDaemon(true);
        thread.start();
        return promise;
    }
    public static Object delay = (java.util.function.Function<Object, Object>) (ms) ->
        (java.util.function.Supplier<Object>) () -> timer((Integer) ms, false);

    public static Object failAfter = (java.util.function.Function<Object, Object>) (ms) ->
        (java.util.function.Supplier<Object>) () -> timer((Integer) ms, true);
