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

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.TextFormat;
import com.google.security.svalbard.client.SvalbardClient.ShareData;
import com.google.security.svalbard.proto.ShareLocation;
import com.google.security.svalbard.proto.SharedSecretMetadata;
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
import java.util.concurrent.Executors;

/**
 * A command-line utility for testing SvalbardClient-class.
 * It requires at least 2 arguments:
 *   rootDir: a directory for the secondary communication channel
 *   operation: the operation to be performed by the client
 *   params:  params for the client request (optional, depending on operation)
 */
public class SvalbardClientCli {
  public static final Charset UTF_8 = Charset.forName("UTF-8");

  public static void main(String[] args) throws Exception {
   if (args.length < 2) {
      System.out.println(
          "Usage: SvalbardClientCli root-dir operation ... ");
      System.exit(1);
    }
    String rootDir = args[0];
    String operation = args[1];
    ListeningExecutorService executorService =
        MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
    FileSecondaryChannel fileChannel = new FileSecondaryChannel(rootDir);
    SvalbardClient client = new SvalbardClient(
        new ServerShareManager(fileChannel, executorService), new FakePrintedShareManager(),
        executorService);
    switch (operation) {
      case "share_secret":
        {
          if (args.length < 7) {
            System.out.println(
                "Usage: SvalbardClientCli root-dir " + operation
                + " secret-name secret-value parameter-k metadata-filename share-location [...]");
            System.exit(1);
          }
          String secretName = args[2];
          String secretValue = args[3];
          int paramK = Integer.valueOf(args[4]);
          String metadataFilename = args[5];
          int locationsOffset = 6;
          int numOfLocations = args.length - locationsOffset;
          int paramN = numOfLocations;
          ShareLocation[] locations = new ShareLocation[numOfLocations];
          for (int i = 0; i < numOfLocations; i++) {
            ShareLocation.Builder locationBuilder = ShareLocation.newBuilder();
            TextFormat.merge(args[i + locationsOffset], locationBuilder);
            locations[i] = locationBuilder.build();
          }
          System.out.println("*** Performing a " + paramK + "-out-of-" + paramN + " sharing...");
          try {
            SvalbardClient.SharingResult result = client.share(
                secretName, secretValue.getBytes(UTF_8), paramK, paramN, locations).get();
            System.out.println(
                "*** Stored " + (paramN - result.getSharesToBeStored().size()) + " shares.");
            write(TextFormat.printToString(result.getSharedSecretMetadata()).getBytes(UTF_8),
                metadataFilename);
          } catch (GeneralSecurityException e) {
            System.out.println("Failure: " + e);
            System.exit(1);
          }
        }
        break;
      case "recover_secret":
        {
          if (args.length != 4) {
            System.out.println("Usage: SvalbardClientCli root-dir " + operation
                + " metadata-filename secret-filename");
            System.exit(1);
          }
          String metadataFilename = args[2];
          String secretFilename = args[3];
          SharedSecretMetadata.Builder metadataBuilder = SharedSecretMetadata.newBuilder();
          String metadataString = new String(read(metadataFilename), UTF_8);
          TextFormat.merge(metadataString, metadataBuilder);
          try {
            SvalbardClient.RecoveryResult result = client.recover(metadataBuilder.build()).get();
            write(result.getSecret(), secretFilename);
            int validSharesCount = 0;
            for (ShareData shareData : result.getShareDataList()) {
              if (shareData.getFailure() == null) {
                validSharesCount++;
              }
            }
            System.out.println(
                "*** Recovered using " + validSharesCount + " shares.");
          } catch (GeneralSecurityException | ExecutionException e) {
            System.out.println("Failure: " + e);
          }
        }
        break;
      default:
        executorService.shutdown();
        throw new IllegalArgumentException("Unsupported operation: " + operation);
    }
    executorService.shutdown();
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
