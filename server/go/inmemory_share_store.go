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

// Package inmemorysharestore implements a store for shares of a Svalbard HTTP
// server, that uses an in-memory structure to store the data.
// It is intended to be used only for testing.
package inmemorysharestore

import (
	"sync"

	"github.com/google/svalbard/server/go/svalbardsrv"
)

// New returns a new InMemory-instance that stores the shares
// in an in-memory data structure (intended for testing only).
// The returned InMemory implements svalbardsrv.ShareStore-interface.
func New() *InMemory {
	return &InMemory{
		store: make(map[string]string),
	}
}

// A ShareStore implementation that uses an in-memory map to store the shares.
type InMemory struct {
	storeMutex sync.RWMutex
	store      map[string]string
}

// Store stores the given 'shareValue' under the specified 'shareID'.
func (ss *InMemory) Store(shareID, shareValue string) error {
	if shareID == "" {
		return svalbardsrv.ErrInvalidShareID
	}
	if shareValue == "" {
		return svalbardsrv.ErrInvalidShareValue
	}
	ss.storeMutex.Lock()
	defer ss.storeMutex.Unlock()
	if _, shareExists := ss.store[shareID]; shareExists {
		return svalbardsrv.ErrShareAlreadyExists
	}
	ss.store[shareID] = shareValue
	return nil
}

// Retrieve returns the value of the share identified by 'shareID',
// if it is present in the store.  If no share is present, or if the
// retrieval fails for some reason, it returns nil and an error message.
func (ss *InMemory) Retrieve(shareID string) (string, error) {
	if shareID == "" {
		return "", svalbardsrv.ErrInvalidShareID
	}
	ss.storeMutex.Lock()
	defer ss.storeMutex.Unlock()
	shareValue, shareExists := ss.store[shareID]
	if !shareExists {
		return "", svalbardsrv.ErrShareNotFound
	}
	return shareValue, nil
}

// Delete removes from the store the share identified by 'shareID',
// if it is present in the store.  If no share is present, or if the
// deletion fails for some reason, it returns an error message.
func (ss *InMemory) Delete(shareID string) error {
	if shareID == "" {
		return svalbardsrv.ErrInvalidShareID
	}
	ss.storeMutex.Lock()
	defer ss.storeMutex.Unlock()
	_, shareExists := ss.store[shareID]
	if !shareExists {
		return svalbardsrv.ErrShareNotFound
	}
	delete(ss.store, shareID)
	return nil
}
