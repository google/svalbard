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

package util

import (
	"regexp"
	"testing"
)

func TestRandomString(t *testing.T) {
	isAlpha := regexp.MustCompile(`^[A-Za-z]+$`).MatchString
	for l := 1; l < 20; l++ {
		s, err := RandomString(l)
		if err != nil {
			t.Fatal(err)
		}
		if len(s) != l {
			t.Errorf("String [%s] has wrong length, expected %v.", s, l)
		}
		if !isAlpha(s) {
			t.Errorf("String [%s] should have only alphabetic characters.", s)
		}
		if l > 3 { // Check randomness, only for longer strings, to avoid flakiness.
			s2, err := RandomString(l)
			if err != nil {
				t.Fatal(err)
			}
			if s == s2 {
				t.Errorf("Two 'random' strings are unexpectedly equal: [%s]", s)
			}
		}
	}
}

func TestRandomStringErrors(t *testing.T) {
	_, err := RandomString(0)
	if err != ErrWrongStringLength {
		t.Errorf("For zero-length expected ErrWrongStringLength (%v)", ErrWrongStringLength)
	}
	_, err = RandomString(-1)
	if err != ErrWrongStringLength {
		t.Errorf("For negative length expected ErrWrongStringLength (%v)", ErrWrongStringLength)
	}
}
