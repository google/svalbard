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
 * ShareManager manages shares of a specific location type.
 */
public interface ShareManager {
  /**
   * Stores the given 'shareValue' at the specified 'location', where
   * the storage location uses 'secretName' as a label for the stored share.
   *
   * @param secretName name of the secret to which the share belongs to.
   * @param shareValue the actual value of the share.
   * @param location location at which the share should be stored.
   */
  public ListenableFuture<Void> storeShare(
      String secretName, byte[] shareValue, ShareLocation location)
      throws GeneralSecurityException;

  /**
   * Retrieves from the specified 'location' a share that belongs to a sharing
   * of a secret value named by 'secretName'.
   *
   * @param secretName name of the secret to which the share belongs to.
   * @param location location from which the share should be retrieved.
   */
  public ListenableFuture<byte[]> retrieveShare(String secretName, ShareLocation location)
      throws GeneralSecurityException;

  /**
   * Deletes at the specified 'location' a share that belongs to a sharing
   * of a secret value named by 'secretName'.
   *
   * @param secretName name of the secret to which the share belongs to.
   * @param location location at which the share should be deleted.
   */
  public ListenableFuture<Void> deleteShare(String secretName, ShareLocation location)
      throws GeneralSecurityException;
}
