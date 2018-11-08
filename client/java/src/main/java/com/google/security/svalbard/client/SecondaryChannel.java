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

package com.google.security.svalbard.client;

import java.security.GeneralSecurityException;

/**
 * SecondaryChannel enables a secondary, one-way communication from server to client.
 * It is used for receiving short-lived tokens that authorize various operations.
 */
public interface SecondaryChannel {
  /**
   * Returns a token from a message sent via a secondary channel.  The channel is
   * determined by (recipientIdType, recipientId)-pair, and the relevant message on the channel
   * is identified by requestId.
   *
   * @param recipientIdType indicates the type of the secondary channel (SMS, e-mail, ...)
   * @param recipientId identifies the recipient of the messages on the secondary channel
   * @param requestId identifies the message that should contain the desired token
   */
  String readToken(String recipientIdType, String recipientId, String requestId)
      throws GeneralSecurityException;
}
