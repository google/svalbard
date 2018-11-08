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

package svalbardsrv_test

import (
	"bufio"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/google/svalbard/server/go/filechannel"
	"github.com/google/svalbard/server/go/inmemorysharestore"
	"github.com/google/svalbard/server/go/shareid"
	"github.com/google/svalbard/server/go/svalbardsrv"
	"github.com/google/svalbard/server/go/testingtools"
	"github.com/google/svalbard/server/go/tokenstore"
	"github.com/google/svalbard/server/go/util"
)

// TODO: Add TSAN tests.

const testTarget = "http://svalbard.example.com"

func newTempDir() string {
	rootDir, err := ioutil.TempDir(os.Getenv("TEST_TMPDIR"), "svalbard_file_channel")
	if err != nil {
		panic(err)
	}
	return rootDir
}

type userID struct {
	IDType string
	ID     string
}

type shareData struct {
	secretName string
	shareValue string
}

func newStoreShareRequest(token string, user userID, data shareData) *http.Request {
	reqData := make(url.Values)
	reqData.Set("token", token)
	reqData.Set("owner_id_type", user.IDType)
	reqData.Set("owner_id", user.ID)
	reqData.Set("secret_name", data.secretName)
	reqData.Set("share_value", data.shareValue)
	body := bufio.NewReader(strings.NewReader(reqData.Encode()))
	req := httptest.NewRequest("POST", testTarget+"/store_share", body)
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	return req
}

func newRetrieveShareRequest(token string, user userID, secretName string) *http.Request {
	reqData := make(url.Values)
	reqData.Set("token", token)
	reqData.Set("owner_id_type", user.IDType)
	reqData.Set("owner_id", user.ID)
	reqData.Set("secret_name", secretName)
	body := bufio.NewReader(strings.NewReader(reqData.Encode()))
	req := httptest.NewRequest("POST", testTarget+"/retrieve_share", body)
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	return req
}

func newDeleteShareRequest(token string, user userID, secretName string) *http.Request {
	reqData := make(url.Values)
	reqData.Set("token", token)
	reqData.Set("owner_id_type", user.IDType)
	reqData.Set("owner_id", user.ID)
	reqData.Set("secret_name", secretName)
	body := bufio.NewReader(strings.NewReader(reqData.Encode()))
	req := httptest.NewRequest("POST", testTarget+"/delete_share", body)
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	return req
}

func newGetTokenRequest(reqID string, user userID, secretName, handlerURL string) *http.Request {
	data := make(url.Values)
	data.Set("request_id", reqID)
	data.Set("owner_id_type", user.IDType)
	data.Set("owner_id", user.ID)
	data.Set("secret_name", secretName)
	body := bufio.NewReader(strings.NewReader(data.Encode()))
	req := httptest.NewRequest("POST", testTarget+handlerURL, body)
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	return req
}

func shareStoredResponse(data shareData, user userID) string {
	return "Stored a share of secret [" + data.secretName + "] for owner [" + user.IDType + ":" + user.ID + "]"
}

func shareDeletedResponse(secretName string, user userID) string {
	return "Deleted a share of secret [" + secretName + "] of owner [" + user.IDType + ":" + user.ID + "]"
}

func shareNotFoundResponse(reqID string) string {
	return "Req. " + reqID + ": share not found.\n"
}

func tokenSentResponse(reqID string, user userID, secretName, operation string) string {
	return fmt.Sprintf("Req. %s: %s token for share of [%s] sent to [%s:%s]",
		reqID, operation, secretName, user.IDType, user.ID)
}

func addBodySuffix(err error) string {
	// Appending "\n" to the error message because http.Error appends it.
	return err.Error() + "\n"
}

func getTestServer(rootDir string, t *testing.T) *svalbardsrv.Server {
	exampleDuration := 5 * time.Second
	tokenLength := 5
	tokenStore, err := tokenstore.NewStore(tokenLength, exampleDuration)
	if err != nil {
		t.Fatalf("Could not setup TokenStore: %v", err)
	}
	return svalbardsrv.NewServer(tokenStore, inmemorysharestore.New(),
		filechannel.NewChannel(rootDir))
}

func fetchToken(rootDir, ownerID, reqID string, t *testing.T) string {
	filename := filepath.Join(rootDir, ownerID+"_secondary_channel.txt")
	content, err := ioutil.ReadFile(filename)
	if err != nil {
		t.Fatal("Could not read file " + filename + ": " + err.Error())
	}
	s := string(content)
	prefix := "SVBD:" + reqID + ":"
	i := strings.LastIndex(s, prefix)
	if i == -1 {
		t.Errorf("No token found for request with ID [%v] of owner [%v].", reqID, ownerID)
	}
	ts := s[i+len(prefix):] // ts starts at the first char of the token
	return ts[:strings.Index(ts, "\n")]
}

func TestGetMsgWithToken(t *testing.T) {
	var tests = []struct {
		data svalbardsrv.TokenMsgData
		msg  string
		err  error
	}{
		{svalbardsrv.TokenMsgData{"reqID1", "someToken"}, "SVBD:reqID1:someToken", nil},
		{svalbardsrv.TokenMsgData{"673hgg", "ghGGHAHye"}, "SVBD:673hgg:ghGGHAHye", nil},
		{svalbardsrv.TokenMsgData{"a", "b"}, "SVBD:a:b", nil},
		{svalbardsrv.TokenMsgData{"7e76g3hgeb3ke", "HEUG83gg37g63gdegw"}, "SVBD:7e76g3hgeb3ke:HEUG83gg37g63gdegw", nil},
		{svalbardsrv.TokenMsgData{"67:g", "ghHAHye"}, "", svalbardsrv.ErrInvalidParametersForMsgWithToken},
		{svalbardsrv.TokenMsgData{"67ag", "ab:e"}, "", svalbardsrv.ErrInvalidParametersForMsgWithToken},
		{svalbardsrv.TokenMsgData{"6::ag", "ab:e"}, "", svalbardsrv.ErrInvalidParametersForMsgWithToken},
		{svalbardsrv.TokenMsgData{":", ":"}, "", svalbardsrv.ErrInvalidParametersForMsgWithToken},
		{svalbardsrv.TokenMsgData{":", ""}, "", svalbardsrv.ErrInvalidParametersForMsgWithToken},
		{svalbardsrv.TokenMsgData{"A", ""}, "", svalbardsrv.ErrInvalidParametersForMsgWithToken},
		{svalbardsrv.TokenMsgData{"", "B"}, "", svalbardsrv.ErrInvalidParametersForMsgWithToken},
		{svalbardsrv.TokenMsgData{"", ""}, "", svalbardsrv.ErrInvalidParametersForMsgWithToken},
	}
	for _, tt := range tests {
		msg, err := svalbardsrv.GetMsgWithToken(tt.data)
		if err != tt.err {
			t.Errorf("GetMsgWithToken(%v) error: got [%v], want [%v]", tt.data, err, tt.err)
		}
		if err == nil && msg != tt.msg {
			t.Errorf("GetMsgWithToken(%v) message: got [%v], want [%v]", tt.data, msg, tt.msg)
		}

	}
}

