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

package tokenstore

import (
	"testing"
	"time"

	"github.com/google/svalbard/server/go/svalbardsrv"
)

// TODO: Add TSAN tests.

func TestNewStore(t *testing.T) {
	exampleDuration := 5 * time.Second
	for i := 5; i < 42; i++ {
		ts, err := NewStore(i, exampleDuration)
		if ts == nil {
			t.Errorf("NewStore: %v", err)
		}
		token, err := ts.GetNewToken("share id", svalbardsrv.OpRetrieveShare)
		if err != nil {
			t.Fatal(err)
		}
		if len(token) != i {
			t.Errorf("Token '%v' has wrong length, should have %v chars", token, i)
		}
	}

	// Try creating a store with shorter tokens.
	for i := -3; i < MinTokenLength; i++ {
		ts, err := NewStore(i, exampleDuration)
		if ts != nil || err != ErrTokenLengthTooSmall {
			t.Errorf("Should have failed as tokenLength %v is too small", i)
		}
	}

	// Try creating a store with shorter duration.
	for i := 0; i < int(MinTokenValidityDuration.Seconds()); i++ {
		shortDuration := time.Duration(i) * time.Second
		ts, err := NewStore(7, shortDuration)
		if ts != nil || err != ErrTokenValidityDurationTooShort {
			t.Errorf("Should have failed as tokenValidityDuration %vs is too short", i)
		}
	}
}

func TestTokenCreationAndExpiration(t *testing.T) {
	shareID1 := "some share ID"
	shareID2 := "other share ID"
	op1 := svalbardsrv.OpRetrieveShare
	op2 := svalbardsrv.OpDeleteShare

	for i := 5; i < 8; i++ {
		ts, err := NewStore(i, 5*time.Second)
		if ts == nil {
			t.Fatalf("NewStore: %v", err)
		}
		token1, err := ts.GetNewToken(shareID1, op1)
		if err != nil {
			t.Fatal(err)
		}
		token2, err := ts.GetNewToken(shareID2, op2)
		if err != nil {
			t.Fatal(err)
		}
		var invalidTokenParametersTests = []struct {
			token   string
			shareID string
			op      svalbardsrv.Operation
		}{
			// Check variations on token1.
			{token1, shareID2, op1},
			{token1, shareID1, op2},
			{token1 + "extra", shareID1, op1},
			{token1[:len(token1)-1], shareID1, op1},
			// Check variations on token2.
			{token2, shareID1, op2},
			{token2, shareID2, op1},
			{token2 + "extra", shareID2, op2},
			{token2[:len(token2)-1], shareID2, op2},
		}
		for i, tt := range invalidTokenParametersTests {
			if err := ts.IsTokenValidNow(tt.token, tt.shareID, tt.op); err == nil {
				t.Errorf("test case #%v: token %v should not be valid for %v, %v",
					i, tt.token, tt.shareID, tt.op)
			}

		}

		var tokenExpirationTests = []struct {
			token   string
			shareID string
			op      svalbardsrv.Operation
		}{
			{token1, shareID1, op1},
			{token2, shareID2, op2},
		}
		// Check tokens before and after timeout.
		for j := 0; j < 4; j++ {
			time.Sleep(time.Second)
			for _, tt := range tokenExpirationTests {
				if err := ts.IsTokenValidNow(tt.token, tt.shareID, tt.op); err != nil {
					t.Errorf("Token %v should be valid for %v, %v; unexpected error: %v",
						tt.token, tt.shareID, tt.op, err)
				}
			}
		}
		time.Sleep(time.Second)
		for _, tt := range tokenExpirationTests {
			if err := ts.IsTokenValidNow(tt.token, tt.shareID, tt.op); err != svalbardsrv.ErrTokenExpired {
				t.Errorf("Token %v for %v, %v should be now expired; unexpected error %v",
					tt.token, tt.shareID, tt.op, err)
			}
		}
	}
}
