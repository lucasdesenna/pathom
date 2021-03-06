(ns com.wsscode.pathom.book.graphql.edn-converter
  (:require
    [cljs.reader :refer [read-string]]
    [cljs.pprint]
    [clojure.string :as str]
    [com.wsscode.pathom.book.app-types :as app-types]
    [com.wsscode.pathom.book.ui.codemirror :as codemirror]
    [com.wsscode.pathom.graphql :as gql]
    [fulcro.client :as fulcro]
    [fulcro.client.localized-dom :as dom]
    [fulcro.client.mutations :as mutations]
    [fulcro.client.primitives :as fp]
    [garden.selectors :as gs]
    [com.wsscode.pathom.viz.codemirror :as pcm]
    [goog.object :as gobj]))

(defn pretty-print-string [x]
  (str/replace (with-out-str (cljs.pprint/pprint x))
    #"'" ""))

(mutations/defmutation update-query [{:keys [ui/om-next-query]}]
  (action [{:keys [state ref]}]
    (let [gql (try
                (let [gql (-> om-next-query
                              read-string
                              (gql/query->graphql))]
                  (swap! state assoc-in (conj ref :ui/translate-error?) false)
                  gql)
                (catch :default _
                  (swap! state assoc-in (conj ref :ui/translate-error?) true)
                  (get-in @state (conj ref :ui/graphql-query))))]
      (swap! state (comp #(assoc-in % (conj ref :ui/om-next-query) om-next-query)
                         #(assoc-in % (conj ref :ui/graphql-query) gql)))))
  (refresh [_] [:ui/om-next-query]))

(fp/defsc GraphQlQueryTranslator
  [this {:ui/keys [om-next-query graphql-query translate-error?]} _ css]
  {:initial-state (fn [params]
                    (merge {::id                 (random-uuid)
                            :ui/om-next-query    "[]"
                            :ui/graphql-query    ""
                            :ui/translate-error? false}
                      params))
   :ident         [::id ::id]
   :query         [::id :ui/om-next-query :ui/graphql-query :ui/translate-error?]
   :css           [[:.container {:display       "grid" :grid-template-columns "1fr 20px 1fr"
                                 :margin-bottom "20px"}
                    [:pre {:margin "0"}]
                    [(gs/> "" :div) {:height   "500px"
                                     :position "relative"}]
                    [:.divisor-v {:position      "static"
                                  :width         "20px"
                                  :background    "#eee"
                                  :border        "1px solid #e0e0e0"
                                  :border-top    "0"
                                  :border-bottom "0"
                                  :z-index       "2"}]]
                   [:.translate-error {:color "#f00"}]]
   :css-include   [pcm/Editor]}
  (dom/div {:classes [:.container (if translate-error? :.translate-error)]}
    (codemirror/clojure {:force-index-update? true
                         :value               om-next-query
                         :onChange            #(fp/transact! this `[(update-query {:ui/om-next-query ~%})])})
    (dom/div :.divisor-v)
    (codemirror/graphql {:force-index-update? true
                         :value               graphql-query
                         ::pcm/options        {::pcm/readOnly true}})))

(def graphql-query-translator (fp/factory GraphQlQueryTranslator))

(defn ref-transact! [this props ident-attr tx]
  (fp/transact! (fp/get-reconciler this) [ident-attr (get props ident-attr)] tx))

(fp/defsc QueryTranslatorWithDemos
  [this {::keys [translator query-examples]} _ css]
  {:initial-state (fn [params]
                    {::id             (random-uuid)
                     ::query-examples '[["Simple" [:user/id :user/name]]
                                        ["Join" [{:app/me [:user/id :user/name]}]]
                                        ["Ident" [{[:user/login "wilkerlucio"] [:bio :url]}
                                                  {[:organization/login "clojure"] [:name :url]}
                                                  {[:github.repository/nameAndowner ["pathom" "wilkerlucio"]] [:id :name]}]]
                                        ["Join Parameters" [{(:app/allUsers {:limit 10})
                                                             [:user/id :user/name]}]]
                                        ["Aliases" [{(:property {::gql/alias "aliased" :another "param"})
                                                     [:subquery]}]]
                                        ["Enums" [{:viewer
                                                   [{(:starredRepositories {:first   10
                                                                            :orderBy {:field       STARRED_AT
                                                                                        :direction DESC}})
                                                     [{:nodes
                                                       [:id :name :updatedAt]}]}]}]]
                                        ["Union" [{:app/timeline
                                                   {:app/User     [:user/id :user/name]
                                                    :app/Activity [:activity/id :activity/title
                                                                   {:activity/user
                                                                    [:user/name]}]}}]]
                                        ["Inline Union" '[{:app/timeline
                                                           [:entity/id
                                                            (:user/name {::gql/on :app/User})
                                                            {(:activity/user {::gql/on :app/User})
                                                             [:user/email]}]}]]
                                        ["Recursive queries" [{:type
                                                               [:kind :name {:ofType 3}]}]]
                                        ["Mutation" [{(users/create {:user/id 123 :user/name "Foo"})
                                                      [:clientMutationId]}]]]
                     ::translator     (fp/get-initial-state GraphQlQueryTranslator params)})
   :ident         [::id ::id]
   :query         [::id ::query-examples
                   {::translator (fp/get-query GraphQlQueryTranslator)}]
   :css           [[:.button {:border        "1px solid #ccc"
                              :border-radius "4px"
                              :color         "#333"
                              :outline       "0"
                              :margin-right  "10px"
                              :margin-bottom "12px"
                              :padding       "6px 12px"}
                    [:&:hover {:background-color "#e6e6e6"
                               :border-color     "#adadad"}]]]
   :css-include   [GraphQlQueryTranslator]}
  (dom/div nil
    (dom/div nil
      (for [[title example] query-examples]
        (dom/button #js {:key       title
                         :className (:button css)
                         :onClick   #(ref-transact! this translator ::id
                                       `[(update-query {:ui/om-next-query ~(pretty-print-string example)})])}
          title)))
    (graphql-query-translator translator)))

(app-types/register-app "edn-graphql-converter"
  (fn [_]
    {::app-types/root (app-types/make-root QueryTranslatorWithDemos "graph-converter")}))

(app-types/register-app "inline-edn-graphql-converter"
  (fn [{::app-types/keys [node]}]
    (let [content (gobj/get node "innerText")
          Root    (app-types/make-root GraphQlQueryTranslator "graph-converter")]
      {::app-types/app  (fulcro/new-fulcro-client :initial-state (fp/get-initial-state Root {:ui/om-next-query (pretty-print-string (read-string content))}))
       ::app-types/root Root})))
