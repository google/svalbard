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

ROOT_DIR="$TEST_SRCDIR/svalbard"
TEST_UTIL="$ROOT_DIR/client/testing/test_util.sh"

source $TEST_UTIL || exit 1

METADATA_FILENAME=$TEST_TMPDIR/metadata.txt
SECRET_FILENAME=$TEST_TMPDIR/recovered_secret.txt

BOLT_STORE_FILE_PREFIX=$TEST_TMPDIR/bolt_share_store

# Sharing parameters (K-out-of-N sharing).
N=5
K=3

# Constructing a space-separated series of ShareLocation-protos in text format.
get_share_locations() {
  local owner_id="$1"
  local count="$2"
  local locs="$(get_share_location_proto $owner_id ${SERVER_PORT[1]})"
  for i in $(seq 2 $count); do
    locs="${locs} $(get_share_location_proto $owner_id ${SERVER_PORT[$i]})"
  done
  echo $locs
}

# Start Svalbard servers.
mkdir -p $FILECHANNEL_DIR
for i in $(seq 1 $N); do
  SERVER_PORT[$i]=$($PORTPICKER_CLI $$)
  $SERVER_BIN -port=${SERVER_PORT[$i]} -filechannel_root_dir=$FILECHANNEL_DIR\
    -bolt_share_store_file="${BOLT_STORE_FILE_PREFIX}_${SERVER_PORT[$i]}.db"\
    -tls_key_file=$TLS_KEY_FILENAME -tls_cert_file=$TLS_CERT_FILENAME\
    -token_validity=2s &
  SERVER_PID[$i]=$!
  trap "kill ${SERVER_PID[$i]}" EXIT
  echo "... Starting server #$i at port ${SERVER_PORT[$i]} as process ${SERVER_PID[$i]}"
done
sleep 2

# Run actual tests.
echo "+++ Share some secret value ..."
secret_value="SomeSecretValue"
locations=$(get_share_locations "Alice" $N)
response=$(perform_client_operation "share_secret"\
    "GmailKey" $secret_value $K $METADATA_FILENAME $locations)
echo "Response: $response"
assert_has_substring "$response" "$K-out-of-$N"
assert_has_substring "$response" "Stored $N shares"

echo "+++ Recover the secret value ..."
response=$(perform_client_operation "recover_secret" $METADATA_FILENAME $SECRET_FILENAME)
echo "Response: $response"
assert_has_substring "$response" "Recovered using $N shares"
recovered_value=$(cat $SECRET_FILENAME)
assert_equals $secret_value $recovered_value

echo "+++ Recover the secret from minimal number of shares..."
for i in $(seq 1 $(($N-$K))); do
  kill ${SERVER_PID[$i]}
done
sleep 2
response=$(perform_client_operation "recover_secret" $METADATA_FILENAME $SECRET_FILENAME)
echo "Response: $response"
assert_has_substring "$response" "Recovered using $K shares"
recovered_value=$(cat $SECRET_FILENAME)
assert_equals $secret_value $recovered_value

echo "+++ Try recovery with an insufficient number of shares..."
kill ${SERVER_PID[$(($N-$K+1))]}
sleep 2
response=$(perform_client_operation "recover_secret" $METADATA_FILENAME $SECRET_FILENAME)
echo "Response: $response"
assert_has_substring "$response" "too few shares"

echo "+++ Restart the first server and re-try recovery..."
$SERVER_BIN -port=${SERVER_PORT[1]} -filechannel_root_dir=$FILECHANNEL_DIR\
    -bolt_share_store_file="${BOLT_STORE_FILE_PREFIX}_${SERVER_PORT[1]}.db"\
    -tls_key_file=$TLS_KEY_FILENAME -tls_cert_file=$TLS_CERT_FILENAME\
    -token_validity=2s &
SERVER_PID[1]=$!
trap "kill ${SERVER_PID[1]}" EXIT
sleep 2
response=$(perform_client_operation "recover_secret" $METADATA_FILENAME $SECRET_FILENAME)
echo "Response: $response"
assert_has_substring "$response" "Recovered using $K shares"
recovered_value=$(cat $SECRET_FILENAME)
assert_equals $secret_value $recovered_value

echo "PASS"