func TestParseMsgWithToken(t *testing.T) {
	var tests = []struct {
		msg  string
		data svalbardsrv.TokenMsgData
		err  error
	}{
		{"SVBD:reqID2:someOtherToken", svalbardsrv.TokenMsgData{"reqID2", "someOtherToken"}, nil},
		{"SVBD:63gh:hEGHE83", svalbardsrv.TokenMsgData{"63gh", "hEGHE83"}, nil},
		{"SVBD:8g3ggb3:hwebt3BGb83", svalbardsrv.TokenMsgData{"8g3ggb3", "hwebt3BGb83"}, nil},
		{"SVBD:7:A", svalbardsrv.TokenMsgData{"7", "A"}, nil},
		{"SV:", svalbardsrv.TokenMsgData{"", ""}, svalbardsrv.ErrInvalidMsgWithToken},
		{"::", svalbardsrv.TokenMsgData{"", ""}, svalbardsrv.ErrInvalidMsgWithToken},
		{"", svalbardsrv.TokenMsgData{"", ""}, svalbardsrv.ErrInvalidMsgWithToken},
		{"SVBD::", svalbardsrv.TokenMsgData{"", ""}, svalbardsrv.ErrInvalidMsgWithToken},
		{"SVBD:A:", svalbardsrv.TokenMsgData{"", ""}, svalbardsrv.ErrInvalidMsgWithToken},
		{"SVBD::B", svalbardsrv.TokenMsgData{"", ""}, svalbardsrv.ErrInvalidMsgWithToken},
		{"SVBD:::", svalbardsrv.TokenMsgData{"", ""}, svalbardsrv.ErrInvalidMsgWithToken},
		{"SVB:reqID2:someOtherToken", svalbardsrv.TokenMsgData{"", ""}, svalbardsrv.ErrInvalidMsgWithToken},
		{"SVBD:reqID3:some:OtherToken", svalbardsrv.TokenMsgData{"", ""}, svalbardsrv.ErrInvalidMsgWithToken},
		{"SVBD:reqID5:AsdF:", svalbardsrv.TokenMsgData{"", ""}, svalbardsrv.ErrInvalidMsgWithToken},
	}
	for _, tt := range tests {
		data, err := svalbardsrv.ParseMsgWithToken(tt.msg)
		if err != tt.err {
			t.Errorf("ParseMsgWithToken(%v) error: got [%v], want [%v]", tt.msg, err, tt.err)
		}
		if err == nil {
			if data != tt.data {
				t.Errorf("ParseMsgWithToken(%v) data: got [%v], want [%v]", tt.msg, data, tt.data)
			}
		}
	}
}

func TestStorageTokenIssuance(t *testing.T) {
	rootDir := newTempDir()
	s := getTestServer(rootDir, t)
	ownerIDType, ownerID1, ownerID2 := "FILE", "Tom", "Jerry"
	secretName := "Gmail key"
	reqID1, reqID2 := "a8ehg3", "9egehw"
	var tests = []struct {
		reqID       string
		ownerIDType string
		ownerID     string
		secretName  string
		respBody    string
	}{
		// Get a storage token for a share.
		{reqID1, ownerIDType, ownerID1, secretName,
			tokenSentResponse(reqID1, userID{ownerIDType, ownerID1}, secretName, "storage")},
		// Get a storage token for another share.
		{reqID2, ownerIDType, ownerID2, secretName,
			tokenSentResponse(reqID2, userID{ownerIDType, ownerID2}, secretName, "storage")},
		// Get another storage token for a share.
		{reqID2, ownerIDType, ownerID1, secretName,
			tokenSentResponse(reqID2, userID{ownerIDType, ownerID1}, secretName, "storage")},
		// Get yet another storage token
		{reqID1, ownerIDType, ownerID2, secretName,
			tokenSentResponse(reqID1, userID{ownerIDType, ownerID2}, secretName, "storage")},
	}

	for _, tt := range tests {
		w := testingtools.NewFakeResponseWriter()
		req := newGetTokenRequest(tt.reqID, userID{tt.ownerIDType, tt.ownerID}, tt.secretName, "/get_storage_token")
		s.GetStorageTokenHandler(w, req)
		if w.Status != http.StatusOK {
			t.Errorf("GetStorageTokenHandler(%v) status: got [%v], want [%v]", tt, w.Status, http.StatusOK)
			continue
		}
		token := fetchToken(rootDir, tt.ownerID, tt.reqID, t)
		if len(token) < 3 {
			t.Errorf("GetStorageTokenHandler(%v) token: should be at least 3 characters but got [%v]", tt, token)
		}
		if w.Body != tt.respBody {
			t.Errorf("GetStorageTokenHandler(%v) body: got [%v], want [%v]", tt, w.Body, tt.respBody)
		}
	}
}

