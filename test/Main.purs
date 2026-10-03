module Test.Main where

import Prelude

import Data.Either (Either(..))
import Data.Maybe (Maybe(..))
import Effect (Effect)
import Effect.Class (liftEffect)
import Effect.Console (log)
import Effect.Exception (error, message)
import Effect.Ref as Ref
import Promise as Promise
import Promise.Lazy as Lazy
import Promise.Rejection as Rejection
import Test.Assert as Assert

foreign import delay :: Int -> Effect (Promise.Promise Int)
foreign import failAfter :: Int -> Effect (Promise.Promise Int)

-- Observe both outcomes before asserting, so a catch cannot swallow an
-- assertion that a promise should have rejected. The returned child is awaited.
outcome :: forall a. Promise.Promise a -> Effect (Promise.Promise (Either String a))
outcome = Promise.thenOrCatch
  (pure <<< Promise.resolve <<< Right)
  (pure <<< Promise.resolve <<< Left <<< rejectionMessage)
  where
  rejectionMessage reason = case Rejection.toError reason of
    Just exception -> message exception
    Nothing -> "non-Error rejection"

deferred :: Effect
  { promise :: Promise.Promise Int
  , resolve :: Int -> Effect Unit
  , reject :: String -> Effect Unit
  }
deferred = do
  success <- Ref.new (\_ -> pure unit)
  failure <- Ref.new (\_ -> pure unit)
  promise <- Promise.new \resolve reject -> do
    Ref.write resolve success
    Ref.write reject failure
  pure
    { promise
    , resolve: \value -> Ref.read success >>= (_ $ value)
    , reject: \text -> Ref.read failure >>= (_ $ Rejection.fromError (error text))
    }

check :: forall a. Eq a => Show a => String -> a -> a -> Lazy.LazyPromise Unit
check label expected actual = liftEffect do
  Assert.assertEqual { expected, actual }
  log ("[OK] " <> label)

-- One observed chain owns every assertion and finalizer. The JVM test wrapper
-- waits for suite; main preserves the usual JavaScript entrypoint.
main :: Effect Unit
main = void $ suite >>= Promise.then_ (\_ -> log "Promise suite passed" $> Promise.resolve unit)

suite :: Effect (Promise.Promise Unit)
suite = Lazy.toPromise do
  resolved <- Lazy.fromPromise $ Promise.new (\res _ -> res "success")
  check "new/resolve" "success" resolved

  recovered <- Lazy.fromPromise $ Promise.new (\(_ :: String -> Effect Unit) reject -> reject (Rejection.fromError (error "fail")))
    >>= Promise.catch (\reason -> pure $ Promise.resolve case Rejection.toError reason of
      Just exception -> message exception
      Nothing -> "non-Error rejection")
  check "new/reject/catch" "fail" recovered

  -- Register all/race on pending values and settle in a chosen order, without
  -- assuming any ordering between JVM threads or wall-clock timers.
  first <- liftEffect deferred
  second <- liftEffect deferred
  both <- liftEffect $ Promise.all [ first.promise, second.promise ]
  liftEffect $ second.resolve 2 *> first.resolve 1
  ordered <- Lazy.fromPromise $ pure both
  check "all/pending/order" [ 1, 2 ] ordered

  failing <- liftEffect deferred
  remaining <- liftEffect deferred
  allFailed <- liftEffect $ Promise.all [ remaining.promise, failing.promise ] >>= outcome
  liftEffect $ failing.reject "all failed" *> remaining.resolve 10
  allResult <- Lazy.fromPromise $ pure allFailed
  check "all/rejection" (Left "all failed") allResult

  loser <- liftEffect deferred
  winner <- liftEffect deferred
  raced <- liftEffect $ Promise.race [ loser.promise, winner.promise ]
  loserResult <- liftEffect $ outcome loser.promise
  liftEffect $ winner.resolve 42 *> loser.reject "lost"
  raceResult <- Lazy.fromPromise $ pure raced
  observedLoser <- Lazy.fromPromise $ pure loserResult
  check "race/pending/loser observed" { winner: 42, loser: Left "lost" } { winner: raceResult, loser: observedLoser }

  ref1 <- liftEffect $ Ref.new 0
  finalized <- Lazy.fromPromise $ Promise.finally
    (delay 10 >>= Promise.then_ (\_ -> Ref.modify_ (_ + 1) ref1 $> Promise.resolve unit))
    (Promise.resolve "ok")
  count1 <- liftEffect $ Ref.read ref1
  check "finally/success/await cleanup" { value: "ok", count: 1 } { value: finalized, count: count1 }

  ref2 <- liftEffect $ Ref.new 0
  finalizedFailure <- Lazy.fromPromise $ Promise.finally
    (delay 10 >>= Promise.then_ (\_ -> Ref.modify_ (_ + 1) ref2 $> Promise.resolve unit))
    (Promise.reject (Rejection.fromError (error "fail")) :: Promise.Promise Int) >>= outcome
  count2 <- liftEffect $ Ref.read ref2
  check "finally/rejection/await cleanup" { value: Left "fail", count: 1 } { value: finalizedFailure, count: count2 }

  lazyResult <- Lazy.fromPromise $ Lazy.toPromise do
    v1 <- Lazy.new (\res _ -> res 10)
    pure (v1 + 20)
  check "Lazy/bind" 30 lazyResult

  lazyRecovered <- Lazy.fromPromise $ Lazy.toPromise $ Lazy.catch (\_ -> pure 99) do
    value <- Lazy.fromPromise (failAfter 10)
    pure (value + 1)
  check "Lazy/catch" 99 lazyRecovered

  ref3 <- liftEffect $ Ref.new 0
  lazyFinalized <- Lazy.fromPromise $ Lazy.toPromise $ Lazy.finally
    (Lazy.fromPromise (delay 10) *> liftEffect (Ref.modify_ (_ + 1) ref3))
    (pure "ok")
  count3 <- liftEffect $ Ref.read ref3
  check "Lazy/finally" { value: "ok", count: 1 } { value: lazyFinalized, count: count3 }

  lazyAll <- Lazy.fromPromise $ Lazy.toPromise $ Lazy.all [ pure 1, pure 2, pure 3 ]
  check "Lazy/all" [ 1, 2, 3 ] lazyAll

  waited <- Lazy.fromPromise (delay 10)
  check "timer/resolve" 10 waited
  timedFailure <- Lazy.fromPromise $ failAfter 10 >>= outcome
  check "timer/reject" (Left "timed out after 10ms") timedFailure
