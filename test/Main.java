    // test/Main.js uses timers; the JVM port resolves on creation and treats
    // the delay as the value, which keeps the Promise.all/race expectations.
    public static Object delay = (java.util.function.Function<Object, Object>) (ms) ->
        (java.util.function.Supplier<Object>) () ->
            __M$Promise_Internal.PromiseValue.resolved(ms);

    public static Object failAfter = (java.util.function.Function<Object, Object>) (ms) ->
        (java.util.function.Supplier<Object>) () ->
            __M$Promise_Internal.PromiseValue.rejected(new RuntimeException("timed out after " + ms + "ms"));
