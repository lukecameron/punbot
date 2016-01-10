(ns punbot.core
  (:require [gniazdo.core :as ws]
            [clj-http.client :as http]
            [clojure.data.json :as json]
            [clojure.string :as str]))

(def token (.trim (slurp "token")))

; creates an instance of the punbot application
(defn create-punbot []
  (atom {}))

(defn get-puns []
  (str/split (slurp "puns") #"\n"))

(defn get-random-pun []
  (rand-nth (get-puns)))

(defn send-pun [app chan msg]
  (ws/send-msg 
    (:socket @app)
    (json/write-str {:id 1, 
                     :type "message", 
                     :channel chan, 
                     :text (get-random-pun)})))

; callback for receiving data from websocket
(defn on-rec [app data]
  (let [api-data (json/read-str data :key-fn keyword)

        event-type (:type api-data)
        msg (:text api-data)
        self-id (get-in @app [:self :id])
        chan (:channel api-data)]

    (if (and (= event-type "message") (.contains msg (str "@" self-id)))
      (send-pun app chan msg))))


; heavily "inspired" by verma/clj-slackbot
; gets websocket URL and other team info from slack API
(defn get-team-info [token]
  (let [body (-> (http/get "https://slack.com/api/rtm.start" 
                           {:query-params {:token token, :no_unreads true}
                           :as :json})
                 :body
                 (json/read-str :key-fn keyword))]
    (if (:ok body)
      (select-keys body [:url :self :team :users :channels :groups :bots]))))

; connects to slack api, assocs state into app's atom
(defn start-punbot [app]
  (let [team-info (get-team-info token)
        socket (ws/connect (:url team-info) :on-receive #(on-rec app %))]
    (swap! app into (assoc team-info :socket socket))))

; disconnects the websocket and calls start-punbot again
(defn restart-punbot [app]
  (if-let [sock (:socket app)] (ws/close sock))
  (swap! app (constantly {}))
  (start-punbot app))

(defn -main []
  (let [app (create-punbot)] (start-punbot app)))

; to send a msg,
; (ws/send-msg socket (json/write-str {:id 1, :type "message", :channel "C0FQU3QHE", :text "hello, rtm api!"}))

