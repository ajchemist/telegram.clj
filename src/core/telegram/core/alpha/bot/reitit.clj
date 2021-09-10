(ns telegram.core.alpha.bot.reitit
  (:require
   [reitit.core :as reitit]
   [reitit.ring]
   [telegram.core.alpha.bot :as tg.bot]
   ))


(defn context-request
  ([request]
   (context-request request {}))
  ([{:keys [body-params] :as request} {:keys [token]}]
   (let [context (tg.bot/telegram-context token body-params)]
     (assoc request :telegram/context
       (assoc context
         ::reitit/match (reitit.ring/get-match request))))))


(def context-middleware
  {:name ::context-middleware
   :compile
   (fn [{:keys [:telegram/bot-config]} _]
     {:pre [(map? bot-config)]}
     (fn
       [handler]
       (fn
         ([request] (-> request (context-request bot-config) (handler)))
         ([request respond raise] (handler request #(-> % (context-request bot-config) (respond)) raise)))))})
