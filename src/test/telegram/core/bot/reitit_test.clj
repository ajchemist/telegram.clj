(ns ^:live telegram.core.bot.reitit-test
  (:require
   [clojure.test :as test :refer [deftest is are testing]]
   [clojure.string :as str]
   [ring.util.response :as response]
   [ring.adapter.jetty :as jetty]
   [reitit.ring]
   [reitit.dev.pretty]
   [muuntaja.core :as muuntaja]
   [reitit.ring.middleware.muuntaja]
   [telegram.core.alpha :as tg]
   [telegram.core.alpha.bot :as tg.bot]
   [telegram.core.alpha.bot.reitit :as tg.bot.reitit]
   ))


(def bot-token (or (System/getenv "TESTBOT_TELEGRAM_TOKEN") (read-line)))


(def cmd-defs
  {"start"
   {:requirements #{}
    :handler
    (fn [context & _]
      (tg.bot/get-from-id context))}
   "echo"
   {:requirements #{}
    :handler
    (fn [context & args]
      (tg.bot/send-message
        context
        (str/join " " args)))}})


(def ring-handler
  (reitit.ring/ring-handler
    (reitit.ring/router
      [""
       ["/tg-handler"
        {:middleware [tg.bot.reitit/context-middleware]
         :muuntaja   (muuntaja/create (assoc-in muuntaja/default-options [:formats "application/json" :decoder 1 :decode-key-fn] false))
         :post
         (fn
           [{:keys [:telegram/context]}]
           (let [handle-message (tg.bot/message-handler (tg.bot/context-command-handler cmd-defs (make-hierarchy)) (fn [_]))
                 handler        (tg.bot/context-handler
                                  {:message        handle-message
                                   :edited_message handle-message})]
             (handler context))
           (prn context)
           (response/response nil))}]]
      {:exception reitit.dev.pretty/exception
       :data      {:telegram/bot-config {:token bot-token}
                   :muuntaja            muuntaja/instance
                   :middleware          [reitit.ring.middleware.muuntaja/format-middleware]}})
    (reitit.ring/routes
      (reitit.ring/create-default-handler))))


(comment
  (assoc-in muuntaja.format.json/format [:decoder 1 :decode-key-fn] false)
  (assoc-in muuntaja.format.json/format [:decoder-opts :decode-key-fn] false)
  )


(def server
  (jetty/run-jetty
    #'ring-handler
    {:host  "0.0.0.0"
     :port  8080
     :join? false}))


(comment
  (.stop server)


  (tg/set-webhook bot-token "https://364f-121-179-163-118.ngrok.io/tg-handler")
  )
