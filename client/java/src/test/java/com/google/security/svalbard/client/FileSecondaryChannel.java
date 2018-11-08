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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;

/**
 * FileSecondaryChannel is a SecondaryChannel implementation based on files.
 * A secondary channel to a user identified by 'userID' is just a file named
 * userID + "secondary_channel.txt" in the root directory of the channel,
 * and each message sent via the channel is written to the file on a separate line.
 * It is intended for testing only.
 */
public final class FileSecondaryChannel implements SecondaryChannel {

  public FileSecondaryChannel(String rootDir) {
    this.rootDir = rootDir;
  }

  /**
   * Returns a token from a message send via a secondary channel.  The channel is
   * determined by (recipientIdType, recipientId)-pair, and the relevant message on the channel
   * is identified by requestId.
   *
   * @param recipientIdType indicates the type of the secondary channel (SMS, e-mail, ...)
   * @param recipientId identifies the recipient of the messages on the secondary channel
   * @param requestId identifies the message that should contain the desired token
   */
  @Override
  public String readToken(String recipientIdType, String recipientId, String requestId)
      throws GeneralSecurityException {
    if (!recipientIdType.equals("FILE")) {
      throw new GeneralSecurityException(
          "Recipient IdType '" + recipientIdType + "' not supported");
    }
    String filename = getFilename(recipientId);
    String prefix = "SVBD:" + requestId;
    try {
      return readToken(filename, prefix);
    } catch (IOException e) {
      throw new GeneralSecurityException(e);
    }
  }

  private String readToken(String filename, String prefix)
      throws GeneralSecurityException, IOException {
    BufferedReader bufferedReader = Files.newBufferedReader(Paths.get(filename), UTF_8);
    String line = bufferedReader.readLine();
    while (line != null) {
      if (line.startsWith(prefix)) {
        return line.substring(prefix.length() + 1);
      }
      line = bufferedReader.readLine();
    }
    throw new GeneralSecurityException("Token not found.");
  }

  private String getFilename(String recipientId) {
    return Paths.get(rootDir, recipientId + "_secondary_channel.txt").toString();
  }

  private final String rootDir;
}
