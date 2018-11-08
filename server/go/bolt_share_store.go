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

// Package boltsharestore implements a store for shares of a Svalbard HTTP
// server, that uses Bolt DB for persisting the data.
package boltsharestore

import (
	"fmt"

	bolt "github.com/etcd-io/bbolt/bolt"
	"github.com/google/svalbard/server/go/svalbardsrv"
)

// OpenOrCreate returns an instance of ShareStore that stores the shares
// in a Bolt database that keeps the data in the specified file.
// The returned Bolt implements svalbardsrv.ShareStore-interface.
func OpenOrCreate(filename string) (*Bolt, error) {
	db, err := bolt.Open(filename, 0600, nil)
	if err != nil {
		return nil, err
	}
	err = db.Update(func(tx *bolt.Tx) error {
		if _, err := tx.CreateBucketIfNotExists([]byte("SvalbardShares")); err != nil {
			return fmt.Errorf("Could not initialize Bolt DB: %s", err)
		}
		return nil
	})
	if err != nil {
		return nil, err
	}
	return &Bolt{
		db: db,
	}, nil
}

// Bolt is a ShareStore implementation that uses a Bolt DB to store the shares.
type Bolt struct {
	db *bolt.DB
}

// Store stores the given 'shareValue' under the specified 'shareID'.
func (ss *Bolt) Store(shareID, shareValue string) error {
	if shareID == "" {
		return svalbardsrv.ErrInvalidShareID
	}
	if shareValue == "" {
		return svalbardsrv.ErrInvalidShareValue
	}
	err := ss.db.Update(func(tx *bolt.Tx) error {
		b := tx.Bucket([]byte("SvalbardShares"))
		if v := b.Get([]byte(shareID)); v != nil {
			return svalbardsrv.ErrShareAlreadyExists
		}
		return b.Put([]byte(shareID), []byte(shareValue))
	})
	return err
}

// Retrieve returns the value of the share identified by 'shareID',
// if it is present in the store.  If no share is present, or if the
// retrieval fails for some reason, it returns nil and an error message.
func (ss *Bolt) Retrieve(shareID string) (string, error) {
	if shareID == "" {
		return "", svalbardsrv.ErrInvalidShareID
	}
	var shareValue string
	err := ss.db.View(func(tx *bolt.Tx) error {
		b := tx.Bucket([]byte("SvalbardShares"))
		v := b.Get([]byte(shareID))
		if v == nil {
			return svalbardsrv.ErrShareNotFound
		}
		shareValue = string(v)
		return nil
	})
	return shareValue, err
}

// Delete removes from the store the share identified by 'shareID',
// if it is present in the store.  If no share is present, or if the
// deletion fails for some reason, it returns an error message.
func (ss *Bolt) Delete(shareID string) error {
	if shareID == "" {
		return svalbardsrv.ErrInvalidShareID
	}
	err := ss.db.Update(func(tx *bolt.Tx) error {
		b := tx.Bucket([]byte("SvalbardShares"))
		if v := b.Get([]byte(shareID)); v == nil {
			return svalbardsrv.ErrShareNotFound
		}
		return b.Delete([]byte(shareID))
	})
	return err
}

// Close closes the underlying Bolt DB, releasing the corresponding resources.
// After Close() the ShareStore cannot be accessed any more.
func (ss *Bolt) Close() error {
	return ss.db.Close()
}
