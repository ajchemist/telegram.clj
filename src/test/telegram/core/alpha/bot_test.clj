(ns telegram.core.alpha.bot-test
  (:require
   [clojure.test :as test :refer [deftest is are testing]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as stest]
   [clojure.spec.gen.alpha :as gen]
   [telegram.core.alpha.bot :as tg.bot]
   [clojure.test.check]
   ))


(comment
  (gen/generate (gen/fmap (fn [[token data]] (tg.bot/telegram-context token data)) (gen/tuple (gen/string-alphanumeric) (gen/map (gen/string-alphanumeric) (gen/string-alphanumeric)))))


  (gen/sample (s/gen :telegram.core.alpha.bot.cmd-defs.config/handler))
  (gen/sample (s/gen :telegram.core.alpha.bot.cmd-defs.config/requirements))
  (gen/sample (s/gen :telegram.core.alpha.bot.cmd-defs/config))
  (stest/check `tg.bot/context-command-handler)
  )


(stest/instrument `tg.bot/context-command-handler)


(deftest main
  (is (seq (try (tg.bot/context-command-handler {} {}) (catch Exception e (::s/problems (ex-data e))))))
  (is (fn? (tg.bot/context-command-handler {} (make-hierarchy))))


  (is
    (s/valid?
      :telegram.core.alpha.bot/cmd-defs
      (tg.bot/expand-cmd-defs
        {"cont10"
         {:aliases #{"연"}
          :handler (fn [_ & _])}})))
  )
