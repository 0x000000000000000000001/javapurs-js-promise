    // Runtime values and settlement. Reactions run eagerly on the settling (or
    // subscribing) thread, outside locks; this port does not emulate microtasks.
    private static final class PromiseResult {
        final boolean failed;
        final Object value;
        PromiseResult(boolean failed, Object value) { this.failed = failed; this.value = value; }
    }

    public static final class PromiseValue {
        // Compatibility view for Java FFI returning already-settled promises.
        // Read settled before the payload; writes belong only to this runtime.
        public volatile boolean settled;
        public volatile boolean failed;
        public volatile Object value;
        public volatile Object rejection;
        private boolean claimed;
        private PromiseResult result;
        private final java.util.List<java.util.function.Consumer<PromiseResult>> observers = new java.util.ArrayList<>();

        public static PromiseValue resolved(Object value) {
            if (value instanceof PromiseValue) return (PromiseValue) value;
            PromiseValue promise = new PromiseValue();
            promise.resolveWith(value);
            return promise;
        }

        public static PromiseValue rejected(Object rejection) {
            PromiseValue promise = new PromiseValue();
            promise.rejectWith(rejection);
            return promise;
        }

        private synchronized boolean claim() {
            if (claimed) return false;
            claimed = true;
            return true;
        }

        // Resolution claims the promise before adopting another pending promise:
        // a later reject/resolve cannot overtake that first decision.
        public void resolveWith(Object value) {
            if (!claim()) return;
            if (value == this) complete(new PromiseResult(true, new IllegalStateException("Promise resolved with itself")));
            else if (value instanceof PromiseValue) ((PromiseValue) value).observe(this::complete);
            else complete(new PromiseResult(false, value));
        }

        public void rejectWith(Object error) {
            if (claim()) complete(new PromiseResult(true, error));
        }

        private void complete(PromiseResult outcome) {
            java.util.List<java.util.function.Consumer<PromiseResult>> pending;
            synchronized (this) {
                if (result != null) return;
                result = outcome;
                failed = outcome.failed;
                if (failed) rejection = outcome.value; else value = outcome.value;
                settled = true; // Publish the complete payload, including null.
                pending = new java.util.ArrayList<>(observers);
                observers.clear();
            }
            // Enqueue the whole snapshot before draining it, so a reaction that
            // adds more work cannot overtake already registered observers.
            dispatch(() -> { for (var observer : pending) dispatch(() -> observer.accept(outcome)); });
        }

        private void observe(java.util.function.Consumer<PromiseResult> observer) {
            PromiseResult outcome;
            synchronized (this) {
                outcome = result;
                if (outcome == null) { observers.add(observer); return; }
            }
            dispatch(() -> observer.accept(outcome));
        }

        private PromiseValue then(Object success, Object failure) {
            PromiseValue next = new PromiseValue();
            observe(outcome -> {
                Object handler = outcome.failed ? failure : success;
                if (handler == null) {
                    if (outcome.failed) next.rejectWith(outcome.value); else next.resolveWith(outcome.value);
                    return;
                }
                try { next.resolveWith(force(apply(handler, outcome.value))); }
                catch (Throwable thrown) { next.rejectWith(thrown); }
            });
            return next;
        }

        private PromiseValue finish(Object cleanup) {
            PromiseValue next = new PromiseValue();
            observe(outcome -> {
                try {
                    PromiseValue.resolved(force(cleanup)).observe(cleaned -> {
                        PromiseResult finalResult = cleaned.failed ? cleaned : outcome;
                        if (finalResult.failed) next.rejectWith(finalResult.value); else next.resolveWith(finalResult.value);
                    });
                } catch (Throwable thrown) { next.rejectWith(thrown); }
            });
            return next;
        }
    }

    // A per-thread trampoline bounds synchronous chains without introducing a
    // scheduler. Callback exceptions are caught by their owning child promise.
    private static final ThreadLocal<java.util.ArrayDeque<Runnable>> reactions = new ThreadLocal<>();
    private static void dispatch(Runnable reaction) {
        java.util.ArrayDeque<Runnable> queue = reactions.get();
        if (queue != null) { queue.add(reaction); return; }
        queue = new java.util.ArrayDeque<>();
        reactions.set(queue);
        queue.add(reaction);
        try { while (!queue.isEmpty()) queue.remove().run(); }
        finally { reactions.remove(); }
    }

    // Collection coordinators own result order and completion, not the FFI
    // wrappers. Each input has one observer; no pending value is read directly.
    private static final class PromiseAll {
        final PromiseValue result = new PromiseValue();
        final Object[] values;
        int remaining;
        PromiseAll(int count) { values = new Object[count]; remaining = count; }
        void accept(int index, PromiseResult outcome) {
            if (outcome.failed) { result.rejectWith(outcome.value); return; }
            boolean complete;
            synchronized (this) { values[index] = outcome.value; complete = --remaining == 0; }
            if (complete) result.resolveWith(values);
        }
    }

    private static PromiseValue allPromises(Object[] inputs) {
        PromiseAll all = new PromiseAll(inputs.length);
        if (inputs.length == 0) all.result.resolveWith(all.values);
        for (int i = 0; i < inputs.length; i++) {
            final int index = i;
            ((PromiseValue) inputs[i]).observe(outcome -> all.accept(index, outcome));
        }
        return all.result;
    }

    private static PromiseValue racePromises(Object[] inputs) {
        PromiseValue result = new PromiseValue();
        for (Object input : inputs) ((PromiseValue) input).observe(outcome -> {
            if (outcome.failed) result.rejectWith(outcome.value); else result.resolveWith(outcome.value);
        });
        return result; // An empty race stays pending.
    }

    // Java calling convention for EffectFn: curried Functions followed by a
    // Supplier. Pure resolve/reject return the runtime value directly.
    private static Object apply(Object fn, Object value) { return ((java.util.function.Function<Object, Object>) fn).apply(value); }
    private static Object force(Object effect) {
        return effect instanceof java.util.function.Supplier ? ((java.util.function.Supplier<?>) effect).get() : effect;
    }

    public static Object $new = (java.util.function.Function<Object, Object>) executor ->
        (java.util.function.Supplier<Object>) () -> {
            PromiseValue promise = new PromiseValue();
            java.util.function.Function<Object, Object> resolve = value -> (java.util.function.Supplier<Object>) () -> { promise.resolveWith(value); return null; };
            java.util.function.Function<Object, Object> reject = error -> (java.util.function.Supplier<Object>) () -> { promise.rejectWith(error); return null; };
            try { force(apply(apply(executor, resolve), reject)); }
            catch (Throwable thrown) { promise.rejectWith(thrown); }
            return promise;
        };
    public static Object then_ = (java.util.function.Function<Object, Object>) success ->
        (java.util.function.Function<Object, Object>) promise -> (java.util.function.Supplier<Object>) () ->
            ((PromiseValue) promise).then(success, null);
    public static Object thenOrCatch = (java.util.function.Function<Object, Object>) success ->
        (java.util.function.Function<Object, Object>) failure -> (java.util.function.Function<Object, Object>) promise ->
            (java.util.function.Supplier<Object>) () -> ((PromiseValue) promise).then(success, failure);
    public static Object $catch = (java.util.function.Function<Object, Object>) failure ->
        (java.util.function.Function<Object, Object>) promise -> (java.util.function.Supplier<Object>) () ->
            ((PromiseValue) promise).then(null, failure);
    public static Object $finally = (java.util.function.Function<Object, Object>) cleanup ->
        (java.util.function.Function<Object, Object>) promise -> (java.util.function.Supplier<Object>) () ->
            ((PromiseValue) promise).finish(cleanup);
    public static Object resolve = (java.util.function.Function<Object, Object>) PromiseValue::resolved;
    public static Object reject = (java.util.function.Function<Object, Object>) PromiseValue::rejected;
    public static Object all = (java.util.function.Function<Object, Object>) inputs ->
        (java.util.function.Supplier<Object>) () -> allPromises((Object[]) inputs);
    public static Object race = (java.util.function.Function<Object, Object>) inputs ->
        (java.util.function.Supplier<Object>) () -> racePromises((Object[]) inputs);
