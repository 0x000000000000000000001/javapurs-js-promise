    // A rejection is an arbitrary Object. Error conversion recognizes Throwable
    // without changing its identity; strings/records use the bridge's coercion.
    public static Object fromError = (java.util.function.Function<Object, Object>) (error) -> error;

    public static Object _toError = (java.util.function.Function<Object, Object>) (just) ->
        (java.util.function.Function<Object, Object>) (nothing) ->
        (java.util.function.Function<Object, Object>) (rejection) ->
            rejection instanceof Throwable
                ? ((java.util.function.Function<Object, Object>) just).apply(rejection)
                : nothing;
