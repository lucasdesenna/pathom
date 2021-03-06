(ns com.wsscode.pathom.diplomat.http.fetch-test
  (:require
    [clojure.test :refer [is are testing]]
    [com.wsscode.async.async-cljs :refer [let-chan <!p go-catch <? <?maybe deftest-async]]
    [com.wsscode.pathom.diplomat.http :as p.http]
    [com.wsscode.pathom.diplomat.http.fetch :as fetch]
    [nubank.workspaces.core :as ws]))

(ws/deftest test-build-request-map
  (are [req out] (= (fetch/build-request-map req) out)
    {::p.http/url "/foo"}
    {:method "get"}

    {::p.http/url    "/foo"
     ::p.http/method ::p.http/post}
    {:method "post"}

    {::p.http/headers {:content-type "application/json"}}
    {:method  "get"
     :headers {:content-type "application/json"}}

    {::p.http/form-params  {:foo "bar"}
     ::p.http/content-type ::p.http/json}
    {:method  "post"
     :headers {:content-type "application/json"}
     :body    "{\"foo\":\"bar\"}"}

    {::p.http/form-params {:foo "bar"}
     ::p.http/as          ::p.http/json}
    {:method  "post"
     :headers {:content-type "application/json"}
     :body    "{\"foo\":\"bar\"}"}))

(deftest-async test-request-async
  (is (= (<? (fetch/request-async {::p.http/url "data:application/json,{\"json\": \"data\"}"
                                   ::p.http/as  ::p.http/json}))
         {::p.http/status 200
          ::p.http/body   {:json "data"}})))
