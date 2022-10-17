(ns web-steroid.reitit.alpha
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [reitit.ring]
   [reitit.middleware]
   [user.ring.alpha :as user.ring]
   [web-steroid.core.alpha.merge]
   [web-steroid.core.alpha.server-render :as sr]
   [web-steroid.core.alpha :as web-steroid]
   ))


(s/def :ssr/root-dir string?)


(defn index-route?
  [[path _data :as route]]
  (or (str/ends-with? path "/")
      (str/ends-with? path "/index.html")
      (str/ends-with? path "/index.htm")))


(def wrap-html-metadata
  {:name ::wrap-html-metadata
   :compile
   (fn
     [{{:keys [search]
        :or   {search (fn [request key]
                        (let [find-param (some-fn key)]
                          (find-param
                            request
                            (::web-steroid/path-config request)
                            (-> request (reitit.ring/get-match) :data))))}
        :as   wrap-opts} ::wrap-html-metadata
       :as               _data} _opts]
     (fn [handler]
       (web-steroid/wrap-html-metadata
         handler
         wrap-opts)))})


(def wrap-request-from-match-data
  {:name ::wrap-request-from-match-data
   :compile
   (fn [_data _opts]
     (fn [handler keys]
       (user.ring/wrap-transform-request
         handler
         (fn [request]
           (let [{:keys [data]} (reitit.ring/get-match request)]
             (reduce-kv
               (fn [ret k v]
                 (cond-> ret
                   (some? v) (assoc k v)))
               request
               (select-keys data keys)))))))})


(def wrap-path-config-edn
  {:name ::wrap-path-config-edn
   :compile
   (fn [{:keys [::path-config-edn-nf] :as _data} _opts]
     (fn [handler]
       (web-steroid/wrap-path-config-edn handler path-config-edn-nf)))})


(def wrap-path-parted-html
  {:name ::wrap-path-parted-html
   :compile
   (fn [{:keys [::path-parted-html-nf] :as _data} _opts]
     (fn self
       ([handler]
        (self handler nil))
       ([handler keys]
        (web-steroid/wrap-path-parted-html handler path-parted-html-nf keys))))})


(def wrap-render-webpack-asset
  {:name ::wrap-render-webpack-asset
   :compile
   (fn [{:keys [::webpack-asset-manifest-reference] :as _data} _opts]
     (fn
       [handler asset-name render!]
       (web-steroid/wrap-html-render-asset
         handler
         :html.head/contents-string
         render!
         webpack-asset-manifest-reference
         asset-name)))})


(def wrap-render-webpack-stylesheets
  {:name ::wrap-render-webpack-stylesheets
   :compile
   (fn [{:keys [::webpack-asset-manifest-reference] :as _data} _opts]
     (fn self
       ([handler asset-names]
        (self handler asset-names nil))
       ([handler asset-names attrs]
        (web-steroid/wrap-html-render-assets
          handler
          :html.head/contents-string
          (fn [_request asset-path sb]
            (sr/render-link-element!
              (-> attrs (update :rel #(or % "stylesheet")) (assoc :href asset-path))
              sb))
          webpack-asset-manifest-reference
          asset-names))))})


(def wrap-render-body-scripts
  {:name ::wrap-render-webpack-asset
   :compile
   (fn [{:keys [::body-script-modules-reference] :as _data} _opts]
     (fn self
       ([handler module-id]
        (self handler module-id {}))
       ([handler module-id attrs]
        (web-steroid/wrap-html-render-asset
          handler
          :html.body.post/contents-string
          (fn [_request module-assets sb]
            (run!
              (fn [{:keys [output-name]}]
                (sr/render-script-element!
                  (-> attrs (assoc :src output-name))
                  sb))
              module-assets))
          body-script-modules-reference
          module-id))))})


;; * chain


(def chain-html-render
  {:name ::chain-signup
   :compile
   (fn [data opts]
     (fn [handler]
       (reitit.middleware/chain
         [[wrap-request-from-match-data
           [:html-request?
            ::user.ring/path-component?
            ::web-steroid/path-parted-html?]]
          [wrap-path-config-edn]
          [wrap-path-parted-html]]
         handler data opts)))})
