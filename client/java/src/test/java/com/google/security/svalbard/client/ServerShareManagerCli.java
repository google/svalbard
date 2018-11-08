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


import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.TextFormat;
import com.google.security.svalbard.proto.ShareLocation;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A command-line utility for testing ServerShareManager-class.
 * It requires at least 2 arguments:
 *   rootDir: a directory for the secondary communication channel
 *   operation: the operation to be performed by the client
 *   params:  params for the client request (optional, depending on operation)
 */
public class ServerShareManagerCli {
  public static final Charset UTF_8 = Charset.forName("UTF-8");

  public static void main(String[] args) throws Exception {
   if (args.length < 2) {
      System.out.println(
          "Usage: ServerShareManagerCli root-dir operation ... ");
      System.exit(1);
    }
    String rootDir = args[0];
    String operation = args[1];

    FileSecondaryChannel fileChannel = new FileSecondaryChannel(rootDir);
    ServerShareManager manager =
        new ServerShareManager(fileChannel, MoreExecutors.newDirectExecutorService());
    switch (operation) {
      case "get_deletion_token":     // fall through
      case "get_retrieval_token":    // fall through
      case "get_storage_token":
        {
          Matcher matcher = Pattern.compile("get_(.*)_token").matcher(operation);
          if (!matcher.matches()) {
            System.out.println("Internal regex matcher error: " + operation);
            System.exit(1);
          }
          String op = matcher.group(1);
          if (args.length != 5) {
            System.out.println(
                "Usage: SvalbardClientCli root-dir " + operation
                + " req-id-filename secret-name share-location");
            System.exit(1);
          }
          String reqIdFilename = args[2];
          String secretName = args[3];
          ShareLocation.Builder locationBuilder = ShareLocation.newBuilder();
          TextFormat.merge(args[4], locationBuilder);
          String requestId = ServerShareManager.getNewRequestId();
          try {
            String token = manager.getOperationToken(
                requestId, op, locationBuilder.build(), secretName).get();
            System.out.println("Got " + op + "-token: " + token);
            write(requestId.getBytes(UTF_8), reqIdFilename);
          } catch (GeneralSecurityException | ExecutionException e) {
            System.out.println("Failure: " + e);
            System.exit(1);
          }
        }
        break;
      case "store_share":
        {
          if (args.length != 5) {
            System.out.println(
                "Usage: SvalbardClientCli root-dir " + operation
                + " secret-name share-value share-location");
            System.exit(1);
          }
          String secretName = args[2];
          String shareValue = args[3];
          String locationText = args[4];
          ShareLocation.Builder locationBuilder = ShareLocation.newBuilder();
          TextFormat.merge(locationText, locationBuilder);
          try {
            manager.storeShare(secretName, shareValue.getBytes(UTF_8),
                locationBuilder.build()).get();
            System.out.println("--- Stored.");
          } catch (GeneralSecurityException e) {
            System.out.println("Failure: " + e);
            System.exit(1);
          }
        }
        break;
      case "retrieve_share":
        {
          if (args.length != 5) {
            System.out.println(
                "Usage: SvalbardClientCli root-dir " + operation
                + " secret-name share-location share-file");
            System.exit(1);
          }
          String secretName = args[2];
          String locationText = args[3];
          String shareFilename = args[4];
          ShareLocation.Builder locationBuilder = ShareLocation.newBuilder();
          TextFormat.merge(locationText, locationBuilder);
          try {
            byte[] share = manager.retrieveShare(secretName, locationBuilder.build()).get();
            System.out.println("Retrieved.");
            write(share, shareFilename);
          } catch (GeneralSecurityException e) {
            System.out.println("Failure: " + e);
            System.exit(1);
          }
        }
        break;
      case "delete_share":
        {
          if (args.length != 4) {
            System.out.println(
                "Usage: SvalbardClientCli root-dir " + operation
                + " secret-name share-location");
            System.exit(1);
          }
          String secretName = args[2];
          String locationText = args[3];
          ShareLocation.Builder locationBuilder = ShareLocation.newBuilder();
          TextFormat.merge(locationText, locationBuilder);
          try {
            manager.deleteShare(secretName, locationBuilder.build()).get();
            System.out.println("Deleted.");
          } catch (GeneralSecurityException e) {
            System.out.println("Failure: " + e);
            System.exit(1);
          }
        }
        break;
      case "store_share_with_token":
        {
          if (args.length != 6) {
            System.out.println(
                "Usage: SvalbardClientCli root-dir " + operation
                + " secret-name share-value share-location token");
            System.exit(1);
          }
          String secretName = args[2];
          String shareValue = args[3];
          String locationText = args[4];
          String token = args[5];
          ShareLocation.Builder locationBuilder = ShareLocation.newBuilder();
          TextFormat.merge(locationText, locationBuilder);
          try {
            manager.storeShare(
                secretName, shareValue.getBytes(UTF_8), locationBuilder.build(), token);
            System.out.println("Stored.");
          } catch (GeneralSecurityException e) {
            System.out.println("Failure: " + e);
            System.exit(1);
          }
        }
        break;
      case "retrieve_share_with_token":
        {
          if (args.length != 6) {
            System.out.println(
                "Usage: SvalbardClientCli root-dir " + operation
                + " secret-name share-location token share-file");
            System.exit(1);
          }
          String secretName = args[2];
          String locationText = args[3];
          String token = args[4];
          String shareFilename = args[5];
          ShareLocation.Builder locationBuilder = ShareLocation.newBuilder();
          TextFormat.merge(locationText, locationBuilder);
          try {
            byte[] share = manager.retrieveShare(secretName, locationBuilder.build(), token);
            System.out.println("Retrieved.");
            write(share, shareFilename);
          } catch (GeneralSecurityException e) {
            System.out.println("Failure: " + e);
            System.exit(1);
          }
        }
        break;
      case "delete_share_with_token":
        {
          if (args.length != 5) {
            System.out.println(
                "Usage: SvalbardClientCli root-dir " + operation
                + " secret-name share-location token");
            System.exit(1);
          }
          String secretName = args[2];
          String locationText = args[3];
          String token = args[4];
          ShareLocation.Builder locationBuilder = ShareLocation.newBuilder();
          TextFormat.merge(locationText, locationBuilder);
          try {
            manager.deleteShare(secretName, locationBuilder.build(), token);
            System.out.println("Deleted.");
          } catch (GeneralSecurityException e) {
            System.out.println("Failure: " + e);
            System.exit(1);
          }
        }
        break;
      default:
        throw new IllegalArgumentException("Unsupported operation: " + operation);
    }
  }

  private static void write(byte[] contents, String filename) throws IOException {
    try (OutputStream outputStream = new FileOutputStream(Paths.get(filename).toFile())) {
      outputStream.write(contents);
    }
  }

  public static byte[] read(String filename) throws GeneralSecurityException, IOException {
    InputStream inputStream = new FileInputStream(Paths.get(filename).toFile());
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int length;
    while ((length = inputStream.read(buffer)) != -1) {
      result.write(buffer, 0, length);
    }
    inputStream.close();
    return result.toByteArray();
  }

}
