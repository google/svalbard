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

// Package filechannel implements the svalbardsrv.SecondaryChannel interface using files.
package filechannel

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/google/svalbard/server/go/svalbardsrv"
)

// NewChannel returns a new Channel object, which is an implementation of
// svalbardsrv.SecondaryChannel interface that communicates using files in the specified 'rootDir'.
// It is intended for testing only.
// TODO: add testonly=1 to the BUILD-rule once other implementations are available.
func NewChannel(rootDir string) *Channel {
	return &Channel{
		rootDir,
	}
}

// Channel is a svalbardsrv.SecondaryChannel implementation based on files.
// A secondary channel to a user identified by 'userID' is just a file named
// userID + "secondary_channel.txt" in the root directory of the channel,
// and each message sent via the channel is written to the file on a separate line.
// It is intended for testing only.
type Channel struct {
	rootDir string
}

func getFile(dir, ownerID string) (*os.File, error) {
	filename := filepath.Join(dir, ownerID+"_secondary_channel.txt")
	file, err := os.OpenFile(filename, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	if err != nil {
		return nil, err
	}
	return file, nil
}

// Send sends 'token' with the label 'reqID' to recipient identified by 'ownerID'
// using communication channel determined by 'ownerIDType'.
// If an error occurs, it returns a non-nil error value.
func (sc *Channel) Send(recipientID svalbardsrv.RecipientID, data svalbardsrv.TokenMsgData) (err error) {
	if strings.ToUpper(recipientID.IDType) != "FILE" {
		return svalbardsrv.ErrUnsupportedOwnerIDType
	}
	msg, err := svalbardsrv.GetMsgWithToken(data)
	if err != nil {
		return err
	}
	f, err := getFile(sc.rootDir, recipientID.ID)
	if err != nil {
		return err
	}
	defer func() {
		if cErr := f.Close(); err == nil {
			err = cErr
		}
	}()
	_, err = fmt.Fprintf(f, "%s\n", msg)
	return err
}
