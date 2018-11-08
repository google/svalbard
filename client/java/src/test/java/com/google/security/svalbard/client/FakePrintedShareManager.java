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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.security.svalbard.proto.ShareLocation;
import java.security.GeneralSecurityException;

/**
 * A ShareManager for testing only, that manages shares with LocationType == PRINTED_COPY.
 * It does not truly print shares or retrieve shares from printed copies, but
 * only caches the shares in the memory, and returnes the copies upon request.
 */
public class FakePrintedShareManager implements ShareManager {
  @Override
  public ListenableFuture<Void> storeShare(
      String secretName, byte[] shareValue, ShareLocation location)
      throws GeneralSecurityException {
    throw new GeneralSecurityException("Not implemented yet.");
  }

  @Override
  public ListenableFuture<byte[]> retrieveShare(String secretName, ShareLocation location)
      throws GeneralSecurityException {
    throw new GeneralSecurityException("Not implemented yet.");
  }

  @Override
  public ListenableFuture<Void> deleteShare(String secretName, ShareLocation location)
      throws GeneralSecurityException {
    throw new GeneralSecurityException("Not implemented yet.");
  }
}
