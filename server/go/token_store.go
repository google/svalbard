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

// Package tokenstore implements a store for tokens that are used by
// various operations of a Svalbard HTTP server.
package tokenstore

import (
	"errors"
	"sync"
	"time"

	"github.com/google/svalbard/server/go/svalbardsrv"
	"github.com/google/svalbard/server/go/util"
)

// NewStore returns a new Store-instance with the specified parameters.
// The returned Store implements svalbardsrv.Store.
func NewStore(tokenLength int, tokenValidityDuration time.Duration) (*Store, error) {
	if tokenLength < MinTokenLength {
		return nil, ErrTokenLengthTooSmall
	}
	if tokenValidityDuration < MinTokenValidityDuration {
		return nil, ErrTokenValidityDurationTooShort
	}
	return &Store{
		tokenLength:           tokenLength,
		tokenValidityDuration: tokenValidityDuration,
		store:                 make(map[string]tokenData),
	}, nil
}

// Bounds on parameters used when creating Store-instances.
const (
	MinTokenLength           = 5
	MinTokenValidityDuration = 2 * time.Second
)

// Errors returned upon failures when creating a Store.
var (
	ErrTokenValidityDurationTooShort = errors.New("tokenValidityDuration too short")
	ErrTokenLengthTooSmall           = errors.New("tokenLength too small")
)

// A Store implementation that uses an in-memory map to store the tokens.
type Store struct {
	// General properties of the store.
	tokenLength           int
	tokenValidityDuration time.Duration
	// Internal data structure that holds the tokens and the corresponding data.
	store      map[string]tokenData
	storeMutex sync.RWMutex
}

// Data associated with each token.
type tokenData struct {
	validTill time.Time
	shareID   string
	op        svalbardsrv.Operation
}

// GetNewToken returns a new access token valid for the operation 'op' on the share
// identified by 'shareID'.
func (ts *Store) GetNewToken(shareID string, op svalbardsrv.Operation) (string, error) {
	validTill := time.Now().Add(ts.tokenValidityDuration)
	tokenData := tokenData{validTill, shareID, op}
	newToken, err := util.RandomString(ts.tokenLength)
	if err != nil {
		return "", err
	}
	ts.storeMutex.Lock()
	defer ts.storeMutex.Unlock()
	ts.store[newToken] = tokenData
	return newToken, nil
}

// IsTokenValidNow returns true iff the given token is currently valid
// for the operation 'op' on the share identified by 'shareID'.
func (ts *Store) IsTokenValidNow(token, shareID string, op svalbardsrv.Operation) error {
	if len(token) != ts.tokenLength {
		return svalbardsrv.ErrTokenNotValid
	}
	ts.storeMutex.Lock()
	defer ts.storeMutex.Unlock()
	tokenData, ok := ts.store[token]
	if !ok {
		return svalbardsrv.ErrTokenNotFound
	}
	if tokenData.validTill.Before(time.Now()) {
		return svalbardsrv.ErrTokenExpired
	}
	if (tokenData.shareID != shareID) || (tokenData.op != op) {
		return svalbardsrv.ErrTokenNotValid
	}
	return nil
}
