# purescript-js-promise

[![Latest release](http://img.shields.io/github/release/purescript-contrib/purescript-js-promise.svg)](https://github.com/purescript-web/purescript-js-promise/releases)
[![Build status](https://github.com/purescript-contrib/purescript-js-promise/workflows/CI/badge.svg?branch=master)](https://github.com/purescript-web/purescript-js-promise/actions?query=workflow%3ACI+branch%3Amaster)
[![Pursuit](https://pursuit.purescript.org/packages/purescript-js-promise/badge)](https://pursuit.purescript.org/packages/purescript-js-promise)

Types and low-level implementations for JavaScript Promises.

## Java port

The Java `PromiseValue` supports pending settlement, adoption and observers.
The first resolution/rejection wins; `all` retains input order and `race` selects
the first settlement, including rejection. Reactions run eagerly on Java threads,
outside runtime locks, with a per-thread trampoline rather than a JS microtask queue.
See the [Java Promise contract](../javapurs/docs/ffi-runtime.md#promesses).

Run `./bin/test-runtime` for deterministic Java checks of pending values, races,
exceptions, cleanup and deep chains. It needs Node and a JDK. The sibling
`javapurs-js-promise-aff/bin/test-runtime` exercises the actual PureScript bridge.

`./bin/test` runs 13 PureScript checks in an isolated workspace. A single observed
chain covers assertions, rejections, losing race inputs and asynchronous cleanup;
the Java timer fixtures return genuinely pending promises. The runner awaits
completion and probes delayed failures, rejection, timeout and premature exit.
See the [port-suite recipe](../javapurs/docs/testing.md#suites-asynchrones-des-ports)
for the built-backend/TAST/Spago/JDK prerequisites and logs.

## Installation

```
spago install js-promise
```

## Documentation

Module documentation is [published on Pursuit](http://pursuit.purescript.org/packages/purescript-js-promise).