func TestStorageTokensAreDistinct(t *testing.T) {
	rootDir := newTempDir()
	s := getTestServer(rootDir, t)
	ownerIDType := "FILE"
	ownerIDs := []string{"Tom", "Jerry", "Alice", "Bob", "Mila", "Misha"}
	secretName := "Gmail key"

	allTokens := make(map[string]bool) // Collects all tokens.
	reqCount := 100
	tokenCount := 0
	for i := 0; i < reqCount; i++ {
		reqID, err := util.RandomString(3)
		if err != nil {
			t.Fatalf("Could not generate reqID, util.RandomString(3) error %v", err)
		}
		ownerID := ownerIDs[i%len(ownerIDs)]
		w := testingtools.NewFakeResponseWriter()
		req := newGetTokenRequest(reqID, userID{ownerIDType, ownerID}, secretName, "/get_storage_token")
		s.GetStorageTokenHandler(w, req)
		if w.Status != http.StatusOK {
			t.Errorf("GetStorageTokenHandler(%v) status: got [%v], want [%v]", req, w.Status, http.StatusOK)
			continue
		}
		token := fetchToken(rootDir, ownerID, reqID, t)
		allTokens[token] = true
		tokenCount++
	}
	if len(allTokens) != tokenCount {
		t.Errorf("The generated tokens are not distinct, there were %v collisions.", tokenCount-len(allTokens))
	}
}

func TestBadRequestsForStorageToken(t *testing.T) {
	rootDir := newTempDir()
	s := getTestServer(rootDir, t)
	ownerIDType, ownerID := "FILE", "Bob"
	secretName := "Gmail key"
	reqID := "63hgtg"
	var tests = []struct {
		reqID       string
		ownerIDType string
		ownerID     string
		secretName  string
		respBody    string
	}{
		// An invalid request: no request_id.
		{"", ownerIDType, ownerID, secretName, addBodySuffix(svalbardsrv.ErrMissingRequestID)},
		// An invalid request: no owner_id_type.
		{reqID, "", ownerID, secretName, addBodySuffix(shareid.ErrMissingOwnerType)},
		// An invalid request: no owner_id.
		{reqID, ownerIDType, "", secretName, addBodySuffix(shareid.ErrMissingOwnerID)},
		// An invalid request: no secret_name.
		{reqID, ownerIDType, ownerID, "", addBodySuffix(shareid.ErrMissingSecretName)},
	}

	for _, tt := range tests {
		w := testingtools.NewFakeResponseWriter()
		req := newGetTokenRequest(tt.reqID, userID{tt.ownerIDType, tt.ownerID}, tt.secretName, "/get_storage_token")
		s.GetStorageTokenHandler(w, req)
		if w.Status != http.StatusBadRequest {
			t.Errorf("GetStorageTokenHandler(%v) status: got [%v], want [%v]", tt, w.Status, http.StatusBadRequest)
		}
		if w.Body != tt.respBody {
			t.Errorf("GetStorageTokenHandler(%v) body: got [%v], want [%v]", tt, w.Body, tt.respBody)
		}
	}
}

func TestGoodRequestsToStoreShare(t *testing.T) {
	rootDir := newTempDir()
	s := getTestServer(rootDir, t)
	ownerIDType, ownerID1, ownerID2 := "FILE", "Tom", "Jerry"
	secretNameA, secretNameB := "Bitcoin key", "Gmail key"
	shareValueA, shareValueB := "some share", "another share"
	reqID1, reqID2 := "a8ehg3", "9egehw"
	unusedReqID := ""
	var tests = []struct {
		url         string
		reqID       string // not used for store_share-requests
		ownerIDType string
		ownerID     string
		secretName  string
		shareValue  string
		respStatus  int
		respBody    string
	}{
		// Get a storage token for a share of owner 1.
		{"/get_storage_token", reqID1, ownerIDType, ownerID1, secretNameA, "",
			http.StatusOK,
			tokenSentResponse(reqID1, userID{ownerIDType, ownerID1}, secretNameA, "storage")},
		// Store a share of owner 1.
		{"/store_share", unusedReqID, ownerIDType, ownerID1, secretNameA, shareValueA,
			http.StatusOK,
			shareStoredResponse(shareData{secretNameA, shareValueA}, userID{ownerIDType, ownerID1})},
		// Get a storage token for a share of owner 2.
		{"/get_storage_token", reqID2, ownerIDType, ownerID2, secretNameB, "",
			http.StatusOK,
			tokenSentResponse(reqID2, userID{ownerIDType, ownerID2}, secretNameB, "storage")},
		// Try to use the token of owner 2 to store a share of owner 1.
		{"/store_share", unusedReqID, ownerIDType, ownerID1, secretNameB, shareValueB,
			http.StatusForbidden,
			"could not store the share: " + addBodySuffix(svalbardsrv.ErrTokenNotValid)},
		// Try to use the token to store a share of a different secret.
		{"/store_share", unusedReqID, ownerIDType, ownerID1, secretNameA, shareValueA,
			http.StatusForbidden,
			"could not store the share: " + addBodySuffix(svalbardsrv.ErrTokenNotValid)},
		// Store a share of owner 2.
		{"/store_share", unusedReqID, ownerIDType, ownerID2, secretNameB, shareValueB,
			http.StatusOK,
			shareStoredResponse(shareData{secretNameB, shareValueB}, userID{ownerIDType, ownerID2})},
	}
	token := ""
	for _, tt := range tests {
		w := testingtools.NewFakeResponseWriter()
		switch tt.url {
		case "/get_storage_token":
			req := newGetTokenRequest(tt.reqID, userID{tt.ownerIDType, tt.ownerID}, tt.secretName, tt.url)
			s.GetStorageTokenHandler(w, req)
			if w.Status == http.StatusOK {
				token = fetchToken(rootDir, tt.ownerID, tt.reqID, t)
			}
		case "/store_share":
			req := newStoreShareRequest(token, userID{tt.ownerIDType, tt.ownerID}, shareData{tt.secretName, tt.shareValue})
			s.StoreShareHandler(w, req)
		default:
			t.Errorf("Unsupported URL path: %s", tt.url)
			continue
		}
		if w.Status != tt.respStatus {
			t.Errorf("StoreShareHandler(%v) status: got [%v], want [%v]", tt, w.Status, tt.respStatus)
		}
		if w.Body != tt.respBody {
			t.Errorf("StoreShareHandler(%v) body: got [%v], want [%v]", tt, w.Body, tt.respBody)
		}
	}
}

