(defproject puppetlabs/clj-ldap "0.1.5-SNAPSHOT"
  :description "Clojure ldap client (Puppet Labs's fork)."
  :url "https://github.com/puppetlabs/clj-ldap"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.unboundid/unboundid-ldapsdk "3.1.1"]]
  :profiles {:dev {:dependencies [[ch.qos.logback/logback-classic "1.1.7"]
                                  [ch.qos.logback/logback-core "1.1.7"]
                                  [jline "0.9.94"]
                                  [fs "1.1.2"]
                                  [org.apache.directory.server/apacheds-service "2.0.0-M23"]
                                  [org.apache.directory.api/api-ldap-schema-converter "1.0.0-RC1"]
                                  [org.apache.directory.api/api-ldap-schema-converter "1.0.0-RC1"]
                                  [org.slf4j/slf4j-api "1.7.21"]]}}

  :deploy-repositories [["releases" {:url "https://clojars.org/repo"
                                     :username :env/clojars_jenkins_username
                                     :password :env/clojars_jenkins_password
                                     :sign-releases false}]]
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"})
