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

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src) \
		src/com/droidlogic/SubTitleService/ISubTitleService.aidl

#LOCAL_SDK_VERSION := current
LOCAL_PROGUARD_ENABLED := disabled

LOCAL_PACKAGE_NAME := SubTitle
LOCAL_CERTIFICATE := platform
LOCAL_REQUIRED_MODULES := libsubjni

include $(BUILD_PACKAGE)
include $(call all-makefiles-under,$(LOCAL_PATH))
