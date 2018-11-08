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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.security.svalbard.proto.LocationType;
import com.google.security.svalbard.proto.ShareLocation;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;

/**
 * A ShareManager that manages shares stored at a Svalbard server.
 */
public final class ServerShareManager implements ShareManager {
  public static final String USER_AGENT = "Svalbard/1.0.0";
  private final CloseableHttpClient httpClient;
  private final SecondaryChannel secondaryChannel;
  private final ListeningExecutorService executorService;
  private static final SecureRandom rand;

  static {
    rand = new SecureRandom();
  }

  public ServerShareManager(
      SecondaryChannel secondaryChannel, ListeningExecutorService executorService) {
    this.httpClient = HttpClientBuilder.create().build();
    this.secondaryChannel = secondaryChannel;
    this.executorService = executorService;
  }

  /**
   * Retrieves from server specified in 'location' a short-lived access token
   * for the given 'operation' on a share of the secret specified by 'secretName'.
   * 'operation' must be one of "storage", "retrieval", "deletion".
   */
  protected ListenableFuture<String> getOperationToken(String requestId, String operation,
      ShareLocation location, String secretName)
      throws GeneralSecurityException {
    if (location.getLocationType() != LocationType.SVALBARD_SERVER) {
      throw new GeneralSecurityException("Wrong LocationType: " + location.getLocationType());
    }
    List<BasicNameValuePair> parameters = new ArrayList<>();
    parameters.add(new BasicNameValuePair("request_id", requestId));
    parameters.add(new BasicNameValuePair("owner_id_type", location.getOwnerIdType()));
    parameters.add(new BasicNameValuePair("owner_id", location.getOwnerId()));
    parameters.add(new BasicNameValuePair("secret_name", secretName));
    String url = location.getLocationName();
    if (url.isEmpty()) {
      throw new GeneralSecurityException("Missing location_name");
    }
    if (!url.startsWith("https")) {
      throw new GeneralSecurityException("location_name name must start with 'https'.");
    }
    HttpPost httpPost = new HttpPost(url + "/get_" + operation + "_token");
    httpPost.setHeader("User-Agent", USER_AGENT);
    try {
      httpPost.setEntity(new UrlEncodedFormEntity(parameters));
    } catch (UnsupportedEncodingException e) {
      throw new GeneralSecurityException(e);
    }
    return executorService.submit(
        () -> {
          try {
            HttpResponse response = httpClient.execute(httpPost);
            int statusCode = response.getStatusLine().getStatusCode();
            InputStream inputStream = response.getEntity().getContent();
            if (statusCode != 200) {
              throw new GeneralSecurityException("request for a " + operation + "-token failed: "
                  + IOUtils.toString(inputStream, "UTF-8"));
            }
            return secondaryChannel.readToken(
                location.getOwnerIdType(), location.getOwnerId(), requestId);
          } catch (IOException e) {
            throw new GeneralSecurityException(e);
          }
        });
  }


  @Override
  public ListenableFuture<Void> storeShare(
      String secretName, byte[] shareValue, ShareLocation location)
      throws GeneralSecurityException {
    if (location.getLocationType() == LocationType.SVALBARD_SERVER) {
      String requestId = getNewRequestId();
      return Futures.transformAsync(
          getOperationToken(requestId, "storage", location, secretName),
          token -> {
            storeShare(secretName, shareValue, location, token);
            return Futures.immediateFuture(null);
          },
          executorService);
    } else {
      throw new GeneralSecurityException("Unsupported LocationType: " + location.getLocationType());
    }
  }

  protected void storeShare(String secretName, byte[] shareValue,
      ShareLocation location, String token) throws GeneralSecurityException {
    if (location.getLocationType() != LocationType.SVALBARD_SERVER) {
      throw new GeneralSecurityException("Wrong LocationType: " + location.getLocationType());
    }
    List<BasicNameValuePair> parameters = new ArrayList<>();
    parameters.add(new BasicNameValuePair("owner_id_type", location.getOwnerIdType()));
    parameters.add(new BasicNameValuePair("owner_id", location.getOwnerId()));
    parameters.add(new BasicNameValuePair("secret_name", secretName));
    parameters.add(new BasicNameValuePair("share_value", Base64.encodeBase64String(shareValue)));
    parameters.add(new BasicNameValuePair("token", token));
    String url = location.getLocationName();
    if (url.isEmpty()) {
      throw new GeneralSecurityException("Missing location_name");
    }
    HttpPost httpPost = new HttpPost(url + "/store_share");
    httpPost.setHeader("User-Agent", USER_AGENT);
    try {
      httpPost.setEntity(new UrlEncodedFormEntity(parameters));
    } catch (UnsupportedEncodingException e) {
      throw new GeneralSecurityException(e);
    }

    try {
      HttpResponse response = httpClient.execute(httpPost);
      int statusCode = response.getStatusLine().getStatusCode();
      InputStream inputStream = response.getEntity().getContent();
      if (statusCode != 200) {
        throw new GeneralSecurityException("request to store a share failed: "
            + IOUtils.toString(inputStream, "UTF-8"));
      }
    } catch (IOException e) {
      throw new GeneralSecurityException(e);
    }
  }

