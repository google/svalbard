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

// Package util implements various utils for Svalbard server.
package util

import (
	"crypto/rand"
	"errors"
)

// Errors returned upon failures.
var (
	ErrWrongStringLength = errors.New("length must be positive")
)

const letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

// RandomString returns a random string of specified length,
// containing a mix of lower-/upper-case letters.
func RandomString(length int) (string, error) {
	if length < 1 {
		return "", ErrWrongStringLength
	}
	r := make([]byte, length)
	_, err := rand.Read(r)
	if err != nil {
		return "", err
	}
	for i := range r {
		r[i] = letters[int(r[i])%len(letters)]
	}
	return string(r), nil
}
