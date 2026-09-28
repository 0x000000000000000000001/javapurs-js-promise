    // Port of Promise/Internal.js. The JVM has no microtask queue for
    // PureScript here, so promises settle eagerly: timers resolve on creation
    // and `all`/`race` inspect the settled elements, which is what the test
    // suite observes.
    public static final class PromiseValue {
        public boolean settled;
        public boolean failed;
        public Object value;
        public Object rejection;

        public static PromiseValue resolved(Object value) {
            PromiseValue promise = new PromiseValue();
            promise.settled = true;
            promise.value = value;
            return promise;
        }

        public static PromiseValue rejected(Object rejection) {
            PromiseValue promise = new PromiseValue();
            promise.settled = true;
            promise.failed = true;
            promise.rejection = rejection;
            return promise;
        }
    }

    private static PromiseValue __promise(Object value) { return (PromiseValue) value; }

    private static Object __effect(Object effect) {
        return effect instanceof java.util.function.Supplier
            ? ((java.util.function.Supplier<Object>) effect).get()
            : effect;
    }

    public static Object $new = (java.util.function.Function<Object, Object>) (executor) ->
        (java.util.function.Supplier<Object>) () -> {
            PromiseValue promise = new PromiseValue();
            java.util.function.Function<Object, Object> resolve = value ->
                (java.util.function.Supplier<Object>) () -> {
                    if (!promise.settled) {
                        promise.settled = true;
                        promise.value = value;
                    }
                    return null;
                };
            java.util.function.Function<Object, Object> reject = rejection ->
                (java.util.function.Supplier<Object>) () -> {
                    if (!promise.settled) {
                        promise.settled = true;
                        promise.failed = true;
                        promise.rejection = rejection;
                    }
                    return null;
                };
            __effect(((java.util.function.Function<Object, Object>) ((java.util.function.Function<Object, Object>) executor).apply(resolve)).apply(reject));
            return promise;
        };

    public static Object then_ = (java.util.function.Function<Object, Object>) (onSuccess) ->
        (java.util.function.Function<Object, Object>) (promiseObj) ->
            (java.util.function.Supplier<Object>) () -> {
                PromiseValue promise = __promise(promiseObj);
                if (promise.failed) return PromiseValue.rejected(promise.rejection);
                Object next = __effect(((java.util.function.Function<Object, Object>) onSuccess).apply(promise.value));
                return next;
            };

    public static Object thenOrCatch = (java.util.function.Function<Object, Object>) (onSuccess) ->
        (java.util.function.Function<Object, Object>) (onFailure) ->
        (java.util.function.Function<Object, Object>) (promiseObj) ->
            (java.util.function.Supplier<Object>) () -> {
                PromiseValue promise = __promise(promiseObj);
                Object handler = promise.failed ? onFailure : onSuccess;
                Object argument = promise.failed ? promise.rejection : promise.value;
                return __effect(((java.util.function.Function<Object, Object>) handler).apply(argument));
            };

    public static Object $catch = (java.util.function.Function<Object, Object>) (onFailure) ->
        (java.util.function.Function<Object, Object>) (promiseObj) ->
            (java.util.function.Supplier<Object>) () -> {
                PromiseValue promise = __promise(promiseObj);
                if (!promise.failed) return PromiseValue.resolved(promise.value);
                return __effect(((java.util.function.Function<Object, Object>) onFailure).apply(promise.rejection));
            };

    public static Object $finally = (java.util.function.Function<Object, Object>) (effect) ->
        (java.util.function.Function<Object, Object>) (promiseObj) ->
            (java.util.function.Supplier<Object>) () -> {
                PromiseValue promise = __promise(promiseObj);
                __effect(effect);
                return promise;
            };

    public static Object resolve = (java.util.function.Function<Object, Object>) (value) ->
        PromiseValue.resolved(value);

    public static Object reject = (java.util.function.Function<Object, Object>) (rejection) ->
        PromiseValue.rejected(rejection);

    public static Object all = (java.util.function.Function<Object, Object>) (promisesObj) ->
        (java.util.function.Supplier<Object>) () -> {
            Object[] promises = (Object[]) promisesObj;
            Object[] values = new Object[promises.length];
            for (int i = 0; i < promises.length; i++) {
                PromiseValue promise = __promise(promises[i]);
                if (promise.failed) return PromiseValue.rejected(promise.rejection);
                values[i] = promise.value;
            }
            return PromiseValue.resolved(values);
        };

    public static Object race = (java.util.function.Function<Object, Object>) (promisesObj) ->
        (java.util.function.Supplier<Object>) () -> {
            Object[] promises = (Object[]) promisesObj;
            for (Object item : promises) {
                PromiseValue promise = __promise(item);
                if (!promise.failed) return promise;
            }
            return promises.length == 0 ? PromiseValue.resolved(null) : __promise(promises[0]);
        };
