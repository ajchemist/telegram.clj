(ns telegram.core.alpha
  {:references ["https://github.com/tonsky/grumpy/blob/master/src/grumpy/telegram.clj"]}
  (:require
   [clojure.string :as str]
   [clj-http.client :as http]
   [user.ring.alpha :as user.ring]
   ))


(defn- format-request-url
  [token method]
  (str "https://api.telegram.org/bot" token method))


(defn- request-url-transformer
  [{:keys [:telegram/token :telegram/method] :as req}]
  (update req :url #(or % (format-request-url token method))))


(def ^:dynamic *core-request-params*
  {:method             :post
   :as                 :json-string-keys
   :content-type       :json
   :connection-timeout 8899})


(def ^{:arglists '([request] [request response raise])}
  request
  (-> http/request
    (user.ring/wrap-transform-request request-url-transformer)
    (user.ring/wrap-transform-request
      (fn
        [request-params]
        (merge *core-request-params* request-params)))
    (user.ring/wrap-meta-response)))


(defn error-handle
  [e request-params]
  (let [{:keys [url]} (request-url-transformer request-params)]
    (cond
      (re-find #"Bad Request: message is not modified" (:body (ex-data e)))
      (println "Telegram request failed:" url (pr-str request-params))

      :else
      (do
        (println "Telegram request failed:" url (pr-str request-params))
        (throw e)))))


(defn client
  [request-params]
  (try
    (request request-params)
    (catch Exception e
      (error-handle e request-params))))


;; * util


(defn render-html-message
  [render components]
  (reduce
    (fn [ret elem]
      (cond
        (vector? elem) (str ret (render elem))
        (string? elem) (str ret (render elem))
        :else          ret))
    ""
    components))


;; * sugar


;; https://core.telegram.org/bots/api


(defn set-webhook
  [token webhook-url]
  (request
    {:telegram/token  token
     :telegram/method "/setWebhook"
     :form-params    {:url webhook-url}}))


(defn delete-webhook
  [token]
  (request
    {:telegram/token  token
     :telegram/method "/deleteWebhook"}))


(defn get-commands
  [token]
  (request
    {:telegram/token  token
     :telegram/method "/getMyCommands"}))


(defn set-commands
  [token commands]
  (request
    {:telegram/token  token
     :telegram/method "/getMyCommands"
     :form-params     {:commands commands}}))


(defn get-me
  [token]
  (request
    {:telegram/token  token
     :telegram/method "/getMe"}))


(defn get-updates
  [token {:keys [limit offset timeout allowed_updates]}]
  (request
    {:telegram/token  token
     :telegram/method "/getUpdates"
     :form-params     (cond-> {:offset  (or offset 0)
                               :limit   (or limit 100)
                               :timeout (or timeout 1)}
                        (vector? allowed_updates) (assoc :allowed_updates allowed_updates))}))


(defn send-message
  ([token to message]
   (send-message token to message nil))
  ([token to message options]
   (request
     {:telegram/token  token
      :telegram/method "/sendMessage"
      :form-params     (into
                         {:chat_id to
                          :text    message}
                         options)})))


(defn answer-callback-query
  ([token id]
   (answer-callback-query token id nil))
  ([token id options]
   (request
     {:telegram/token  token
      :telegram/method "/answerCallbackQuery"
      :form-params     (into
                         {:callback_query_id id}
                         options)})))


(comment
  )