func TestBadRequestsToStoreShare(t *testing.T) {
	rootDir := newTempDir()
	s := getTestServer(rootDir, t)
	ownerIDType, ownerID := "FILE", "Tom"
	secretName := "Gmail key"
	shareValue := "some share"
	var tests = []struct {
		token       string
		ownerIDType string
		ownerID     string
		secretName  string
		shareValue  string
		respBody    string
	}{
		// An invalid request: no token.
		{"", ownerIDType, ownerID, secretName, shareValue, addBodySuffix(svalbardsrv.ErrMissingToken)},
		// An invalid request: no owner_id_type.
		{"token1", "", ownerID, secretName, shareValue, addBodySuffix(shareid.ErrMissingOwnerType)},
		// An invalid request: no owner_id.
		{"token2", ownerIDType, "", secretName, shareValue, addBodySuffix(shareid.ErrMissingOwnerID)},
		// An invalid request: no secret_name.
		{"token3", ownerIDType, ownerID, "", shareValue, addBodySuffix(shareid.ErrMissingSecretName)},
		// An invalid request: no share_value.
		{"token4", ownerIDType, ownerID, secretName, "", addBodySuffix(svalbardsrv.ErrMissingShareValue)},
	}
	for _, tt := range tests {
		w := testingtools.NewFakeResponseWriter()
		req := newStoreShareRequest(tt.token, userID{tt.ownerIDType, tt.ownerID}, shareData{tt.secretName, tt.shareValue})
		s.StoreShareHandler(w, req)
		if w.Status != http.StatusBadRequest {
			t.Errorf("StoreShareHandler(%v) status: got [%v], want [%v]", tt, w.Status, http.StatusBadRequest)
		}
		if w.Body != tt.respBody {
			t.Errorf("StoreShareHandler(%v) body: got [%v], want [%v]", tt, w.Body, tt.respBody)
		}
	}
}

func TestRetrievalTokenIssuance(t *testing.T) {
	rootDir := newTempDir()
	s := getTestServer(rootDir, t)
	reqID, ownerIDType, ownerID, secretName := "some_req_id", "FILE", "Alice", "Gmail key"
	unusedReqID := ""
	shareValue := "some share"
	var tests = []struct {
		url         string
		reqID       string // not used for store_share-requests
		ownerIDType string
		ownerID     string
		secretName  string
		respStatus  int
		respBody    string
	}{
		// Try getting a retrieval token for a non-existing share.
		{"/get_retrieval_token", reqID, ownerIDType, ownerID, secretName,
			http.StatusNotFound, shareNotFoundResponse(reqID)},
		// Get a storage token for a share.
		{"/get_storage_token", reqID, ownerIDType, ownerID, secretName,
			http.StatusOK,
			tokenSentResponse(reqID, userID{ownerIDType, ownerID}, secretName, "storage")},
		// Store a share of owner 1.
		{"/store_share", unusedReqID, ownerIDType, ownerID, secretName,
			http.StatusOK,
			shareStoredResponse(shareData{secretName, shareValue}, userID{ownerIDType, ownerID})},
		// Try getting a retrieval token for an existing share.
		{"/get_retrieval_token", reqID, ownerIDType, ownerID, secretName,
			http.StatusOK,
			tokenSentResponse(reqID, userID{ownerIDType, ownerID}, secretName, "retrieval")},
	}

	token := ""
	for _, tt := range tests {
		w := testingtools.NewFakeResponseWriter()
		switch tt.url {
		case "/get_storage_token":
			req := newGetTokenRequest(tt.reqID, userID{tt.ownerIDType, tt.ownerID}, tt.secretName, tt.url)
			s.GetStorageTokenHandler(w, req)
			if w.Status == http.StatusOK {
				token = fetchToken(rootDir, tt.ownerID, tt.reqID, t)
			}
		case "/store_share":
			req := newStoreShareRequest(token, userID{tt.ownerIDType, tt.ownerID}, shareData{tt.secretName, shareValue})
			s.StoreShareHandler(w, req)
		case "/get_retrieval_token":
			req := newGetTokenRequest(tt.reqID, userID{tt.ownerIDType, tt.ownerID}, tt.secretName, tt.url)
			s.GetRetrievalTokenHandler(w, req)
			if w.Status == http.StatusOK {
				token = fetchToken(rootDir, tt.ownerID, tt.reqID, t)
			}
		default:
			t.Errorf("Unsupported URL path: %s", tt.url)
			continue
		}
		if w.Status != tt.respStatus {
			t.Errorf("Unexpected status for request [%v]: got [%v], want [%v]", tt, w.Status, tt.respStatus)
		}
		if w.Body != tt.respBody {
			t.Errorf("Unexpected body for request [%v]: got [%v], want [%v]", tt, w.Body, tt.respBody)
		}
	}
}

func TestRetrievalTokensAreDistinct(t *testing.T) {
	rootDir := newTempDir()
	s := getTestServer(rootDir, t)
	ownerIDType := "FILE"
	ownerID := "Alice"
	secretName := "Gmail key"
	shareValue := "some share"

	// Store a share, so that we can request retrieval tokens for it.
	reqID := "hehge8"
	req := newGetTokenRequest(reqID, userID{ownerIDType, ownerID}, secretName, "/get_storage_token")
	w := testingtools.NewFakeResponseWriter()
	s.GetStorageTokenHandler(w, req)
	if w.Status != http.StatusOK {
		t.Fatalf("Test setup failure: GetStorageTokenHandler(%v) status not OK: %v", req, w.Status)
	}
	token := fetchToken(rootDir, ownerID, reqID, t)
	req = newStoreShareRequest(token, userID{ownerIDType, ownerID}, shareData{secretName, shareValue})
	w = testingtools.NewFakeResponseWriter()
	s.StoreShareHandler(w, req)
	if w.Status != http.StatusOK {
		t.Fatalf("Test setup failure: StoreShareHandler(%v) status not OK: %v", req, w.Status)
	}

	// Request multiple retrieval tokens and check for distinctness.
	allTokens := make(map[string]bool) // Collects all tokens.
	reqCount := 100
	tokenCount := 0
	for i := 0; i < reqCount; i++ {
		// RandomString fails iff rand.Read() fails, which "should never happen".
		reqID, err := util.RandomString(3)
		if err != nil {
			t.Fatalf("Could not generate reqID, util.RandomString(3) error %v", err)
		}
		w := testingtools.NewFakeResponseWriter()
		req := newGetTokenRequest(reqID, userID{ownerIDType, ownerID}, secretName, "/get_retrieval_token")
		s.GetRetrievalTokenHandler(w, req)
		if w.Status != http.StatusOK {
			t.Errorf("GetRetrievalTokenHandler(%v) status: got [%v], want [%v]", req, w.Status, http.StatusOK)
			continue
		}
		token := fetchToken(rootDir, ownerID, reqID, t)
		allTokens[token] = true
		tokenCount++
	}
	if len(allTokens) != tokenCount {
		t.Errorf("The generated tokens are not distinct, there were %v collisions.", tokenCount-len(allTokens))
	}
}