  @Override
  public ListenableFuture<byte[]> retrieveShare(
      String secretName, ShareLocation location)
      throws GeneralSecurityException {
    if (location.getLocationType() == LocationType.SVALBARD_SERVER) {
      String requestId = getNewRequestId();
      return Futures.transformAsync(
          getOperationToken(requestId, "retrieval", location, secretName),
          token -> {
            return Futures.immediateFuture(retrieveShare(secretName, location, token));
          },
          executorService);
    } else {
      throw new GeneralSecurityException("Unsupported LocationType: " + location.getLocationType());
    }
  }

  protected byte[] retrieveShare(String secretName,
      ShareLocation location, String token) throws GeneralSecurityException {
    if (location.getLocationType() != LocationType.SVALBARD_SERVER) {
      throw new GeneralSecurityException("Wrong LocationType: " + location.getLocationType());
    }
    List<BasicNameValuePair> parameters = new ArrayList<>();
    parameters.add(new BasicNameValuePair("owner_id_type", location.getOwnerIdType()));
    parameters.add(new BasicNameValuePair("owner_id", location.getOwnerId()));
    parameters.add(new BasicNameValuePair("secret_name", secretName));
    parameters.add(new BasicNameValuePair("token", token));
    String url = location.getLocationName();
    if (url.isEmpty()) {
      throw new GeneralSecurityException("Missing location_name");
    }
    HttpPost httpPost = new HttpPost(url + "/retrieve_share");
    httpPost.setHeader("User-Agent", USER_AGENT);
    try {
      httpPost.setEntity(new UrlEncodedFormEntity(parameters));
    } catch (UnsupportedEncodingException e) {
      throw new GeneralSecurityException(e);
    }

    try {
      HttpResponse response = httpClient.execute(httpPost);
      int statusCode = response.getStatusLine().getStatusCode();
      InputStream inputStream = response.getEntity().getContent();
      if (statusCode != 200) {
        throw new GeneralSecurityException("request to retrieve a share failed: "
            + IOUtils.toString(inputStream, "UTF-8"));
      }
      String base64Share = IOUtils.toString(inputStream, "UTF-8");
      return Base64.decodeBase64(base64Share);
    } catch (IOException e) {
      throw new GeneralSecurityException(e);
    }
  }

  @Override
  public ListenableFuture<Void> deleteShare(
      String secretName, ShareLocation location)
      throws GeneralSecurityException {
    if (location.getLocationType() == LocationType.SVALBARD_SERVER) {
      String requestId = getNewRequestId();
      return Futures.transformAsync(
          getOperationToken(requestId, "deletion", location, secretName),
          token -> {
            deleteShare(secretName, location, token);
            return Futures.immediateFuture(null);
          },
          executorService);
    } else {
      throw new GeneralSecurityException("Unsupported LocationType: " + location.getLocationType());
    }
  }

  protected void deleteShare(String secretName,
      ShareLocation location, String token) throws GeneralSecurityException {
    if (location.getLocationType() != LocationType.SVALBARD_SERVER) {
      throw new GeneralSecurityException("Wrong LocationType: " + location.getLocationType());
    }
    List<BasicNameValuePair> parameters = new ArrayList<>();
    parameters.add(new BasicNameValuePair("owner_id_type", location.getOwnerIdType()));
    parameters.add(new BasicNameValuePair("owner_id", location.getOwnerId()));
    parameters.add(new BasicNameValuePair("secret_name", secretName));
    parameters.add(new BasicNameValuePair("token", token));
    String url = location.getLocationName();
    if (url.isEmpty()) {
      throw new GeneralSecurityException("Missing location_name");
    }
    HttpPost httpPost = new HttpPost(url + "/delete_share");
    httpPost.setHeader("User-Agent", USER_AGENT);
    try {
      httpPost.setEntity(new UrlEncodedFormEntity(parameters));
    } catch (UnsupportedEncodingException e) {
      throw new GeneralSecurityException(e);
    }

    try {
      HttpResponse response = httpClient.execute(httpPost);
      int statusCode = response.getStatusLine().getStatusCode();
      InputStream inputStream = response.getEntity().getContent();
      if (statusCode != 200) {
        throw new GeneralSecurityException("request to delete a share failed: "
            + IOUtils.toString(inputStream, "UTF-8"));
      }
    } catch (IOException e) {
      throw new GeneralSecurityException(e);
    }
  }

  protected static String getNewRequestId() {
    return String.valueOf(rand.nextInt(10000));
  }
}
