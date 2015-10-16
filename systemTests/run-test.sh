#!/bin/bash
#
# Copyright (c) 2015 the authors of j2objc-gradle (see AUTHORS file)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


# Fail if anything fails.
set -euv

if [[ "$PWD" =~ systemTests ]]; then
   echo "Should be run from project root and not systemTests directory"
   exit 1
fi


TEST_DIR=$1
echo Running test $TEST_DIR

pushd $TEST_DIR
./gradlew wrapper
./gradlew clean
# If we fail, try again with lots of logging.
./gradlew build

# Dump out listings of the files generated for manual debugging/verification.
ls -R1c build/j2objcOutputs || echo No such outputs
ls -R1c */build/j2objcOutputs || echo No such outputs

# pop $TEST_DIR
popd