func TestBadRequestsForRetrievalToken(t *testing.T) {
	rootDir := newTempDir()
	s := getTestServer(rootDir, t)
	reqID, ownerIDType, ownerID, secretName := "some_req_id", "FILE", "Alice", "Gmail key"
	var tests = []struct {
		reqID       string
		ownerIDType string
		ownerID     string
		secretName  string
		respBody    string
	}{
		// An invalid request: no request_id.
		{"", ownerIDType, ownerID, secretName, addBodySuffix(svalbardsrv.ErrMissingRequestID)},
		// An invalid request: no owner_id_type.
		{reqID, "", ownerID, secretName, addBodySuffix(shareid.ErrMissingOwnerType)},
		// An invalid request: no owner_id.
		{reqID, ownerIDType, "", secretName, addBodySuffix(shareid.ErrMissingOwnerID)},
		// An invalid request: no secret_name.
		{reqID, ownerIDType, ownerID, "", addBodySuffix(shareid.ErrMissingSecretName)},
	}

	for _, tt := range tests {
		w := testingtools.NewFakeResponseWriter()
		req := newGetTokenRequest(tt.reqID, userID{tt.ownerIDType, tt.ownerID}, tt.secretName, "/get_retrieval_token")
		s.GetRetrievalTokenHandler(w, req)
		if w.Status != http.StatusBadRequest {
			t.Errorf("GetRetrievalTokenHandler(%v) status: got [%v], want [%v]", tt, w.Status, http.StatusBadRequest)
		}
		if w.Body != tt.respBody {
			t.Errorf("GetRetrievalTokenHandler(%v) status: got [%v], want [%v]", tt, w.Body, tt.respBody)
		}
	}
}

func TestGoodRequestsToRetrieveShare(t *testing.T) {
	rootDir := newTempDir()
	s := getTestServer(rootDir, t)
	ownerIDType, ownerID1, ownerID2 := "FILE", "Tom", "Jerry"
	secretNameA, secretNameB := "Bitcoin key", "Gmail key"
	shareValueA, shareValueB := "some share", "another share"
	reqID1, reqID2 := "a8ehg3", "9egehw"
	unusedReqID := ""
	var tests = []struct {
		url         string
		reqID       string // used only for requests for tokens
		ownerIDType string
		ownerID     string
		secretName  string
		shareValue  string
		respStatus  int
		respBody    string
	}{
		// Store two shares for later retrieval.
		{"/get_storage_token", reqID1, ownerIDType, ownerID1, secretNameA, "",
			http.StatusOK,
			tokenSentResponse(reqID1, userID{ownerIDType, ownerID1}, secretNameA, "storage")},
		{"/store_share", unusedReqID, ownerIDType, ownerID1, secretNameA, shareValueA,
			http.StatusOK,
			shareStoredResponse(shareData{secretNameA, shareValueA}, userID{ownerIDType, ownerID1})},
		{"/get_storage_token", reqID2, ownerIDType, ownerID2, secretNameB, "",
			http.StatusOK,
			tokenSentResponse(reqID2, userID{ownerIDType, ownerID2}, secretNameB, "storage")},
		{"/store_share", unusedReqID, ownerIDType, ownerID2, secretNameB, shareValueB,
			http.StatusOK,
			shareStoredResponse(shareData{secretNameB, shareValueB}, userID{ownerIDType, ownerID2})},

		// Get a retrieval token for a share of owner 1.
		{"/get_retrieval_token", reqID1, ownerIDType, ownerID1, secretNameA, "",
			http.StatusOK,
			tokenSentResponse(reqID1, userID{ownerIDType, ownerID1}, secretNameA, "retrieval")},
		// Retrieve a share of owner 1.
		{"/retrieve_share", unusedReqID, ownerIDType, ownerID1, secretNameA, shareValueA,
			http.StatusOK, shareValueA},
		// Get a retrieval token for a share of owner 2.
		{"/get_retrieval_token", reqID2, ownerIDType, ownerID2, secretNameB, "",
			http.StatusOK,
			tokenSentResponse(reqID2, userID{ownerIDType, ownerID2}, secretNameB, "retrieval")},
		// Try to use the token of owner 2 to retrieve a share of owner 1.
		{"/retrieve_share", unusedReqID, ownerIDType, ownerID1, secretNameB, shareValueB,
			http.StatusForbidden,
			"could not retrieve the share: " + addBodySuffix(svalbardsrv.ErrTokenNotValid)},
		// Try to use the token to retrieve a share of a different secret.
		{"/retrieve_share", unusedReqID, ownerIDType, ownerID1, secretNameA, "",
			http.StatusForbidden,
			"could not retrieve the share: " + addBodySuffix(svalbardsrv.ErrTokenNotValid)},
		// Retrieve a share of owner 2.
		{"/retrieve_share", unusedReqID, ownerIDType, ownerID2, secretNameB, "",
			http.StatusOK, shareValueB},
		// Try to get a retrieval token for a non-existing share.
		{"/get_retrieval_token", reqID2, ownerIDType, ownerID2, "non-existing-secret", "",
			http.StatusNotFound, shareNotFoundResponse(reqID2)},
	}
	token := ""
	for _, tt := range tests {
		w := testingtools.NewFakeResponseWriter()
		switch tt.url {
		case "/get_storage_token":
			req := newGetTokenRequest(tt.reqID, userID{tt.ownerIDType, tt.ownerID}, tt.secretName, tt.url)
			s.GetStorageTokenHandler(w, req)
			if w.Status == http.StatusOK {
				token = fetchToken(rootDir, tt.ownerID, tt.reqID, t)
			}
		case "/get_retrieval_token":
			req := newGetTokenRequest(tt.reqID, userID{tt.ownerIDType, tt.ownerID}, tt.secretName, tt.url)
			s.GetRetrievalTokenHandler(w, req)
			if w.Status == http.StatusOK {
				token = fetchToken(rootDir, tt.ownerID, tt.reqID, t)
			}
		case "/store_share":
			req := newStoreShareRequest(token, userID{tt.ownerIDType, tt.ownerID}, shareData{tt.secretName, tt.shareValue})
			s.StoreShareHandler(w, req)
		case "/retrieve_share":
			req := newRetrieveShareRequest(token, userID{tt.ownerIDType, tt.ownerID}, tt.secretName)
			s.RetrieveShareHandler(w, req)
		default:
			t.Errorf("Unsupported URL path: %s", tt.url)
			continue
		}
		if w.Status != tt.respStatus {
			t.Errorf("Unexpected status for request [%v]: got [%v], want [%v]", tt, w.Status, tt.respStatus)
		}
		if w.Body != tt.respBody {
			t.Errorf("Unexpected body for request [%v]: got [%v], want [%v]", tt, w.Body, tt.respBody)
		}
	}
}

