# Svalbard: a long-term secret backup system

Svalbard is a distributed backup system for secret data, like passwords,
encryption keys, Bitcoin keys, etc., intended for long-term protection of that
data.  It assures a reasonable confidentiality, while being also easy-to-use.
In particular, the scheme does not require any additional account or password,
and is resilient against failures or compromise of components of the system.
The system leverages the trust/identification mechanisms present at various
components from the real world to provide a solution that is both usable and
secure.

If you are interested in Svalbard, you may consider joining our [mailing
list svalbard-users@googlegroups.com](https://groups.google.com/forum/#!forum/svalbard-users).

**DISCLAIMER** Svalbard is not an officially supported Google product.

#### Current status

Svalbard is an experimental system under development.  The existing code
provides basic server functionality and a library for client side operations,
which allows for experimenting, see
[sharing_test](client/testing/sharing_test.sh) and
[SvalbardClientCli](client/java/src/test/java/com/google/security/svalbard/client/SvalbardClientCli.java)
for an example and more info on how to use the code.  However, the system is far
from complete, and several actions are planned for the coming months:

 * support for printed and peer-managed shares
 * integration with real secondary communication channels
 * encapsulation of client functionality in an app for mobile devices
 * launch of experimental Svalbard servers

Please don't hesitate to contact us if you have any questions and/or you'd like
to [contribute](CONTRIBUTING.md).


## Svalbard overview

### Scenario, requirements, and assumptions

We consider the following scenario:

 * Alice has a short high-value secret value SV that should be backed up
   (e.g. an encryption key, bitcoin key, password)
 * Alice has an account with a cloud provider CP
 * CP can store data reliably, but doesn’t have Alice’s full trust wrt. privacy
 * Alice has a trusted device that can process secret data

A back-up solution should have the following features and properties:

 1. **long-term storage**: the secrets should be stored for many (5+) years

 2. **easy setup and storage**: low overhead for setting up and storage ---
    ideally just a simple multiple-choice setup, and a push of a button in an
    app (no need for additional accounts/passwords/setup)

 3. **easy testability and recovery**: test whether the secret is recoverable
    (optionally with some resilience measure) and the actual recovery of the
    secret should happen also with a single push of a button, without need for
    logging in to 3rd-party services/accounts;

 4. **resilience against hardware failures**: a damage/failure/loss of a single
    (or even of multiple) hardware piece does not break the recovery

 5. **resilience against provider blackouts and failures**: a blackout/failure
    of a single (or even of multiple) provider(s) does not break the recovery

 6. **security against distributed attacks**: a successful attack on a subset of
    the components involved does not allow for recovery of the key. (however,
    not all components are equally trusted or distrusted, cf. assumptions above)


### Svalbard backup: basically a two-level secret sharing

Svalbard is based on so-called [secret
sharing](https://en.wikipedia.org/wiki/Secret_sharing), a well-known technique
for robust, distributed protection of secrets.  To effectively protect against
corruption at any single party involved, and even against corruption of groups
of parties, it uses a **two-level secret sharing**:

 * At first level Alice's secret value SV is split into two shares of an
   2-out-of-2 [XOR secret
   sharing](https://en.wikipedia.org/wiki/Secret_sharing#t_=_n), resulting in
   shares SH1 and SH2.
 * One of these shares, say SH1, is stored by the cloud provider CP, and the
   other one (SH2) is split into _n_ shares using _k_-out-of\-_n_ [Shamir's
   secret sharing](https://en.wikipedia.org/wiki/Shamir%27s_Secret_Sharing) for
   specific values of _k_ and _n_ chosen by Alice.  This results in shares
   SH2_1, ..., SH2_n, for each of which Alice picks a storage form and location.

The second-level shares are then stored in a distributed way, while the
metadata of the sharing (i.e. number of shares and their storage locations) are
stored by the cloud provider CP (together with the share SH1).  Such
distribution of the shares and the metadata guarantees that no single party gets
any information about the secret value SV (except for the length of SV).

Svalbard distinguishes three forms of share storage (but other forms can be
added in the future):

  * **printed copy**: the share is "encoded" in a physical object, e.g. printed
    on a piece of paper (as a QR code, or as a Base64-encoded sequence in an
    OCR-friendly font), or as a 3d-printed sculpture, and kept in a safe place,
    for example in a binder with Alice's other important documents.
  * **peer device**: the share is stored on a different device (e.g. another
    device of the user, or a device of a friend or a family member), via NFC or
    Bluetooth; this requires co-presence and active participation of the peer
    device.
  * **Svalbard server**: the share is stored on a dedicated server, that
    leverages authentication mechanisms present in existing real-world systems
    (no additional account/sign-up required, see more details below)

_Svalbard server_ is a server that maintains a database of shares, allows
submissions of shares, and sends them to designated recipients upon request.
The submission of shares and requests for retrieval are sent via an https
interface (so that the requests are protected from eavesdropping), but they are
guarded by a so-called _secondary channel_ (e.g. SMS), that provides an
independent comunication channel, an approach that is commonly used for
second-factor authentication e.g. in e-banking applications.  A detailed
specification of the functionality of a Svalbard server is given in a [separate
doc](docs/SERVER.md).  We emphasize that the server does not offer accounts
and/or signing-in, and anybody can send requests to the server.  The servers
could be run by major competing cloud providers free of charge, to encourage
customers to adopt cloud-based encryption solutions.

### Svalbard recovery: a reconstruction of the secret

To recover a secret from the backup Alice proceeds according to the following
steps:

  1. Fetch the corresponding metadata of the sharing from cloud provider.
  2. Retrieve sufficiently many (i.e. at least _k_) shares of the sharing
     from the storage locations recorded in the metadata.
  3. Reconstruct the secret from the retrieved shares.

Note that except for the retrieval of the shares from some storage types (from
printed copies or peer devices), the entire process can be fully automated.

### Protections against corruption of shares and metadata

As the shares and the metadata are distributed among multiple systems, they can
be subject to corruption, either accidental or malicious.  Such a corruption
could result in an undetected reconstruction of a wrong secret value, so
Svalbard employs simple hash-based protections to make sure that any corruption
is detected with high probability.

To detect potential corruptions of the second-level shares, the metadata
contains also a salted hash of each share.  This assures the correctness of
the shares that enter the secret reconstruction stage, assuming that the
metadata is correct.  Moreover, this will detect also corruption of the
hashes in the metadata unless the cloud provider CP that stores the metadata
maliciously cooperates with the provider of storage of second-level shares.

Even if the second-level shares were all correct and the first-level share SH2
was successfully reconstructed, a corruption of the share SH1 (that is stored as
a part of the metadata) could result in an undetected bit-flip or other
controlled modification of the reconstructed secret.  To detect a potential
corruption of SH1 the actual value that is being shared at the second level is
not the sole share SH2, but SH2 concatenated with a salted hash of the original
secret value SV.  This way after reconstruction of the secret one can check,
whether the reconstructed value is indeed equal to the original secret.

For a more detailed discussion of security properties see a separate
[SECURITY doc](docs/SECURITY.md).

## Structure of the code

The code is organized as follows:

 * [`client/`](./client/) contains code for a Svalbard client
 * [`server/`](./server/) contains code for a Svalbard server

For more information on the functionalities of the components see
[CLIENT.md](docs/CLIENT.md) resp. [SERVER.md](docs/SERVER.md).
