(ns telegram.core.alpha-test
  (:require
   [clojure.test :as test :refer [deftest is are testing]]
   [clojure.string :as str]
   [telegram.core.alpha :as tg]
   #_[rum.core :as rum]
   ))


;; C-c M-: cider-read-and-eval


(def telegram-token (System/getenv "TELEGRAM_TOKEN"))
(def telegram-to (System/getenv "TELEGRAM_TO"))
(def gh-sha (System/getenv "GITHUB_SHA"))


(deftest main
  (comment
    (tg/get-me telegram-token))


  (let [updates (tg/get-updates telegram-token {})]
    (is (map? updates))
    (is (get updates "ok")))
  )