func TestBadRequestsToRetrieveShare(t *testing.T) {
	rootDir := newTempDir()
	s := getTestServer(rootDir, t)
	ownerIDType, ownerID := "FILE", "Tom"
	secretName := "Gmail key"
	var tests = []struct {
		token       string
		ownerIDType string
		ownerID     string
		secretName  string
		respBody    string
	}{
		// An invalid request: no token.
		{"", ownerIDType, ownerID, secretName, addBodySuffix(svalbardsrv.ErrMissingToken)},
		// An invalid request: no owner_id_type.
		{"token1", "", ownerID, secretName, addBodySuffix(shareid.ErrMissingOwnerType)},
		// An invalid request: no owner_id.
		{"token2", ownerIDType, "", secretName, addBodySuffix(shareid.ErrMissingOwnerID)},
		// An invalid request: no secret_name.
		{"token3", ownerIDType, ownerID, "", addBodySuffix(shareid.ErrMissingSecretName)},
	}
	for _, tt := range tests {
		w := testingtools.NewFakeResponseWriter()
		req := newRetrieveShareRequest(tt.token, userID{tt.ownerIDType, tt.ownerID}, tt.secretName)
		s.RetrieveShareHandler(w, req)
		if w.Status != http.StatusBadRequest {
			t.Errorf("RetrieveShareHandler(%v) status: got [%v], want [%v]", tt, w.Status, http.StatusBadRequest)
		}
		if w.Body != tt.respBody {
			t.Errorf("RetrieveShareHandler(%v) body: got [%v], want [%v]", tt, w.Body, tt.respBody)
		}
	}
}

func TestDeletionTokenIssuance(t *testing.T) {
	rootDir := newTempDir()
	s := getTestServer(rootDir, t)
	reqID, ownerIDType, ownerID, secretName := "some_req_id", "FILE", "Alice", "Gmail key"
	unusedReqID := ""
	shareValue := "some share"
	var tests = []struct {
		url         string
		reqID       string // not used for store_share-requests
		ownerIDType string
		ownerID     string
		secretName  string
		respStatus  int
		respBody    string
	}{
		// Try getting a deletion token for a non-existing share.
		{"/get_deletion_token", reqID, ownerIDType, ownerID, secretName,
			http.StatusNotFound, shareNotFoundResponse(reqID)},
		// Get a storage token for a share.
		{"/get_storage_token", reqID, ownerIDType, ownerID, secretName,
			http.StatusOK,
			tokenSentResponse(reqID, userID{ownerIDType, ownerID}, secretName, "storage")},
		// Store a share of owner 1.
		{"/store_share", unusedReqID, ownerIDType, ownerID, secretName,
			http.StatusOK,
			shareStoredResponse(shareData{secretName, shareValue}, userID{ownerIDType, ownerID})},
		// Try getting a deletion token for an existing share.
		{"/get_deletion_token", reqID, ownerIDType, ownerID, secretName,
			http.StatusOK,
			tokenSentResponse(reqID, userID{ownerIDType, ownerID}, secretName, "deletion")},
	}

	token := ""
	for _, tt := range tests {
		w := testingtools.NewFakeResponseWriter()
		switch tt.url {
		case "/get_storage_token":
			req := newGetTokenRequest(tt.reqID, userID{tt.ownerIDType, tt.ownerID}, tt.secretName, tt.url)
			s.GetStorageTokenHandler(w, req)
			if w.Status == http.StatusOK {
				token = fetchToken(rootDir, tt.ownerID, tt.reqID, t)
			}
		case "/store_share":
			req := newStoreShareRequest(token, userID{tt.ownerIDType, tt.ownerID}, shareData{tt.secretName, shareValue})
			s.StoreShareHandler(w, req)
		case "/get_deletion_token":
			req := newGetTokenRequest(tt.reqID, userID{tt.ownerIDType, tt.ownerID}, tt.secretName, tt.url)
			s.GetDeletionTokenHandler(w, req)
			if w.Status == http.StatusOK {
				token = fetchToken(rootDir, tt.ownerID, tt.reqID, t)
			}
		default:
			t.Errorf("Unsupported URL path: %s", tt.url)
			continue
		}
		if w.Status != tt.respStatus {
			t.Errorf("Unexpected status for request [%v]: got [%v], want [%v]", tt, w.Status, tt.respStatus)
		}
		if w.Body != tt.respBody {
			t.Errorf("Unexpected body for request [%v]: got [%v], want [%v]", tt, w.Body, tt.respBody)
		}
	}
}

