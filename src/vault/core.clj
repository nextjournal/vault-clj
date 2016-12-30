(ns vault.core
  "Core protocols for interacting with the Vault API."
  (:import
    java.net.URI))


;; ## Client Protocols

(defprotocol Client
  "General protocol for interacting with Vault."

  (authenticate!
    [client auth-type credentials]
    "Updates the client's internal state by authenticating with the given
    credentials. Possible arguments:

    - `:token \"...\"`
    - `:userpass {:username \"user\", :password \"hunter2\"}`
    - `:app-id {:app \"foo-service-dev\", :user \"...\"}`")

  (status
    [client]
    "Returns the health status of the Vault server."))


(defprotocol TokenManager
  "Token management interface supported by the \"token\" auth backend."

  (create-token!
    [client]
    [client opts]
    "Creates a new token. With no arguments, this creates a child token that
    inherits the policies and settings from the current token. Options passed
    in the map may include:

    - `:id` The ID of the client token. Can only be specified by a root token.
      Otherwise, the token ID is a randomly generated UUID.
    - `:display-name` The display name of the token. Defaults to \"token\".
    - `:meta` Map of string metadata to attach to the token. This will appear
      in the audit logs.
    - `:no-parent` If true and set by a root caller, the token will not have
      the parent token of the caller. This creates a token with no parent.
    - `:policies` Set of policies to issue the token with. This must be a
      subset of the current token's policies, unless it is a root token.
    - `:no-default-policy` If true the default policy will not be contained in
      this token's policy set.
    - `:num-uses` The maximum uses for the given token. This can be used to
      create a one-time-token or limited use token. Defaults to 0, which has no
      limit to the number of uses.
    - `:renewable` Boolean indicating whether the token should be renewable.
    - `:ttl` The TTL period of the token, provided as \"1h\", where hour is the
      largest suffix. If not provided, the token is valid for the default lease
      TTL, or indefinitely if the root policy is used.
    - `:explicit-max-ttl` If set, the token will have an explicit max TTL set
      upon it. This maximum token TTL cannot be changed later, and unlike with
      normal tokens, updates to the system/mount max TTL value will have no
      effect at renewal time -- the token will never be able to be renewed or
      used past the value set at issue time.
    - `:wrap-ttl` Returns a wrapped response with a wrap-token valid for the
      given number of seconds.")

  (lookup-token
    [client]
    [client token]
    "Returns information about the given token, or the client token if not
    specified.")

  (lookup-accessor
    [client token-accessor]
    "Fetch the properties of the token associated with the accessor, except the
    token ID. This is meant for purposes where there is no access to token ID
    but there is need to fetch the properties of a token.")

  (renew-token
    [client]
    [client token]
    "Renews a lease associated with a token. This is used to prevent the
    expiration of a token, and the automatic revocation of it. Token renewal is
    possible only if there is a lease associated with it.")

  (revoke-token!
    [client]
    [client token]
    "Revokes a token and all child tokens. When the token is revoked, all
    secrets generated with it are also revoked.")

  (revoke-accessor!
    [client]
    [client token-accessor]
    "Revoke the token associated with the accessor and all the child tokens.
    This is meant for purposes where there is no access to token ID but there
    is need to revoke a token and its children."))


(defprotocol LeaseManager
  "Lease management for dynamic secrets"

  (list-leases
    [client] ; TODO: opts to filter? return type?
    "Lists the currently leased secrets this client knows about.")

  (renew-lease
    [client lease-id]
    "Renews the identified secret lease. Returns a map containing the
    extended `:lease-duration` and whether the lease is `:renewable`.")

  (revoke-lease!
    [client lease-id]
    "Revokes the identified secret lease. Returns nil on success."))


(defprotocol SecretClient
  "Basic API for listing, reading, and writing secrets."

  (list-secrets
    [client path]
    "List the secrets located under a path.")

  (read-secret
    [client path]
    [client path opts]
    "Reads a secret from a path. Returns the full map of stored secret data.
    Additional options may include:

    - `:renew` whether or not to renew this secret when the lease is near
      expiry.
    - `:callback` function to call with updated secret data when renewing the
      secret lease.")

  (write-secret!
    [client path data]
    "Writes secret data to a path. `data` should be a map. Returns a
    boolean indicating whether the write was successful.")

  (delete-secret!
    [client path]
    "Removes secret data from a path. Returns a boolean indicating whether the
    deletion was successful."))


(defprotocol WrappingClient
  "Secret wrapping API for exchanging limited-use tokens for wrapped data."

  (wrap!
    [client data] ; TODO: how to set TTL?
    "Wraps the given user data in a single-use wrapping token.")

  (unwrap!
    [client wrap-token]
    "Returns the original response wrapped by the given token."))



;; ## Client Construction

(defmulti new-client
  "Constructs a new Vault client from a URI by dispatching on the scheme. The
  client will be returned in an initialized but not started state."
  (fn dispatch
    [uri]
    (.getScheme (URI. uri))))


(defmethod new-client :default
  [uri]
  (throw (IllegalArgumentException.
           (str "Unsupported Vault client URI scheme: " (pr-str uri)))))
