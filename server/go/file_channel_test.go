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

package filechannel

import (
	"bytes"
	"io/ioutil"
	"os"
	"path/filepath"
	"testing"

	"github.com/google/svalbard/server/go/svalbardsrv"
)

func getFileContent(filename string, t *testing.T) []byte {
	content, err := ioutil.ReadFile(filename)
	if err != nil {
		t.Fatal("Could not read file " + filename + ": " + err.Error())
	}
	return content
}

func TestSendMessageWritesToPerRecipientFilesInclDuplicates(t *testing.T) {
	rootDir, err := ioutil.TempDir(os.Getenv("TEST_TMPDIR"), "svalbard_file_channel")
	if err != nil {
		t.Fatal(err)
	}
	sc := NewChannel(rootDir)
	var tests = []struct {
		recipient svalbardsrv.RecipientID
		data      svalbardsrv.TokenMsgData
	}{
		// A few valid requests to be sent to various recipients.
		// Each distinct recipientID.ID results in a separate file.
		// Each repetition of a message should also appear in the corresponding file.
		{svalbardsrv.RecipientID{"file", "alice"}, svalbardsrv.TokenMsgData{"req42", "asdfie"}},
		{svalbardsrv.RecipientID{"FILE", "Bob"}, svalbardsrv.TokenMsgData{"26g3", "AEUHE"}},
		{svalbardsrv.RecipientID{"FIle", "Bob"}, svalbardsrv.TokenMsgData{"636328", "yqggyod"}},
		{svalbardsrv.RecipientID{"File", "Mary"}, svalbardsrv.TokenMsgData{"3682a", "Uye83gh"}},
		{svalbardsrv.RecipientID{"FilE", "Mary"}, svalbardsrv.TokenMsgData{"362843a", "ABueyge63"}},
		{svalbardsrv.RecipientID{"fILe", "alice"}, svalbardsrv.TokenMsgData{"req42", "asdfie"}},
		{svalbardsrv.RecipientID{"fiLE", "alice"}, svalbardsrv.TokenMsgData{"req42", "asdfie"}},
	}
	// Every entry corresponds to a file that should be created for each distinct ownerID.
	expectedContent := make(map[string][]byte)
	for _, tt := range tests {
		err := sc.Send(tt.recipient, tt.data)
		if err != nil {
			t.Errorf("Unexpected error %v", err)
			continue
		}
		// Update expectedContent.
		expected := expectedContent[tt.recipient.ID]
		msg, err := svalbardsrv.GetMsgWithToken(tt.data)
		if err != nil {
			t.Errorf("Unexpected error %v", err)
			continue
		}
		expectedContent[tt.recipient.ID] = append(expected, []byte(msg+"\n")...)
		// Compare with actual content.
		filename := filepath.Join(rootDir, tt.recipient.ID+"_secondary_channel.txt")
		actualContent := getFileContent(filename, t)
		expected = expectedContent[tt.recipient.ID]
		if !bytes.Equal(actualContent, expected) {
			t.Errorf("Unexpected content of %v: got [%v], want [%v]",
				filename, string(actualContent), string(expected))
		}
	}
}

func TestSendMessageDoesNotCrashForUnsupportedOwnerIdTypes(t *testing.T) {
	rootDir, err := ioutil.TempDir(os.Getenv("TEST_TMPDIR"), "svalbard_file_channel")
	if err != nil {
		t.Fatal(err)
	}
	sc := NewChannel(rootDir)
	var tests = []struct {
		recipient svalbardsrv.RecipientID
		data      svalbardsrv.TokenMsgData
	}{
		{svalbardsrv.RecipientID{"SMS", "alice"}, svalbardsrv.TokenMsgData{"req42", "hehggeo"}},
		{svalbardsrv.RecipientID{"email", "Mary"}, svalbardsrv.TokenMsgData{"76263", "662563"}},
		{svalbardsrv.RecipientID{"foo", "Bob"}, svalbardsrv.TokenMsgData{"63tg3", "63hgg3"}},
	}
	// Every entry corresponds to a file that should be created for each distinct ownerID.
	for _, tt := range tests {
		t.Logf("Calling Send() with recipient [%v] and data [%v]", tt.recipient, tt.data)
		err := sc.Send(tt.recipient, tt.data)
		if err != svalbardsrv.ErrUnsupportedOwnerIDType {
			t.Errorf("Unexpected error %v", err)
			continue
		}
	}
}
