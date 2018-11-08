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

// Package testingtools offers tools useful for testing.
package testingtools

import (
	"net/http"
)

// FakeResponseWriter implements http.ResponseWriter interface.
// (see https://golang.org/pkg/net/http/#ResponseWriter)
type FakeResponseWriter struct {
	header    http.Header
	Body      string
	Status    int
	statusSet bool
}

// NewFakeResponseWriter returns a new FakeResponseWriter.
func NewFakeResponseWriter() *FakeResponseWriter {
	return &FakeResponseWriter{
		header:    make(http.Header),
		statusSet: false,
	}
}

// Header returns the header map that will be sent by WriteHeader.
func (r *FakeResponseWriter) Header() http.Header {
	return r.header
}

// Write writes the data to the connection as part of an HTTP reply.
func (r *FakeResponseWriter) Write(body []byte) (int, error) {
	if !r.statusSet {
		r.WriteHeader(http.StatusOK)
		r.statusSet = true
	}
	r.Body = string(body)
	return len(body), nil
}

// WriteHeader sends an HTTP response header with status code.
func (r *FakeResponseWriter) WriteHeader(status int) {
	r.statusSet = true
	r.Status = status
}
