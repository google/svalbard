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

package inmemorysharestore

import (
	"testing"

	"github.com/google/svalbard/server/go/svalbardsrv"
)

// TODO: Add TSAN tests.

func TestInMemoryStoresAndRetrievesShares(t *testing.T) {
	s := New()
	tests := []struct {
		op      string // Operation on a ShareStore object
		shareID string
		value   string
	}{
		// Add share42 and verify it exitsts.
		{"Store", "share42", "some value"},
		{"Retrieve", "share42", "some value"},
		// Add share23 and verify it exitsts.
		{"Store", "share23", "some other value"},
		{"Retrieve", "share23", "some other value"},
		// Add a bunch of new shares.
		{"Store", "share1", "some value 1"},
		{"Store", "share2", "some value 2"},
		{"Store", "share3", "some value 3"},
		{"Store", "share4", "some value 4"},
		{"Store", "share5", "some value 5"},
		{"Store", "share6", "some value 6"},
		{"Store", "share7", "some value 7"},
		{"Store", "share8", "some value 8"},
		{"Store", "share9", "some value 9"},
		// Check all stored shares exist.
		{"Retrieve", "share42", "some value"},
		{"Retrieve", "share23", "some other value"},
		{"Retrieve", "share1", "some value 1"},
		{"Retrieve", "share2", "some value 2"},
		{"Retrieve", "share3", "some value 3"},
		{"Retrieve", "share4", "some value 4"},
		{"Retrieve", "share5", "some value 5"},
		{"Retrieve", "share6", "some value 6"},
		{"Retrieve", "share7", "some value 7"},
		{"Retrieve", "share8", "some value 8"},
		{"Retrieve", "share9", "some value 9"},
	}
	for i, tt := range tests {
		var err error
		var value string
		switch tt.op {
		case "Store":
			err = s.Store(tt.shareID, tt.value)
		case "Retrieve":
			value, err = s.Retrieve(tt.shareID)
		default:
			panic("Unknown operation: " + tt.op)
		}
		if err != nil {
			t.Errorf("Unexpected err of test #%d, %s(%q): %q", i, tt.op, tt.shareID, err)
		}
		if tt.op == "Retrieve" && err == nil && value != tt.value {
			t.Errorf("Unexpected value of test #%d, Retrieve(%q): got [%q], want[%q]",
				i, tt.shareID, value, tt.value)
		}
	}
}

func TestInMemoryStoresAndDeletesShares(t *testing.T) {
	s := New()
	tests := []struct {
		op      string // Operation on a ShareStore object
		shareID string
		value   string
		err     error // used only for Retrieve-operations
	}{
		// Add a bunch of shares.
		{"Store", "share1", "some value 1", nil},
		{"Store", "share2", "some value 2", nil},
		{"Store", "share3", "some value 3", nil},
		{"Store", "share4", "some value 4", nil},
		{"Store", "share5", "some value 5", nil},
		{"Store", "share6", "some value 6", nil},
		{"Store", "share7", "some value 7", nil},
		{"Store", "share8", "some value 8", nil},
		{"Store", "share9", "some value 9", nil},
		// Delete some of the shares, check that they are deleted.
		{"Delete", "share1", "", nil},
		{"Delete", "share3", "", nil},
		{"Delete", "share5", "", nil},
		{"Delete", "share7", "", nil},
		{"Delete", "share9", "", nil},
		{"Retrieve", "share1", "", svalbardsrv.ErrShareNotFound},
		{"Retrieve", "share3", "", svalbardsrv.ErrShareNotFound},
		{"Retrieve", "share5", "", svalbardsrv.ErrShareNotFound},
		{"Retrieve", "share7", "", svalbardsrv.ErrShareNotFound},
		{"Retrieve", "share9", "", svalbardsrv.ErrShareNotFound},
		// Non-deleted shares still exist.
		{"Retrieve", "share2", "some value 2", nil},
		{"Retrieve", "share4", "some value 4", nil},
		{"Retrieve", "share6", "some value 6", nil},
		{"Retrieve", "share8", "some value 8", nil},
	}
	for i, tt := range tests {
		var err error
		var value string
		switch tt.op {
		case "Store":
			err = s.Store(tt.shareID, tt.value)
		case "Delete":
			err = s.Delete(tt.shareID)
		case "Retrieve":
			value, err = s.Retrieve(tt.shareID)
		default:
			panic("Unknown operation: " + tt.op)
		}
		if err != tt.err {
			t.Errorf("Unexpected err of test #%d, %s(%q): got [%q], want[%q]",
				i, tt.op, tt.shareID, err, tt.err)
		}
		if tt.op == "Retrieve" && err == nil && value != tt.value {
			t.Errorf("Unexpected value of test #%d, Retrieve(%q): got [%q], want[%q]",
				i, tt.shareID, value, tt.value)
		}
	}
}

func TestInMemoryOperationErrors(t *testing.T) {
	s := New()
	tests := []struct {
		op      string // Operation on a ShareStore object
		shareID string
		value   string
		err     error // used only for Retrieve-operations
	}{
		// Invalid requests.
		{"Store", "", "some value", svalbardsrv.ErrInvalidShareID},
		{"Retrieve", "", "some value", svalbardsrv.ErrInvalidShareID},
		{"Delete", "", "some value", svalbardsrv.ErrInvalidShareID},
		{"Store", "someShareID", "", svalbardsrv.ErrInvalidShareValue},
		// share42 does not exist yet.
		{"Retrieve", "share42", "some other value", svalbardsrv.ErrShareNotFound},
		// Add share42 and verify it exitsts.
		{"Store", "share42", "some other value", nil},
		{"Retrieve", "share42", "some other value", nil},
		// Delete share42.
		{"Delete", "share42", "", nil},
		{"Retrieve", "share42", "some other value", svalbardsrv.ErrShareNotFound},
		// Try deleting non-existing shares.
		{"Delete", "share42", "", svalbardsrv.ErrShareNotFound},
		{"Delete", "someOtherShare", "", svalbardsrv.ErrShareNotFound},
	}
	for i, tt := range tests {
		var err error
		var value string
		switch tt.op {
		case "Store":
			err = s.Store(tt.shareID, tt.value)
		case "Delete":
			err = s.Delete(tt.shareID)
		case "Retrieve":
			value, err = s.Retrieve(tt.shareID)
		default:
			panic("Unknown operation: " + tt.op)
		}
		if err != tt.err {
			t.Errorf("Unexpected err of test #%d, %s(%q): got [%q], want[%q]",
				i, tt.op, tt.shareID, err, tt.err)
		}
		if tt.op == "Retrieve" && err == nil && value != tt.value {
			t.Errorf("Unexpected value of test #%d, Retrieve(%q): got [%q], want[%q]",
				i, tt.shareID, value, tt.value)
		}
	}
}
