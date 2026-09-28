    // Port of Promise/Rejection.js. A rejection is the JavaScript error object,
    // which this backend represents as a Throwable.
    public static Object fromError = (java.util.function.Function<Object, Object>) (error) -> error;

    public static Object _toError = (java.util.function.Function<Object, Object>) (just) ->
        (java.util.function.Function<Object, Object>) (nothing) ->
        (java.util.function.Function<Object, Object>) (rejection) ->
            rejection instanceof Throwable
                ? ((java.util.function.Function<Object, Object>) just).apply(rejection)
                : nothing;
