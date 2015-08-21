(ns cats.runner
  (:require [clojure.string :as str]
            [cljs.test :as test]
            [cats.core-spec]
            [cats.builtin-spec]
            [cats.applicative.validation]
            [cats.monad.exception-spec]
            [cats.monad.either-spec]
            [cats.monad.maybe-spec]
            [cats.monad.identity-spec]
            [cats.labs.continuation-spec]
            [cats.labs.channel-spec]
            [cats.labs.state-spec]
            [cats.labs.reader-spec]
            [cats.labs.writer-spec]))

(enable-console-print!)

(defn main
  []
  (test/run-tests
   (test/empty-env)
   'cats.core-spec
   'cats.builtin-spec
   'cats.applicative.validation
   'cats.monad.exception-spec
   'cats.monad.either-spec
   'cats.monad.maybe-spec
   'cats.monad.identity-spec
   'cats.labs.continuation-spec
   'cats.labs.state-spec
   'cats.labs.reader-spec
   'cats.labs.writer-spec
   'cats.labs.channel-spec
   ))

(defmethod test/report [:cljs.test/default :end-run-tests]
  [m]
  (if (test/successful? m)
    (set! (.-exitCode js/process) 0)
    (set! (.-exitCode js/process) 1)))

(set! *main-cli-fn* main)
