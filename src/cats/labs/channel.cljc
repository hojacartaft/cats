;; Copyright (c) 2014-2015, Andrey Antukh
;; Copyright (c) 2014-2015, Alejandro Gómez
;; All rights reserved.
;;
;; Redistribution and use in source and binary forms, with or without
;; modification, are permitted provided that the following conditions
;; are met:
;;
;; 1. Redistributions of source code must retain the above copyright
;;    notice, this list of conditions and the following disclaimer.
;; 2. Redistributions in binary form must reproduce the above copyright
;;    notice, this list of conditions and the following disclaimer in the
;;    documentation and/or other materials provided with the distribution.
;;
;; THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
;; IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
;; OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
;; IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
;; INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
;; NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
;; DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
;; THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
;; (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
;; THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

(ns cats.labs.channel
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]]))
  (:require #?(:clj  [clojure.core.async :refer [go] :as a]
               :cljs [cljs.core.async :as a])
            [clojure.core.async.impl.dispatch :as dispatch]
            [clojure.core.async.impl.protocols :as impl]
            [cats.context :as ctx]
            [cats.core :as m]
            [cats.protocols :as p]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Monad definition
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn with-value
  "Simple helper that creates a channel and attach
  an value to it."
  ([value] (with-value value (a/chan)))
  ([value ch]
   (a/put! ch value)
   ch))

(defn channel?
  "Return true if a `c` is a channel."
  [c]
  (instance? #?(:clj  clojure.core.async.impl.channels.ManyToManyChannel
                :cljs cljs.core.async.impl.channels.ManyToManyChannel) c))

(def ^{:no-doc true}
  channel-monad
  (reify
    p/Functor
    (fmap [_ f mv]
      (let [ctx ctx/*context*
            c (a/chan 1 (map (fn [v]
                               (binding [ctx/*context* ctx]
                                 (f v)))))]
        (a/pipe mv c)
        c))

    p/Applicative
    (pure [_ v]
      (let [c (a/chan 1)]
        (a/put! c v)
        (a/close! c)
        c))

    (fapply [mn af av]
      (let [c (a/chan 1)]
        (go
          (let [af' (a/<! (a/reduce conj [] af))
                av' (a/<! (a/reduce conj [] av))
                ctx (p/get-context [])]
            (a/onto-chan c (p/fapply ctx af' av'))))
        c))

    p/Monad
    (mreturn [_ v]
      (let [c (a/chan)]
        (a/put! c v)
        c))

    (mbind [it mv f]
      (let [ctx ctx/*context*
            c (a/chan 1)]
        (a/go-loop []
          (if-let [v (a/<! mv)]
            (do
              (if (channel? v)
                (let [result (a/<! v)
                      result (f result)]
                  (if (channel? result)
                    (a/>! c (a/<! result))
                    (a/>! c result)))
                (let [result (f v)]
                  (if (channel? result)
                    (a/>! c (a/<! result))
                    (a/>! c result))))
              (recur))
            (a/close! c)))
        c))))

(extend-type #?(:clj  clojure.core.async.impl.channels.ManyToManyChannel
                :cljs cljs.core.async.impl.channels.ManyToManyChannel)
  p/Context
  (get-context [_] channel-monad))

