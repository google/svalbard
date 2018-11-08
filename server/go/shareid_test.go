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

package shareid

import "testing"

func TestGetShareID(t *testing.T) {
	var tests = []struct {
		ps []string
		id string
	}{
		{[]string{"a", "b", "c"}, "e998ba073ec38976e56156523126e98679eb916063d8cb5f1d9bd8193467dc25"},
		{[]string{"abc", "xyz", "efg"}, "7d97f68401fb8217b4beab14598eb88af5b5ab8c4282731a67b464ad47e2793b"},
	}

	for _, test := range tests {
		id, err := GetShareID(test.ps[0], test.ps[1], test.ps[2])
		if err != nil {
			t.Errorf("Unexpected error %v", err)
			continue
		}
		if id != test.id {
			t.Errorf("Expected %v but got %v", test.id, id)
		}
	}
}

func TestGetShareIDErrors(t *testing.T) {
	var tests = []struct {
		ps  []string
		err error
	}{
		{[]string{"", "b", "c"}, ErrMissingOwnerType},
		{[]string{"a", "", "c"}, ErrMissingOwnerID},
		{[]string{"a", "b", ""}, ErrMissingSecretName},
	}

	for _, test := range tests {
		_, err := GetShareID(test.ps[0], test.ps[1], test.ps[2])
		if err == nil {
			t.Error("Expected an error.")
		}
		if err != test.err {
			t.Errorf("Expected error [%v] but got [%v]", test.err, err)
		}
	}
}
