(ns telegram.core.alpha.bot
  (:require
   [clojure.string :as str]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
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


(defn context-handler
  "`handlers` is a map of [ :kind -> handler ] "
  [handlers]
  (fn [{:keys [event] :as context}]
    (let [f (handlers (:kind event) (fn [_]))]
      (f context))))


(defn message-handler
  [command-handler etc]
  (fn
    [{:keys [event] :as context}]
    (let [text   (get-in event [:data "text"])]
      (when (string? text)
        (let [parsed (parse-text text)]
          (when (map? parsed)
            (let [{:keys [command args]} parsed]
              (if (and (string? command) (not (str/blank? command)))
                (command-handler context command args)
                (etc context)))))))))


;; ** command


(def ^:dynamic *no-command-permission-message* "🔴 커맨드 사용권한 없음")


(s/def :telegram.core.alpha.bot/telegram-context
  (s/with-gen
    #(instance? TelegramContext %)
    #(gen/fmap
      (fn [[token data]] (telegram-context token data))
      (gen/tuple (gen/string-alphanumeric) (gen/map (gen/string-alphanumeric) (gen/string-alphanumeric))))))


(s/def :telegram.core.alpha.bot.cmd-defs/cmd-name string?)
(s/def :telegram.core.alpha.bot.cmd-defs.config/handler
  (s/fspec :args (s/cat :context :telegram.core.alpha.bot/telegram-context
                        :args (s/* string?))
           :ret any?))
(s/def :telegram.core.alpha.bot.cmd-defs.config/requirements set?)
(s/def :telegram.core.alpha.bot.cmd-defs/config
  (s/keys :req-un [:telegram.core.alpha.bot.cmd-defs.config/handler]
          :opt-un [:telegram.core.alpha.bot.cmd-defs.config/requirements]))
(s/def :telegram.core.alpha.bot/cmd-defs (s/map-of :telegram.core.alpha.bot.cmd-defs/cmd-name :telegram.core.alpha.bot.cmd-defs/config))


(s/def :telegram.core.alpha.bot.role-tree/parents map?)
(s/def :telegram.core.alpha.bot.role-tree/descendants map?)
(s/def :telegram.core.alpha.bot.role-tree/ancestors map?)
(s/def :telegram.core.alpha.bot/role-tree
  (s/keys :req-un [:telegram.core.alpha.bot.role-tree/parents
                   :telegram.core.alpha.bot.role-tree/descendants
                   :telegram.core.alpha.bot.role-tree/ancestors]))


(s/fdef expand-cmd-defs
  :args (s/cat :cmd-defs :telegram.core.alpha.bot/cmd-defs)
  :ret :telegram.core.alpha.bot/cmd-defs)


(s/fdef context-command-handler
  :args (s/cat :cmd-defs :telegram.core.alpha.bot/cmd-defs :role-tree :telegram.core.alpha.bot/role-tree)
  :ret fn?)


;;


(defn allow?
  "Return ture if requirements is empty"
  [h requirements role]
  (every? #(isa? h role %) requirements))


(comment
  (and
    (allow? (make-hierarchy) #{} :user-1)
    (allow? (make-hierarchy) #{} :user-2)
    (allow? (make-hierarchy) #{} nil))


  (allow? (-> (make-hierarchy) (derive :alpha :admin)) #{:alpha} :user)
  (allow? (-> (make-hierarchy) (derive :alpha :admin)) #{:alpha} :admin)
  (allow? (-> (make-hierarchy) (derive :alpha :admin)) #{:alpha} :alpha)
  (allow? (-> (make-hierarchy) (derive :alpha :admin)) #{:admin} :alpha)
  )


(defn expand-cmd-defs
  [defs]
  (reduce
    (fn [ret [cmd-name {:keys [aliases] :as config}]]
      (let [config' (dissoc config :aliases)]
        (reduce #(assoc %1 %2 config')
                (assoc ret cmd-name config')
                aliases)))
    {}
    defs))


(defn context-command-handler
  [cmd-defs role-tree]
  (fn
    [context command args]
    (let [handler (get-in cmd-defs [command :handler])]
      (if (fn? handler)
        (if (allow? role-tree (get-in cmd-defs [command :requirements]) (:role context))
          (apply handler context args)
          (send-message context (str *no-command-permission-message* ": " command)))
        nil))))


(defn callback-query-handler
  [do-operaion]
  (fn
    [context]
    (let [callback-data (get-in context [:event :data "data"])
          [operation]   (str/split callback-data #"\s+" 2)
          op            (keyword operation)]
      (do-operaion context op))))


(set! *warn-on-reflection* false)