func TestDeletionTokensAreDistinct(t *testing.T) {
	rootDir := newTempDir()
	s := getTestServer(rootDir, t)
	ownerIDType := "FILE"
	ownerID := "Alice"
	secretName := "Gmail key"
	shareValue := "some share"

	// Store a share, so that we can request deletion tokens for it.
	reqID := "hehge8"
	req := newGetTokenRequest(reqID, userID{ownerIDType, ownerID}, secretName, "/get_storage_token")
	w := testingtools.NewFakeResponseWriter()
	s.GetStorageTokenHandler(w, req)
	if w.Status != http.StatusOK {
		t.Fatalf("Could setup the test, request for a token returned status %v", w.Status)
	}
	token := fetchToken(rootDir, ownerID, reqID, t)
	req = newStoreShareRequest(token, userID{ownerIDType, ownerID}, shareData{secretName, shareValue})
	w = testingtools.NewFakeResponseWriter()
	s.StoreShareHandler(w, req)
	if w.Status != http.StatusOK {
		t.Fatalf("Could setup the test, request to store a share returned status %v", w.Status)
	}

	// Request multiple deletion tokens and check for distinctness.
	allTokens := make(map[string]bool) // Collects all tokens.
	reqCount := 100
	tokenCount := 0
	for i := 0; i < reqCount; i++ {
		reqID, err := util.RandomString(3)
		if err != nil {
			t.Fatalf("Could not generate reqID, util.RandomString(3) error %v", err)
		}
		w := testingtools.NewFakeResponseWriter()
		req := newGetTokenRequest(reqID, userID{ownerIDType, ownerID}, secretName, "/get_deletion_token")
		s.GetDeletionTokenHandler(w, req)
		if w.Status != http.StatusOK {
			t.Errorf("GetDeletionTokenHandler(%v): got [%v], want [%v]", req, w.Status, http.StatusOK)
			continue
		}

		token := fetchToken(rootDir, ownerID, reqID, t)
		allTokens[token] = true
		tokenCount++
	}
	if len(allTokens) != tokenCount {
		t.Errorf("The generated tokens are not distinct, there were %v collisions.", tokenCount-len(allTokens))
	}
}

func TestBadRequestsForDeletionToken(t *testing.T) {
	rootDir := newTempDir()
	s := getTestServer(rootDir, t)
	reqID, ownerIDType, ownerID, secretName := "some_req_id", "FILE", "Alice", "Gmail key"
	var tests = []struct {
		reqID       string
		ownerIDType string
		ownerID     string
		secretName  string
		respBody    string
	}{
		// An invalid request: no request_id.
		{"", ownerIDType, ownerID, secretName, addBodySuffix(svalbardsrv.ErrMissingRequestID)},
		// An invalid request: no owner_id_type.
		{reqID, "", ownerID, secretName, addBodySuffix(shareid.ErrMissingOwnerType)},
		// An invalid request: no owner_id.
		{reqID, ownerIDType, "", secretName, addBodySuffix(shareid.ErrMissingOwnerID)},
		// An invalid request: no secret_name.
		{reqID, ownerIDType, ownerID, "", addBodySuffix(shareid.ErrMissingSecretName)},
	}

	for _, tt := range tests {
		w := testingtools.NewFakeResponseWriter()
		req := newGetTokenRequest(tt.reqID, userID{tt.ownerIDType, tt.ownerID}, tt.secretName, "/get_deletion_token")
		s.GetDeletionTokenHandler(w, req)
		if w.Status != http.StatusBadRequest {
			t.Errorf("GetDeletionTokenHandler(%v) status: got [%v], want [%v]", tt, w.Status, http.StatusBadRequest)
		}
		if w.Body != tt.respBody {
			t.Errorf("GetDeletionTokenHandler(%v) body: got [%v], want [%v]", tt, w.Body, tt.respBody)
		}
	}
}

