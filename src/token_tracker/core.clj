(ns token-tracker.core
  (:gen-class)
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(def ^:private api-url "https://api.anthropic.com/v1/messages")
(def ^:private model "claude-opus-4-6")

(defn- api-key []
  (or (System/getenv "ANTHROPIC_API_KEY")
      (throw (ex-info "ANTHROPIC_API_KEY environment variable is not set" {}))))

(defn- send-messages [messages]
  (let [response (http/post api-url
                   {:headers {"x-api-key"         (api-key)
                              "anthropic-version"  "2023-06-01"
                              "content-type"       "application/json"}
                    :body    (json/generate-string
                               {:model      model
                                :max_tokens 16000
                                :messages   messages})
                    :as      :json
                    :coerce  :always})]
    (:body response)))

(defn- extract-text [response]
  (->> (:content response)
       (filter #(= (:type %) "text"))
       first
       :text))

(defn- print-usage [{:keys [input_tokens output_tokens]}]
  (let [total (+ input_tokens output_tokens)]
    (println (format "\n┌─ Token Usage ──────────────┐"))
    (println (format "│  Input:  %6d tokens     │" input_tokens))
    (println (format "│  Output: %6d tokens     │" output_tokens))
    (println (format "│  Total:  %6d tokens     │" total))
    (println (format "└────────────────────────────┘"))))

(defn chat-loop []
  (println "\nClaude Chat Wrapper")
  (println (str "Model: " model))
  (println "Type 'quit' or 'exit' to end the session.\n")
  (loop [messages []]
    (print "You: ")
    (flush)
    (when-let [input (read-line)]
      (when-not (contains? #{"quit" "exit"} (.trim input))
        (if (.isBlank input)
          (recur messages)
          (let [new-messages (conj messages {:role "user" :content input})]
            (try
              (let [response     (send-messages new-messages)
                    reply        (extract-text response)
                    usage        (:usage response)
                    next-messages (conj new-messages {:role "assistant" :content reply})]
                (println (str "\nClaude: " reply))
                (print-usage usage)
                (println)
                (recur next-messages))
              (catch Exception e
                (println (str "\nError: " (or (ex-message e) (.getMessage e))))
                (recur new-messages)))))))))

(defn -main [& _args]
  (chat-loop))
