(ns clj-ldap.test.server
  "An embedded ldap server for unit testing"
  (:import
    [java.io File]
    [java.util HashSet]
    [net.sf.ehcache Cache CacheManager]
    [net.sf.ehcache.config CacheConfiguration SizeOfPolicyConfiguration]
    [org.apache.directory.api.ldap.model.name Dn]
    [org.apache.directory.api.ldap.schema.manager.impl DefaultSchemaManager]
    [org.apache.directory.server.constants ServerDNConstants]
    [org.apache.directory.server.core DefaultDirectoryService]
    [org.apache.directory.server.core.api InstanceLayout]
    [org.apache.directory.server.core.api.schema SchemaPartition]
    [org.apache.directory.server.core.partition.impl.btree.jdbm
     JdbmPartition
     JdbmIndex]
    [org.apache.directory.server.core.partition.ldif LdifPartition]
    [org.apache.directory.server.core.shared DefaultDnFactory]
    [org.apache.directory.server.ldap LdapServer]
    [org.apache.directory.server.ldap.handlers.extended StartTlsHandler]
    [org.apache.directory.server.protocol.shared.transport TcpTransport])
  (:require [clj-ldap.client :as ldap]
            [fs.core :as fs]))

(defonce server (atom nil))
(defonce cache-manager (CacheManager/newInstance))

(def test-base-dn "dc=alienscience,dc=org,dc=uk")

(defn- new-dn
  [dn-str]
  (Dn. (into-array String [dn-str])))

(defn new-cache
  [nombre]
  (let [cache (Cache.
                (doto (CacheConfiguration.)
                  (.setName (str nombre))
                  ;; totally arbitrary limit of 16 MiB
                  (.setMaxBytesLocalHeap (* 16 1024 1024))
                  (.sizeOfPolicy (doto (SizeOfPolicyConfiguration.)
                                   (.setMaxDepth (int 1e6))))))]
    ;; the cache manager initializes the cache for us
    (.addCache cache-manager cache)
    (.getCache cache-manager nombre)))

(defn- new-partition
 [schema-manager dn-factory partitions-dir id suffix-dn]
  (doto (JdbmPartition. schema-manager dn-factory)
    (.setId id)
    (.setSuffixDn suffix-dn)
    (.setPartitionPath (.toURI (File. partitions-dir id)))))

(defn- add-indices!
  "Adds indices to the given partition on the given attributes"
  [partition & attrs]
  (let [indexed-attrs (HashSet.)]
    (doseq [attr attrs]
      (.add indexed-attrs (JdbmIndex. attr false)))
    (.setIndexedAttributes partition indexed-attrs)))

(defn- start-ldap-server
  "Start up an embedded ldap server"
  [port ssl-port]
  (let [working-dir (InstanceLayout. (fs/temp-dir))
        partitions-dir (.getPartitionsDirectory working-dir)
        schema-manager (DefaultSchemaManager.)
        dn-factory (DefaultDnFactory.
                     schema-manager
                     (new-cache "clj-ldap test dn factory"))
        mk-part (partial new-partition schema-manager dn-factory partitions-dir)
        ds (doto (DefaultDirectoryService.) ; hashtag Java.png
             (.setShutdownHookEnabled true)
             (.setAllowAnonymousAccess true)
             (.setInstanceLayout working-dir)
             (.setSchemaManager schema-manager)
             (.setDnFactory dn-factory)
             (.setSystemPartition
               (mk-part "system" (new-dn (str ServerDNConstants/SYSTEM_DN))))
             (.setSchemaPartition
               (doto (SchemaPartition. schema-manager)
                 (.setWrappedPartition
                   (doto (LdifPartition. schema-manager dn-factory)
                     (.setPartitionPath
                       (.toURI (File. partitions-dir "schema"))))))))
        ssl-transport (doto (TcpTransport. ssl-port)
                        (.setEnableSSL true))
        ldap (doto (LdapServer.)
               (.setDirectoryService ds)
               (.addExtendedOperationHandler (StartTlsHandler.))
               (.setTransports
                 (into-array [(TcpTransport. port) ssl-transport])))]
    (->> (doto (mk-part "test-data" (new-dn test-base-dn))
           (add-indices! "objectClass" "ou" "uid"))
      (.addPartition ds))
    (.startup ds)
    (.start ldap)
    [ds ldap]))

(defn- add-toplevel-objects!
  "Adds top level objects, needed for testing, to the ldap server"
  [connection]
  (ldap/add connection test-base-dn
            {:objectClass ["top" "domain" "extensibleObject"]
             :dc "alienscience"})
  (ldap/add connection (str "ou=people," test-base-dn)
            {:objectClass ["top" "organizationalUnit"]
             :ou "people"})
  (ldap/add connection (str "cn=Saul Hazledine,ou=people," test-base-dn)
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
