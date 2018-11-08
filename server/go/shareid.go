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

// Package shareid implements share ids for a Svalbard server.
package shareid

import (
	"crypto/sha256"
	"encoding/hex"
	"errors"
)

// Errors returned upon failures when generating share IDs.
var (
	ErrMissingOwnerType  = errors.New("missing owner_id_type")
	ErrMissingOwnerID    = errors.New("missing owner_id")
	ErrMissingSecretName = errors.New("missing secret_name")
)

// GetShareID generates a unique, determinisic ID for the given parameters,
// all of which must be non-empty (returns an error if this condition
// is violated; the error message must not contain any senstivie information,
// in particular it must not quote any values of the parameters).
func GetShareID(ownerIDType, ownerID, secretName string) (string, error) {
	if ownerIDType == "" {
		return "", ErrMissingOwnerType
	}
	if ownerID == "" {
		return "", ErrMissingOwnerID
	}
	if secretName == "" {
		return "", ErrMissingSecretName
	}
	stringToHash := "[" + ownerIDType + "][" + ownerID + "][" + secretName + "]"
	hashValue := sha256.Sum256([]byte(stringToHash))
	return hex.EncodeToString(hashValue[:]), nil
}
