# Security properties of Svalbard backups

[Svalbard](../README.md) protects secret values using [secret
sharing](https://en.wikipedia.org/wiki/Secret_sharing), a well-known technique
for robust, distributed protection of secrets.  A user Alice ([Svalbard
client](CLIENT.md)) computes the shares, and stores them at various locations,
while the metadata of the sharing (describing how the sharing was computed and
where the shares are stored) is stored at a separate, distingushed party called
_cloud provider_ (CP), which should be disjoint from the parties offering
storage of shares.  It is assumed that Alice already has an account with CP,and
that this account offers strong authentication mechanisms (e.g. 2-factor
authentication).  Cloud provider can store data reliably, but should not learn
Alice's secrets.

To effectively protect against corruption at any single party involved, and even
against corruption of groups of parties, it uses a **two-level secret sharing**:

 * At first level Alice's secret value SV is split into two shares of an
   2-out-of-2 [XOR secret
   sharing](https://en.wikipedia.org/wiki/Secret_sharing#t_=_n), resulting in
   shares SH1 and SH2.
 * One of these shares, say SH1, is stored by the cloud provider CP, and the
   other one (SH2) is split into _n_ shares using _k_-out-of\-_n_ [Shamir's
   secret sharing](https://en.wikipedia.org/wiki/Shamir%27s_Secret_Sharing) for
   specific values of _k_ and _n_ chosen by Alice.  This results in shares
   SH2_1, ..., SH2_n, for each of which Alice picks a storage form and location.

The basic security of the backed up secret follows then from the properties of
the employed secret sharing.  However, the shares and the metadata are
distributed among multiple systems, they can be subject to corruption, either
accidental or malicious.  Such a corruption could result in an undetected
reconstruction of a wrong secret value, so Svalbard employs additionally simple
hash-based protections to make sure that any corruption is detected with high
probability.

## Protection against corruption of the second-level shares

To detect potential corruptions of the second-level shares, the metadata
contains also a salted hash of each share.  This assures the correctness of
the shares that enter the secret reconstruction stage, assuming that the
metadata is correct.  Moreover, this will detect also corruption of the
hashes in the metadata unless the cloud provider CP that stores the metadata
maliciously cooperates with the provider of storage of second-level shares.

More precisely, the metadata contains additionally a randomly picked string
`hash_salt` of length max. 255 bytes, and for each sencond-level share SH2_i,
_i=1..n_, the metadata contains `SaltedHash(SH2_i, hash_salt)`, with
`SaltedHash`-function defined as

```
  SaltedHash(share, salt) = SHA-256(length(salt) || salt || SH2_i)
```

where `length(hash_salt)` is one byte value denoting the length of `hash_salt`
in bytes, `||` denotes concatentation, and `SHA-256()` denotes [SHA-256 Secure
Hash Algorithm](https://tools.ietf.org/html/rfc6234#section-4.1).  Upon
retrieval of a share the corresponding salted hash value is verified, and only
consistent shares are accepted for the secret reconstruction step.

## Protection against corruption of the first-level shares

Even if the second-level shares were all correct and the first-level share SH2
was successfully reconstructed, a corruption of the share SH1 (which is stored
as a part of the metadata) could result in an undetected bit-flip or other
controlled modification of the reconstructed secret.  To detect a potential
corruption of SH1, the second-level sharing protects additionally a salted hash
of the original secret value SV.  That is, the input value for the second-level
sharing is not just SH2, but `SH2_with_hash` defined as follows:

```
  SV_hash = SaltedHash(SV, hash_salt)
  SH2_with_hash = SV_hash || SH2
```

This way after a reconstruction of the secret one can check, whether the
resulting value is indeed equal to the original secret: `SV_hash` and `SH2`
are extracted from `SH2_with_hash`, and then `SV_hash` is checked against
`SaltedHash(SH1 xor SH2, hash_salt)`.

## An example

Here is an example that lists all the major values involved in
the sharing of a secret value `SV`, with parameters _k_ , _n_:

```
  SH1 = Random(length(SV))
  SH2 = SV xor SH1                 // i.e. SV == SH1 xor SH2
  hash_salt = Random(SALT_LENGTH)  // max. 255 bytes
  SV_hash = SaltedHash(SV, hash_salt)
  SH2_with_hash = SV_hash || SH2

  // for each i=1..n SH2_i is a k-out-of-n share of SH2_with_hash
  SH2_i = ... // i-th share of SH2_with_hash
  location_i = ... // location where i-th share is stored
  SH2_i_hash = SaltedHash(SH2_i, hash_salt)
```

Each share `SH2_i` is stored at the corresponding location,
and the metadata stored at the cloud provider CP contains then:

 * `SH1`
 * `hash_salt`
 * _k_ , _n_, and other metadata about second-level sharing
 * per-share information, for each `i=1..n` :
   * `location_i`
   * `SH2_i_hash`
