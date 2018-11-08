# Copyright 2018 The Svalbard Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
################################################################################

#!/bin/bash

#############################################################################
### Constants used throughout the tests.


TLS_KEY_FILENAME="$ROOT_DIR/server/testdata/test_server_key.pem"
TLS_CERT_FILENAME="$ROOT_DIR/server/testdata/test_server_certificate.crt"
TRUSTSTORE_FILE="$ROOT_DIR/client/testing/test_truststore.jks"

SERVER_BIN="$ROOT_DIR/server/go/linux_amd64_stripped/server"
CLIENT_BIN="$ROOT_DIR/client/java/svalbard_client_cli --jvm_flag=-Djavax.net.ssl.trustStore=$TRUSTSTORE_FILE"
MANAGER_BIN="$ROOT_DIR/client/java/server_share_manager_cli --jvm_flag=-Djavax.net.ssl.trustStore=$TRUSTSTORE_FILE"
PORTPICKER_CLI="$ROOT_DIR/external/org_python_pypi_portpicker/portpicker_cli"
HTTP_RESPONSE_BODY="$TEST_TMPDIR/http_response_body.txt"
FILECHANNEL_DIR="$TEST_TMPDIR/filechannel"

#############################################################################
### Helpers for communicating with Svalbard servers.

get_token() {
  local owner_id="$1"
  local request_id="$2"
  local channel_file="$FILECHANNEL_DIR/${owner_id}_secondary_channel.txt"
  local token=$(grep "SVBD:$request_id" $channel_file | cut -d: -f3)
  echo $token
}

send_request_to_server() {
  local operation="$1"
  local output_filename="$2"
  shift
  shift
  local response=$($MANAGER_BIN $FILECHANNEL_DIR $operation $output_filename $@) || die "operation $operation failed"
  echo "$response"
}

perform_client_operation() {
  local operation="$1"
  local output_filename="$2"
  shift
  shift
  local response=$($CLIENT_BIN $FILECHANNEL_DIR $operation $output_filename $@) || die "operation $operation failed"
  echo "$response"
}

get_share_location_proto() {
  local owner_id="$1"
  local server_port="$2"
  echo "owner_id_type:\"FILE\";owner_id:\"$owner_id\";location_type:SVALBARD_SERVER;location_name:\"https://localhost:$server_port/\""
}

#############################################################################
### Helpers for checking test results.

assert_equals() {
  local expected="$1"
  local actual="$2"
  if [ "$expected" != "$actual" ]; then
    echo "--- Failure: expected value: [$expected], actual value: [$actual]"
    exit 1
  fi
  echo "    Success: got [$actual], as expected."
}

assert_has_substring() {
  local string="$1"
  local substring="$2"
  if [[ $substring = "" ]]; then
    echo "--- Failure. expected substring cannot be empty"
    exit 1
  fi
  if [[ $string != *"$substring"* ]]; then
    echo "--- Failure: no substring [$substring] found in [$string]"
    exit 1
  fi
  echo "    Success: given string has substring [$substring]"
}
