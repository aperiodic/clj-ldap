(ns clj-ldap.test.server
  "An embedded ldap server for unit testing"
  (:import
    [java.util HashSet]
    [net.sf.ehcache Cache]
    [net.sf.ehcache.config CacheConfiguration]
    [org.apache.directory.api.ldap.schema.manager.impl DefaultSchemaManager]
    [org.apache.directory.server.core DefaultDirectoryService]
    [org.apache.directory.server.core.partition.impl.btree.jdbm
     JdbmPartition
     JdbmIndex]
    [org.apache.directory.server.ldap LdapServer]
    [org.apache.directory.server.ldap.handlers.extended StartTlsHandler]
    [org.apache.directory.server.protocol.shared.transport TcpTransport])
  (:require [clj-ldap.client :as ldap]
            [fs.core :as fs]))

(defonce server (atom nil))

(defn- add-partition! 
  "Adds a partition to the embedded directory service"
  [service id dn]
  (let [partition (doto (JdbmPartition.)
                    (.setId id)
                    (.setSuffix dn))]
    (.addPartition service partition)
    partition))

(defn- add-index!
  "Adds an index to the given partition on the given attributes"
  [partition & attrs]
  (let [indexed-attrs (HashSet.)]
    (doseq [attr attrs]
      (.add indexed-attrs (JdbmIndex. attr)))
    (.setIndexedAttributes partition indexed-attrs)))

(defn- start-ldap-server
  "Start up an embedded ldap server"
  [port ssl-port]
  (let [work-dir (fs/temp-dir)
        directory-service (doto (DefaultDirectoryService.)
                            (.setShutdownHookEnabled true)
                            (.setWorkingDirectory work-dir))
        ldap-transport (TcpTransport. port)
        ssl-transport (doto (TcpTransport. ssl-port)
                        (.setEnableSSL true))
        ldap-server (doto (LdapServer.)
                      (.setDirectoryService directory-service)
                      (.setAllowAnonymousAccess true)
                      (.addExtendedOperationHandler (StartTlsHandler.))
                      (.setTransports
                       (into-array [ldap-transport ssl-transport])))]
    (-> (add-partition! directory-service
                        "clojure" "dc=alienscience,dc=org,dc=uk")
        (add-index! "objectClass" "ou" "uid"))
    (.startup directory-service)
    (.start ldap-server)
    [directory-service ldap-server]))

(defn- add-toplevel-objects!
  "Adds top level objects, needed for testing, to the ldap server"
  [connection]
  (ldap/add connection "dc=alienscience,dc=org,dc=uk"
            {:objectClass ["top" "domain" "extensibleObject"]
             :dc "alienscience"})
  (ldap/add connection "ou=people,dc=alienscience,dc=org,dc=uk"
            {:objectClass ["top" "organizationalUnit"]
             :ou "people"})
  (ldap/add connection
            "cn=Saul Hazledine,ou=people,dc=alienscience,dc=org,dc=uk"
            {:objectClass ["top" "Person"]
             :cn "Saul Hazledine"
             :sn "Hazledine"
             :description "Creator of bugs"}))

(defn stop!
  "Stops the embedded ldap server"
  []
  (if @server
    (let [[directory-service ldap-server] @server]
      (reset! server nil)
      (.stop ldap-server)
      (.shutdown directory-service))))

(defn start!
  "Starts an embedded ldap server on the given port"
  [port ssl-port]
  (stop!)
  (reset! server (start-ldap-server port ssl-port))
  (let [conn (ldap/connect {:host {:address "localhost" :port port}})]
    (add-toplevel-objects! conn)))
