(ns telegram.core.alpha.bot
  (:require
   [clojure.string :as str]
   [clojure.spec.alpha :as spec]
   [telegram.core.alpha :as tg]
   )
  (:import
   java.time.Instant
   ))


(set! *warn-on-reflection* true)


;; https://core.telegram.org/bots/api
;; https://core.telegram.org/bots/api#available-types


;; * https://core.telegram.org/bots/api#update


(defn update-kind
  "At most one of the optional parameters can be present in any given update."
  [raw-update]
  (let [ks (keys (dissoc raw-update "update_id"))]
    (case (first ks)
      "message"              :message
      "edited_message"       :edited_message
      "channel_post"         :channel_post
      "edited_channel_post"  :edited_channel_post
      "inline_query"         :inline_query
      "chosen_inline_result" :chosen_inline_result
      "callback_query"       :callback_query
      "shipping_query"       :shipping_query
      "pre_checkout_query"   :pre_checkout_query
      "poll"                 :poll
      "poll_answer"          :poll_answer
      "my_chat_member"       :my_chat_member
      "chat_member"          :chat_member
      :unknown)))


{:message (fn [m] (update m "date" #(Instant/ofEpochSecond %)))}


;; * https://core.telegram.org/bots/api#message


;; ** /command


(defn parse-text
  [^String text]
  (let [[command args] (str/split text #"\s+" 2)
        [command]      (str/split command #"@" 2)]
    (when (str/starts-with? command "/")
      {:args    (when (string? args) (str/split args #"\s+"))
       :command (subs command 1)})))


(defn extract-command-part
  [message]
  (some-> (get message "text")
    (str/split #"\s+" 2)
    (first)
    (str/split #"@" 2)
    (first)))


(defn command?
  "Checks if message is a command with a name.
  /stars and /st are considered different."
  [message name]
  (let [command-part (extract-command-part message)]
    (= command-part (str "/" name))))


(defn extract-command
  [message]
  (let [command-part (extract-command-part message)]
    (when (str/starts-with? command-part "/")
      (subs command-part 1))))


;; * event & context


;; https://bottender.js.org/docs/en/api-telegram-context
;; https://bottender.js.org/docs/en/api-telegram-event


(defprotocol ITelegramContext
  (get-from-id [context])
  (get-chat-id [context] "https://github.com/Yoctol/bottender/blob/bd6e93343f7d2778cfc783deaa9ade9a166bd586/packages/bottender/src/telegram/TelegramContext.ts")
  (send-message [context message] [context message options] [context to message options])
  (answer-callback-query [context] [context options]))


(defrecord TelegramContext [token event]
  ITelegramContext
  (get-from-id
    [_]
    (get-in event [:data "from" "id"]))
  (get-chat-id
    [_]
    (let [kind (:kind event)]
      (cond
        (#{:message :edited_message :channel_post :edited_channel_post} kind)
        (get-in event [:data "chat" "id"]))))
  (send-message
    [context message]
    (tg/send-message token (get-chat-id context) message))
  (send-message
    [context message options]
    (tg/send-message token (get-chat-id context) message options))
  (send-message
    [_ to message options]
    (tg/send-message token to message options))
  (answer-callback-query
    [_]
    (tg/answer-callback-query token (get-in event [:data "id"])))
  (answer-callback-query
    [_ options]
    (tg/answer-callback-query token (get-in event [:data "id"]) options)))


(defn telegram-event
  [raw-update]
  (let [kind (update-kind raw-update)]
    (cond-> {:kind kind
             :raw-update raw-update}
      (not= kind :unknown) (assoc :data (get raw-update (name kind))))))


(defn telegram-context
  [token raw-update]
  (->TelegramContext
    token
    (telegram-event raw-update)))


(set! *warn-on-reflection* false)
