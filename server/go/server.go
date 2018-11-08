// Copyright 2018 The Svalbard Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
///////////////////////////////////////////////////////////////////////////////

// Binary server starts a Svalbard HTTP server to handle token sharing.
package main

import (
	"flag"
	"log"
	"net/http"
	"time"

	"github.com/google/svalbard/server/go/boltsharestore"
	"github.com/google/svalbard/server/go/filechannel"
	"github.com/google/svalbard/server/go/svalbardsrv"
	"github.com/google/svalbard/server/go/tokenstore"
)

func main() {
	filechannelRootDir := flag.String("filechannel_root_dir", "", "root dir for file-based secondary channel")
	boltShareStoreFile := flag.String("bolt_share_store_file", "", "Bolt DB file for storing shares")
	serverPort := flag.String("port", "8080", "port on which server should listen to incoming requests")
	tokenValidityPeriod := flag.Duration("token_validity", 5*time.Second, "validity period for short-lived tokens")
	keyFileTLS := flag.String("tls_key_file", "", "file with private key to be used for TLS")
	certFileTLS := flag.String("tls_cert_file", "", "file with certificate for the key to be used for TLS")
	flag.Parse()
	if *filechannelRootDir == "" {
		log.Fatal("Please provide -filechannel_root_dir")
	}
	if *boltShareStoreFile == "" {
		log.Fatal("Please provide -bolt_share_store_file")
	}
	useTLS := false
	if *keyFileTLS != "" || *certFileTLS != "" {
		useTLS = true
		if *keyFileTLS == "" {
			log.Fatalf("Missing -tls_key_file")
		}
		if *certFileTLS == "" {
			log.Fatalf("Missing -tls_cert_file")
		}
	}

	tokenLength := 5
	tokenStore, err := tokenstore.NewStore(tokenLength, *tokenValidityPeriod)
	if err != nil {
		log.Fatalf("Could not setup TokenStore: %v", err)
	}
	shareStore, err := boltsharestore.OpenOrCreate(*boltShareStoreFile)
	if err != nil {
		log.Fatalf("Could not setup BoltShareStore: %v", err)
	}
	srv := svalbardsrv.NewServer(tokenStore, shareStore,
		filechannel.NewChannel(*filechannelRootDir))
	http.HandleFunc("/get_storage_token", srv.GetStorageTokenHandler)
	http.HandleFunc("/get_storage_token/", srv.GetStorageTokenHandler)
	http.HandleFunc("/store_share", srv.StoreShareHandler)
	http.HandleFunc("/store_share/", srv.StoreShareHandler)
	http.HandleFunc("/get_retrieval_token", srv.GetRetrievalTokenHandler)
	http.HandleFunc("/get_retrieval_token/", srv.GetRetrievalTokenHandler)
	http.HandleFunc("/retrieve_share", srv.RetrieveShareHandler)
	http.HandleFunc("/retrieve_share/", srv.RetrieveShareHandler)
	http.HandleFunc("/get_deletion_token", srv.GetDeletionTokenHandler)
	http.HandleFunc("/get_deletion_token/", srv.GetDeletionTokenHandler)
	http.HandleFunc("/delete_share", srv.DeleteShareHandler)
	http.HandleFunc("/delete_share/", srv.DeleteShareHandler)
	log.Printf("Starting Svalbard server at port %v, using directory %v for secondary channel...\n",
		*serverPort, *filechannelRootDir)
	if useTLS {
		log.Printf("Starting in TLS-mode, using key from %v and certificate from %v ...\n",
			*keyFileTLS, *certFileTLS)
		log.Fatal(http.ListenAndServeTLS(":"+(*serverPort), *certFileTLS, *keyFileTLS, nil))
	} else {
		log.Printf("WARNING: starting in non-encrypted mode, all traffic can be intercepted ...\n")
		log.Fatal(http.ListenAndServe(":"+(*serverPort), nil))
	}
}