func TestGoodRequestsToDeleteShare(t *testing.T) {
	rootDir := newTempDir()
	s := getTestServer(rootDir, t)
	ownerIDType, ownerID1, ownerID2 := "FILE", "Tom", "Jerry"
	secretNameA, secretNameB := "Bitcoin key", "Gmail key"
	shareValueA, shareValueB := "some share", "another share"
	reqID1, reqID2 := "a8ehg3", "9egehw"
	unusedReqID := ""
	var tests = []struct {
		url         string
		reqID       string // used only for requests for tokens
		ownerIDType string
		ownerID     string
		secretName  string
		shareValue  string
		respStatus  int
		respBody    string
	}{
		// Store two shares for later deletion.
		{"/get_storage_token", reqID1, ownerIDType, ownerID1, secretNameA, "",
			http.StatusOK,
			tokenSentResponse(reqID1, userID{ownerIDType, ownerID1}, secretNameA, "storage")},
		{"/store_share", unusedReqID, ownerIDType, ownerID1, secretNameA, shareValueA,
			http.StatusOK,
			shareStoredResponse(shareData{secretNameA, shareValueA}, userID{ownerIDType, ownerID1})},
		{"/get_storage_token", reqID2, ownerIDType, ownerID2, secretNameB, "",
			http.StatusOK,
			tokenSentResponse(reqID2, userID{ownerIDType, ownerID2}, secretNameB, "storage")},
		{"/store_share", unusedReqID, ownerIDType, ownerID2, secretNameB, shareValueB,
			http.StatusOK,
			shareStoredResponse(shareData{secretNameB, shareValueB}, userID{ownerIDType, ownerID2})},

		// Get a deletion token for a share of owner 1.
		{"/get_deletion_token", reqID1, ownerIDType, ownerID1, secretNameA, "",
			http.StatusOK,
			tokenSentResponse(reqID1, userID{ownerIDType, ownerID1}, secretNameA, "deletion")},
		// Delete a share of owner 1.
		{"/delete_share", unusedReqID, ownerIDType, ownerID1, secretNameA, "",
			http.StatusOK,
			shareDeletedResponse(secretNameA, userID{ownerIDType, ownerID1})},
		// Get a deletion token for a share of owner 2.
		{"/get_deletion_token", reqID2, ownerIDType, ownerID2, secretNameB, "",
			http.StatusOK,
			tokenSentResponse(reqID2, userID{ownerIDType, ownerID2}, secretNameB, "deletion")},
		// Try to use the token of owner 2 to delete a share of owner 1.
		{"/delete_share", unusedReqID, ownerIDType, ownerID1, secretNameB, shareValueB,
			http.StatusForbidden,
			"could not delete the share: " + addBodySuffix(svalbardsrv.ErrTokenNotValid)},
		// Try to use the token to delete a share of a different secret.
		{"/delete_share", unusedReqID, ownerIDType, ownerID1, secretNameA, "",
			http.StatusForbidden,
			"could not delete the share: " + addBodySuffix(svalbardsrv.ErrTokenNotValid)},
		// Delete a share of owner 2.
		{"/delete_share", unusedReqID, ownerIDType, ownerID2, secretNameB, "",
			http.StatusOK,
			shareDeletedResponse(secretNameB, userID{ownerIDType, ownerID2})},
		// Try to get a deletion token for a deleted share.
		{"/get_deletion_token", reqID2, ownerIDType, ownerID2, secretNameB, "",
			http.StatusNotFound, shareNotFoundResponse(reqID2)},
	}
	token := ""
	for _, tt := range tests {
		w := testingtools.NewFakeResponseWriter()
		switch tt.url {
		case "/get_storage_token":
			req := newGetTokenRequest(tt.reqID, userID{tt.ownerIDType, tt.ownerID}, tt.secretName, tt.url)
			s.GetStorageTokenHandler(w, req)
			if w.Status == http.StatusOK {
				token = fetchToken(rootDir, tt.ownerID, tt.reqID, t)
			}
		case "/get_deletion_token":
			req := newGetTokenRequest(tt.reqID, userID{tt.ownerIDType, tt.ownerID}, tt.secretName, tt.url)
			s.GetDeletionTokenHandler(w, req)
			if w.Status == http.StatusOK {
				token = fetchToken(rootDir, tt.ownerID, tt.reqID, t)
			}
		case "/store_share":
			req := newStoreShareRequest(token, userID{tt.ownerIDType, tt.ownerID}, shareData{tt.secretName, tt.shareValue})
			s.StoreShareHandler(w, req)
		case "/delete_share":
			req := newDeleteShareRequest(token, userID{tt.ownerIDType, tt.ownerID}, tt.secretName)
			s.DeleteShareHandler(w, req)
		default:
			t.Errorf("Unsupported URL path: %s", tt.url)
			continue
		}
		if w.Status != tt.respStatus {
			t.Errorf("Unexpected status for request [%v]: got [%v], want [%v]", tt, w.Status, tt.respStatus)
		}
		if w.Body != tt.respBody {
			t.Errorf("Unexpected body for request [%v]: got [%v], want [%v]", tt, w.Body, tt.respBody)
		}
	}
}

func TestBadRequestsToDeleteShare(t *testing.T) {
	rootDir := newTempDir()
	s := getTestServer(rootDir, t)
	ownerIDType, ownerID := "FILE", "Tom"
	secretName := "Gmail key"
	var tests = []struct {
		token       string
		ownerIDType string
		ownerID     string
		secretName  string
		respBody    string
	}{
		// An invalid request: no token.
		{"", ownerIDType, ownerID, secretName, addBodySuffix(svalbardsrv.ErrMissingToken)},
		// An invalid request: no owner_id_type.
		{"token1", "", ownerID, secretName, addBodySuffix(shareid.ErrMissingOwnerType)},
		// An invalid request: no owner_id.
		{"token2", ownerIDType, "", secretName, addBodySuffix(shareid.ErrMissingOwnerID)},
		// An invalid request: no secret_name.
		{"token3", ownerIDType, ownerID, "", addBodySuffix(shareid.ErrMissingSecretName)},
	}
	for _, tt := range tests {
		w := testingtools.NewFakeResponseWriter()
		req := newDeleteShareRequest(tt.token, userID{tt.ownerIDType, tt.ownerID}, tt.secretName)
		s.DeleteShareHandler(w, req)
		if w.Status != http.StatusBadRequest {
			t.Errorf("DeleteShareHandler(%v) status: got [%v], want [%v]", tt, w.Status, http.StatusBadRequest)
		}
		if w.Body != tt.respBody {
			t.Errorf("DeleteShareHandler(%v) body: got [%v], want [%v]", tt, w.Body, tt.respBody)
		}
	}
}

func TestNonPostRequests(t *testing.T) {
	rootDir := newTempDir()
	s := getTestServer(rootDir, t)
	expectedBody := addBodySuffix(svalbardsrv.ErrExpectedPostRequest)

	reqBody := bufio.NewReader(strings.NewReader("some request body"))

	var tests = []struct {
		path        string
		handler     func(w http.ResponseWriter, r *http.Request)
		handlerName string
	}{
		{"/get_storage_token", s.GetStorageTokenHandler, "GetStorageTokenHandler"},
		{"/store_share", s.StoreShareHandler, "StoreShareHandler"},
		{"/get_retrieval_token", s.GetRetrievalTokenHandler, "GetRetrievalTokenHandler"},
		{"/retrieve_share", s.RetrieveShareHandler, "RetrieveShareHandler"},
		{"/get_deletion_token", s.GetDeletionTokenHandler, "GetDeletionTokenHandler"},
		{"/delete_share", s.DeleteShareHandler, "DeleteShareHandler"},
	}
	for _, tt := range tests {
		req := httptest.NewRequest("GET", testTarget+tt.path, reqBody)
		w := testingtools.NewFakeResponseWriter()
		tt.handler(w, req)
		if w.Status != http.StatusBadRequest {
			t.Errorf("%v(%v) status: got [%v], want [%v]", tt.handlerName, req, w.Status, http.StatusBadRequest)
		}
		if w.Body != expectedBody {
			t.Errorf("%v(%v) body: got [%v], want [%v]", tt.handlerName, req, w.Body, expectedBody)
		}
	}
}
